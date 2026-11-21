package com.example.itcardbrawl.game;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import com.example.itcardbrawl.game.Match.Side;
import com.example.itcardbrawl.game.Match.Status;
import com.example.itcardbrawl.game.MatchEvent.AbilityTriggered;
import com.example.itcardbrawl.game.MatchEvent.Attacked;
import com.example.itcardbrawl.game.MatchEvent.CardBurned;
import com.example.itcardbrawl.game.MatchEvent.CardDrawn;
import com.example.itcardbrawl.game.MatchEvent.CardPlayed;
import com.example.itcardbrawl.game.MatchEvent.CharacterDefeated;
import com.example.itcardbrawl.game.MatchEvent.EventExpired;
import com.example.itcardbrawl.game.MatchEvent.EffectApplied;
import com.example.itcardbrawl.game.MatchEvent.HpChanged;
import com.example.itcardbrawl.game.MatchEvent.MatchEnded;
import com.example.itcardbrawl.game.MatchEvent.TurnEnded;
import com.example.itcardbrawl.game.MatchEvent.TurnStarted;
import com.example.itcardbrawl.game.MatchResult.EndReason;

import org.junit.jupiter.api.Test;

class MatchEngineTests {

	private static final MatchEngine ENGINE = new MatchEngine();

	private static final Faction FACTION = new Faction("test", text("Test"), text("Test"), true);

	// ---- Zagranie ----

	@Test
	void playingCharacterPaysCostAndEntersBoard() {
		CardInstance dev = character(1, 3, 2, 4, null);
		Match match = match(side().hand(dev).budget(5).deck(filler(100, 3)), side().deck(filler(200, 3)));

		Match after = ENGINE.apply(match, Side.PLAYER, new Move.PlayCard(1));

		assertThat(after.player().hand()).isEmpty();
		assertThat(after.player().budgetCurrent()).isEqualTo(2);
		assertThat(after.player().budgetMax()).isEqualTo(5);
		assertThat(after.player().board()).containsExactly(new BoardCharacter(dev, 0, true, false));
	}

	@Test
	void notEnoughBudgetIsRejected() {
		Match match = match(side().hand(character(1, 3, 2, 4, null)).budget(2), side());

		assertThatThrownBy(() -> ENGINE.apply(match, Side.PLAYER, new Move.PlayCard(1)))
			.isInstanceOf(IllegalMoveException.class)
			.hasMessage("Za mało budżetu na zagranie karty");
	}

	@Test
	void sixthCharacterIsRejected() {
		SideBuilder player = side().hand(character(1, 1, 1, 1, null)).budget(MatchRules.BUDGET_CAP);
		for (int i = 0; i < MatchRules.BOARD_LIMIT; i++) {
			player.board(onBoard(character(10 + i, 1, 1, 1, null), false));
		}

		assertIllegal(match(player, side()), Side.PLAYER, new Move.PlayCard(1));
	}

	@Test
	void cardNotInHandIsRejected() {
		Match match = match(side().hand(character(1, 1, 1, 1, null)).budget(5).deck(character(2, 1, 1, 1, null)),
				side().hand(character(3, 1, 1, 1, null)));

		assertIllegal(match, Side.PLAYER, new Move.PlayCard(2));
		assertIllegal(match, Side.PLAYER, new Move.PlayCard(3));
	}

	@Test
	void moveOutsideOwnTurnIsRejected() {
		Match match = match(side().budget(5), side().hand(character(1, 1, 1, 1, null)).budget(5));

		assertIllegal(match, Side.OPPONENT, new Move.PlayCard(1));
		assertIllegal(match, Side.OPPONENT, new Move.EndTurn());
	}

	@Test
	void instantEventGoesToGraveyardAndLastingEventBecomesActive() {
		CardInstance instant = event(1, 1, 0);
		CardInstance lasting = event(2, 1, 2);
		Match match = match(side().hand(instant, lasting).budget(5), side());

		Match after = ENGINE.apply(ENGINE.apply(match, Side.PLAYER, new Move.PlayCard(1)), Side.PLAYER,
				new Move.PlayCard(2));

		assertThat(after.player().graveyard()).containsExactly(instant);
		assertThat(after.player().activeEvents()).containsExactly(new ActiveEvent(lasting, 2));
		assertThat(after.player().budgetCurrent()).isEqualTo(3);
		assertThat(after.player().board()).isEmpty();
	}

	@Test
	void targetedDamageCanChooseAnyCharacterOrPlayerWithoutGuardRestrictions() {
		CardInstance spell = event(1, 1, 0, new Effect(EffectKind.DAMAGE, EffectScope.TARGET, 3));
		CardInstance guard = character(2, 1, 1, 5, ability(AbilityKeyword.GUARD));
		CardInstance own = character(3, 1, 1, 5, null);
		Match match = match(side().hand(spell).budget(2).board(onBoard(own, false)),
				side().board(onBoard(guard, false)).hp(26));

		assertThat(ENGINE.legalMoves(match, Side.PLAYER)).contains(new Move.PlayCard(1, new Target.Character(2)),
				new Move.PlayCard(1, new Target.Character(3)), new Move.PlayCard(1, new Target.Player(Side.PLAYER)),
				new Move.PlayCard(1, new Target.Player(Side.OPPONENT)));
		Match after = ENGINE.apply(match, Side.PLAYER, new Move.PlayCard(1, new Target.Player(Side.OPPONENT)));

		assertThat(after.opponent().hp()).isEqualTo(23);
		assertThat(after.log()).contains(new EffectApplied(Side.PLAYER, spell,
				new Effect(EffectKind.DAMAGE, EffectScope.TARGET, 3), null, Side.OPPONENT));
	}

	@Test
	void targetIntersectionRejectsPlayersWhenCardAlsoTargetsCharactersOnly() {
		CardInstance spell = event(1, 1, 0, new Effect(EffectKind.DAMAGE, EffectScope.TARGET, 2),
				new Effect(EffectKind.TRAP, EffectScope.TARGET, 0));
		Match match = match(side().hand(spell).budget(2), side().board(onBoard(character(2, 1, 1, 5, null), false)));

		assertThat(ENGINE.legalMoves(match, Side.PLAYER)).containsExactly(new Move.PlayCard(1, new Target.Character(2)));
		assertIllegal(match, Side.PLAYER, new Move.PlayCard(1, new Target.Player(Side.PLAYER)));
		assertIllegal(match, Side.PLAYER, new Move.PlayCard(1));
	}

	@Test
	void targetedEventRequiresTargetAndRejectsTargetWhenNoneIsNeeded() {
		CardInstance targeted = event(1, 1, 0, new Effect(EffectKind.HEAL, EffectScope.TARGET, 2));
		CardInstance untargeted = event(2, 1, 0, new Effect(EffectKind.PLAYER_DAMAGE, EffectScope.OPPONENT, 2));
		Match match = match(side().hand(targeted, untargeted).budget(3), side());

		assertIllegal(match, Side.PLAYER, new Move.PlayCard(1));
		assertIllegal(match, Side.PLAYER, new Move.PlayCard(2, new Target.Player(Side.OPPONENT)));
		assertThat(ENGINE.legalMoves(match, Side.PLAYER)).contains(new Move.PlayCard(2, null));
	}

	@Test
	void areaDamageAffectsTheCorrectSidesAndDefeatsCharactersInOpponentThenOwnerOrder() {
		CardInstance spell = event(1, 1, 0, new Effect(EffectKind.DAMAGE, EffectScope.ALL_BOARDS, 2));
		CardInstance own = character(2, 1, 1, 2, null);
		CardInstance enemy = character(3, 1, 1, 2, null);
		Match match = match(side().hand(spell).budget(2).board(onBoard(own, false)),
				side().board(onBoard(enemy, false)));

		Match after = ENGINE.apply(match, Side.PLAYER, new Move.PlayCard(1));

		assertThat(after.player().board()).isEmpty();
		assertThat(after.opponent().board()).isEmpty();
		assertThat(after.log()).containsSubsequence(new CharacterDefeated(Side.OPPONENT, enemy),
				new CharacterDefeated(Side.PLAYER, own));
	}

	@Test
	void healsHpAndCharacterDamageWithoutExceedingMaximum() {
		CardInstance hpHeal = event(1, 1, 0, new Effect(EffectKind.HEAL, EffectScope.TARGET, 5));
		CardInstance damaged = character(2, 1, 1, 5, null);
		Match match = match(side().hand(hpHeal).budget(2).hp(28)
				.board(new BoardCharacter(damaged, 3, true, false)), side());

		Match hpAfter = ENGINE.apply(match, Side.PLAYER, new Move.PlayCard(1, new Target.Player(Side.PLAYER)));
		assertThat(hpAfter.player().hp()).isEqualTo(MatchRules.MAX_HP);
		assertThat(hpAfter.player().board().getFirst().damage()).isEqualTo(3);

		CardInstance characterHeal = event(3, 1, 0, new Effect(EffectKind.HEAL, EffectScope.TARGET, 5));
		Match characterAfter = ENGINE.apply(match(side().hand(characterHeal).budget(2)
				.board(new BoardCharacter(damaged, 3, true, false)), side()), Side.PLAYER,
				new Move.PlayCard(3, new Target.Character(2)));
		assertThat(characterAfter.player().board().getFirst().damage()).isZero();
	}

	@Test
	void everyImmediateScopeAppliesToExpectedState() {
		CardInstance playerDamage = event(1, 1, 0, new Effect(EffectKind.PLAYER_DAMAGE, EffectScope.OPPONENT, 2));
		Match damaged = ENGINE.apply(match(side().hand(playerDamage).budget(2), side()), Side.PLAYER,
				new Move.PlayCard(1));
		assertThat(damaged.opponent().hp()).isEqualTo(28);

		CardInstance draw = event(2, 1, 0, new Effect(EffectKind.DRAW, EffectScope.SELF, 2));
		CardInstance first = character(10, 1, 1, 1, null);
		CardInstance second = character(11, 1, 1, 1, null);
		Match drawn = ENGINE.apply(match(side().hand(draw).budget(2).deck(first, second), side()), Side.PLAYER,
				new Move.PlayCard(2));
		assertThat(drawn.player().hand()).containsExactly(first, second);

		CardInstance budgetGain = event(3, 1, 0, new Effect(EffectKind.BUDGET_GAIN, EffectScope.SELF, 8));
		Match budgeted = ENGINE.apply(match(side().hand(budgetGain).budget(2), side()), Side.PLAYER,
				new Move.PlayCard(3));
		assertThat(budgeted.player().budgetCurrent()).isEqualTo(MatchRules.BUDGET_CAP - 1);

		CardInstance maxBudget = event(4, 1, 0,
				new Effect(EffectKind.BUDGET_MAX, EffectScope.SELF, 8));
		Match maxed = ENGINE.apply(match(side().hand(maxBudget).budget(2), side()), Side.PLAYER,
				new Move.PlayCard(4));
		assertThat(maxed.player().budgetMax()).isEqualTo(MatchRules.BUDGET_CAP);
		assertThat(maxed.player().budgetCurrent()).isEqualTo(1);

		CardInstance enemyBoard = event(5, 1, 0, new Effect(EffectKind.DAMAGE, EffectScope.ENEMY_BOARD, 1));
		CardInstance enemyUnit = character(20, 1, 1, 5, null);
		Match enemyHit = ENGINE.apply(match(side().hand(enemyBoard).budget(2),
				side().board(onBoard(enemyUnit, false))), Side.PLAYER, new Move.PlayCard(5));
		assertThat(enemyHit.opponent().board().getFirst().damage()).isEqualTo(1);

		CardInstance enemyAll = event(6, 1, 0, new Effect(EffectKind.DAMAGE, EffectScope.ENEMY_ALL, 2));
		Match enemyAllHit = ENGINE.apply(match(side().hand(enemyAll).budget(2),
				side().board(onBoard(enemyUnit, false))), Side.PLAYER, new Move.PlayCard(6));
		assertThat(enemyAllHit.opponent().board().getFirst().damage()).isEqualTo(2);
		assertThat(enemyAllHit.opponent().hp()).isEqualTo(28);

		CardInstance healOwn = event(7, 1, 0, new Effect(EffectKind.HEAL, EffectScope.OWN_BOARD, 2));
		CardInstance ownUnit = character(21, 1, 1, 5, null);
		Match ownHealed = ENGINE.apply(match(side().hand(healOwn).budget(2)
				.board(new BoardCharacter(ownUnit, 2, true, false)), side()), Side.PLAYER, new Move.PlayCard(7));
		assertThat(ownHealed.player().board().getFirst().damage()).isZero();

		CardInstance healOwnAll = event(8, 1, 0, new Effect(EffectKind.HEAL, EffectScope.OWN_ALL, 2));
		Match ownAllHealed = ENGINE.apply(match(side().hand(healOwnAll).budget(2).hp(28)
				.board(new BoardCharacter(ownUnit, 2, true, false)), side()), Side.PLAYER, new Move.PlayCard(8));
		assertThat(ownAllHealed.player().board().getFirst().damage()).isZero();
		assertThat(ownAllHealed.player().hp()).isEqualTo(30);

		CardInstance healAll = event(9, 1, 0, new Effect(EffectKind.HEAL, EffectScope.ALL_BOARDS, 1));
		Match allHealed = ENGINE.apply(match(side().hand(healAll).budget(2)
				.board(new BoardCharacter(ownUnit, 2, true, false)),
				side().board(new BoardCharacter(enemyUnit, 2, true, false))), Side.PLAYER, new Move.PlayCard(9));
		assertThat(allHealed.player().board().getFirst().damage()).isEqualTo(1);
		assertThat(allHealed.opponent().board().getFirst().damage()).isEqualTo(1);
	}

	@Test
	void drawEffectBurnsAtFullHandAndEmptyDeckEndsMatchAndStopsNextEffect() {
		CardInstance top = character(50, 1, 1, 1, null);
		CardInstance next = character(51, 1, 1, 1, null);
		CardInstance draw = event(1, 1, 0, new Effect(EffectKind.DRAW, EffectScope.SELF, 2));
		Match full = ENGINE.apply(match(side().hand(draw).budget(2)
				.hand(filler(100, MatchRules.HAND_LIMIT - 1)).deck(next, top), side()), Side.PLAYER,
				new Move.PlayCard(1));
		assertThat(full.player().graveyard()).contains(top);
		assertThat(full.log()).contains(new CardBurned(Side.PLAYER, top));

		CardInstance empty = event(2, 1, 0, new Effect(EffectKind.DRAW, EffectScope.SELF, 1),
				new Effect(EffectKind.PLAYER_DAMAGE, EffectScope.OPPONENT, 3));
		Match lost = ENGINE.apply(match(side().hand(empty).budget(2), side()), Side.PLAYER,
				new Move.PlayCard(2));
		assertThat(lost.status()).isEqualTo(Status.FINISHED);
		assertThat(lost.result()).isEqualTo(new MatchResult(Side.OPPONENT, EndReason.EMPTY_DECK));
		assertThat(lost.opponent().hp()).isEqualTo(MatchRules.MAX_HP);
		assertThat(lost.log()).noneMatch(e -> e instanceof EffectApplied applied
				&& applied.effect().kind() == EffectKind.PLAYER_DAMAGE);
	}

	@Test
	void ownPlayerDamageToZeroHpLosesAndStopsRemainingEffects() {
		CardInstance spell = event(1, 1, 0, new Effect(EffectKind.DAMAGE, EffectScope.TARGET, 2),
				new Effect(EffectKind.BUDGET_GAIN, EffectScope.SELF, 2));
		Match match = match(side().hand(spell).budget(2).hp(1), side());

		Match after = ENGINE.apply(match, Side.PLAYER, new Move.PlayCard(1, new Target.Player(Side.PLAYER)));

		assertThat(after.player().hp()).isEqualTo(-1);
		assertThat(after.result()).isEqualTo(new MatchResult(Side.OPPONENT, EndReason.HP_DEPLETED));
		assertThat(after.player().budgetCurrent()).isEqualTo(1);
		assertThat(after.log()).noneMatch(e -> e instanceof EffectApplied applied
				&& applied.effect().kind() == EffectKind.BUDGET_GAIN);
	}

	// Efekt, który kończy partię, nie dopisuje nic do logu po MatchEnded - ani obrażeń postaci, ani pokonań.
	@Test
	void matchEndingAreaDamageLogsNothingAfterMatchEnded() {
		CardInstance spell = event(1, 1, 2, new Effect(EffectKind.DAMAGE, EffectScope.ENEMY_ALL, 3),
				new Effect(EffectKind.ATTACK, EffectScope.ENEMY_BOARD, -1));
		CardInstance enemy = character(2, 1, 1, 2, null);
		Match match = match(side().hand(spell).budget(1),
				side().hp(2).board(onBoard(enemy, false)));

		Match after = ENGINE.apply(match, Side.PLAYER, new Move.PlayCard(1));

		assertThat(after.result()).isEqualTo(new MatchResult(Side.PLAYER, EndReason.HP_DEPLETED));
		assertThat(after.log().getLast()).isInstanceOf(MatchEnded.class);
		assertThat(after.log()).noneMatch(CharacterDefeated.class::isInstance);
	}

	// ---- Gotowość ----

	@Test
	void freshCharacterCannotAttackButRushCan() {
		CardInstance plain = character(1, 1, 2, 2, null);
		CardInstance rush = character(2, 1, 2, 2, ability(AbilityKeyword.RUSH));
		Match match = match(side().hand(plain, rush).budget(5), side());

		Match after = ENGINE.apply(ENGINE.apply(match, Side.PLAYER, new Move.PlayCard(1)), Side.PLAYER,
				new Move.PlayCard(2));

		assertIllegal(after, Side.PLAYER, new Move.Attack(1, null));
		Match attacked = ENGINE.apply(after, Side.PLAYER, new Move.Attack(2, null));
		assertThat(attacked.opponent().hp()).isEqualTo(28);
	}

	@Test
	void secondAttackInSameTurnIsRejected() {
		Match match = match(side().board(onBoard(character(1, 1, 2, 5, null), true)), side());

		Match after = ENGINE.apply(match, Side.PLAYER, new Move.Attack(1, null));

		assertThat(after.player().board().getFirst().attacked()).isTrue();
		assertIllegal(after, Side.PLAYER, new Move.Attack(1, null));
	}

	// ---- Walka ----

	@Test
	void combatDamageIsMutualAndBothCanDie() {
		CardInstance attacker = character(1, 1, 3, 3, null);
		CardInstance target = character(2, 1, 4, 3, null);
		Match match = match(side().board(onBoard(attacker, true)), side().board(onBoard(target, false)));

		Match after = ENGINE.apply(match, Side.PLAYER, new Move.Attack(1, 2));

		assertThat(after.player().board()).isEmpty();
		assertThat(after.opponent().board()).isEmpty();
		assertThat(after.player().graveyard()).containsExactly(attacker);
		assertThat(after.opponent().graveyard()).containsExactly(target);
	}

	@Test
	void survivingCharactersKeepDamage() {
		CardInstance attacker = character(1, 1, 2, 5, null);
		CardInstance target = character(2, 1, 1, 6, null);
		Match match = match(side().board(onBoard(attacker, true)), side().board(onBoard(target, false)));

		Match after = ENGINE.apply(match, Side.PLAYER, new Move.Attack(1, 2));

		assertThat(after.player().board()).containsExactly(new BoardCharacter(attacker, 1, false, true));
		assertThat(after.opponent().board()).containsExactly(new BoardCharacter(target, 2, true, false));
		assertThat(attacker.card().health() - after.player().board().getFirst().damage()).isEqualTo(4);
	}

	@Test
	void guardBlocksPlayerAttackAndNonGuardTargets() {
		CardInstance guard = character(2, 1, 1, 5, ability(AbilityKeyword.GUARD));
		CardInstance other = character(3, 1, 1, 1, null);
		Match match = match(side().board(onBoard(character(1, 1, 2, 5, null), true)),
				side().board(onBoard(guard, false), onBoard(other, false)));

		assertIllegal(match, Side.PLAYER, new Move.Attack(1, null));
		assertIllegal(match, Side.PLAYER, new Move.Attack(1, 3));
		Match after = ENGINE.apply(match, Side.PLAYER, new Move.Attack(1, 2));
		assertThat(after.opponent().board().getFirst().damage()).isEqualTo(2);
	}

	@Test
	void attackCanTakeHpBelowZeroAndEndsMatch() {
		Match match = match(side().board(onBoard(character(1, 1, 5, 5, null), true)), side().hp(2));

		Match after = ENGINE.apply(match, Side.PLAYER, new Move.Attack(1, null));

		assertThat(after.opponent().hp()).isEqualTo(-3);
		assertThat(after.log()).contains(new HpChanged(Side.PLAYER, Side.OPPONENT, 2, -3));
		assertThat(after.status()).isEqualTo(Status.FINISHED);
		assertThat(after.result()).isEqualTo(new MatchResult(Side.PLAYER, EndReason.HP_DEPLETED));
		assertThat(after.log().getLast()).isEqualTo(new MatchEnded(Side.PLAYER, after.result()));
	}

	// ---- Zdolności ----

	@Test
	void onPlayDamageEndsMatchWhenHpReachesZero() {
		Match match = match(side().hand(character(1, 2, 1, 1, ability(AbilityKeyword.ON_PLAY_DAMAGE, 3))).budget(2),
				side().hp(3));

		Match after = ENGINE.apply(match, Side.PLAYER, new Move.PlayCard(1));

		assertThat(after.opponent().hp()).isZero();
		assertThat(after.status()).isEqualTo(Status.FINISHED);
		assertThat(after.result()).isEqualTo(new MatchResult(Side.PLAYER, EndReason.HP_DEPLETED));
	}

	@Test
	void healDoesNotExceedMaxHp() {
		Match match = match(side().hand(character(1, 1, 1, 1, ability(AbilityKeyword.HEAL, 4))).budget(1).hp(27),
				side());

		Match after = ENGINE.apply(match, Side.PLAYER, new Move.PlayCard(1));

		assertThat(after.player().hp()).isEqualTo(MatchRules.MAX_HP);
		assertThat(after.status()).isEqualTo(Status.IN_PROGRESS);
	}

	@Test
	void healIsCappedAtMaxHp() {
		CardInstance heal = event(1, 1, 0, new Effect(EffectKind.HEAL, EffectScope.TARGET, 5));
		Match match = match(side().hand(heal).budget(1).hp(28), side());

		Match after = ENGINE.apply(match, Side.PLAYER, new Move.PlayCard(1, new Target.Player(Side.PLAYER)));

		assertThat(after.player().hp()).isEqualTo(30);
		assertThat(after.log()).contains(new HpChanged(Side.PLAYER, Side.PLAYER, 28, 30));
	}

	@Test
	void healAtMaxHpLogsNoHpChange() {
		CardInstance heal = event(1, 1, 0, new Effect(EffectKind.HEAL, EffectScope.TARGET, 5));
		CardInstance healer = character(2, 1, 1, 1, ability(AbilityKeyword.HEAL, 3));
		Match match = match(side().hand(heal, healer).budget(2).hp(MatchRules.MAX_HP), side());

		Match after = ENGINE.apply(ENGINE.apply(match, Side.PLAYER, new Move.PlayCard(1, new Target.Player(Side.PLAYER))),
				Side.PLAYER, new Move.PlayCard(2));

		assertThat(after.player().hp()).isEqualTo(MatchRules.MAX_HP);
		assertThat(after.log()).noneMatch(HpChanged.class::isInstance);
		assertThat(after.status()).isEqualTo(Status.IN_PROGRESS);
	}

	@Test
	void overtimeDealsDamageAtEndOfTurn() {
		CardInstance worker = character(1, 1, 1, 1, ability(AbilityKeyword.OVERTIME));
		Match match = match(side().board(onBoard(worker, false)), side().hp(26).deck(filler(200, 1)));

		Match after = ENGINE.apply(match, Side.PLAYER, new Move.EndTurn());

		assertThat(after.opponent().hp()).isEqualTo(25);
		assertThat(after.activeSide()).isEqualTo(Side.OPPONENT);
	}

	@Test
	void overtimeCanEndMatchAndStopsRemainingSteps() {
		CardInstance first = character(1, 1, 1, 1, ability(AbilityKeyword.OVERTIME));
		CardInstance second = character(2, 1, 1, 1, ability(AbilityKeyword.OVERTIME));
		Match match = match(side().board(onBoard(first, false), onBoard(second, false)),
				side().hp(1).deck(filler(200, 1)));

		Match after = ENGINE.apply(match, Side.PLAYER, new Move.EndTurn());

		assertThat(after.opponent().hp()).isZero();
		assertThat(after.status()).isEqualTo(Status.FINISHED);
		assertThat(after.result()).isEqualTo(new MatchResult(Side.PLAYER, EndReason.HP_DEPLETED));
		// Druga Nadgodzina, zmiana tury i dobranie przeciwnika już się nie wykonują.
		assertThat(after.activeSide()).isEqualTo(Side.PLAYER);
		assertThat(after.turnNumber()).isEqualTo(1);
		assertThat(after.opponent().hand()).isEmpty();
		assertThat(after.log()).filteredOn(AbilityTriggered.class::isInstance).hasSize(1);
		assertThat(after.log()).noneMatch(TurnEnded.class::isInstance);
	}

	// ---- Koniec tury ----

	@Test
	void damageIsClearedAtEndOfTurn() {
		CardInstance mine = character(1, 1, 1, 5, null);
		CardInstance theirs = character(2, 1, 1, 5, null);
		Match match = match(side().board(new BoardCharacter(mine, 3, true, false)),
				side().board(new BoardCharacter(theirs, 2, true, false)).deck(filler(200, 1)));

		Match after = ENGINE.apply(match, Side.PLAYER, new Move.EndTurn());

		assertThat(after.player().board()).containsExactly(new BoardCharacter(mine, 0, true, false));
		assertThat(after.opponent().board()).containsExactly(new BoardCharacter(theirs, 0, false, false));
	}

	@Test
	void endTurnSwitchesSideDrawsAndRaisesBudget() {
		CardInstance top = character(200, 1, 1, 1, null);
		Match match = match(side().budget(1), side().budget(0).deck(top, character(201, 1, 1, 1, null)));

		Match after = ENGINE.apply(match, Side.PLAYER, new Move.EndTurn());

		assertThat(after.activeSide()).isEqualTo(Side.OPPONENT);
		assertThat(after.turnNumber()).isEqualTo(2);
		assertThat(after.opponent().hand()).containsExactly(top);
		assertThat(after.opponent().deck()).hasSize(1);
		assertThat(after.opponent().budgetMax()).isEqualTo(1);
		assertThat(after.opponent().budgetCurrent()).isEqualTo(1);
	}

	@Test
	void budgetStopsAtCap() {
		Match match = match(side(), side().budget(MatchRules.BUDGET_CAP).spent().deck(filler(200, 1)));

		Match after = ENGINE.apply(match, Side.PLAYER, new Move.EndTurn());

		assertThat(after.opponent().budgetMax()).isEqualTo(MatchRules.BUDGET_CAP);
		assertThat(after.opponent().budgetCurrent()).isEqualTo(MatchRules.BUDGET_CAP);
	}

	@Test
	void eventExpiresAfterDurationOfOwnersTurns() {
		CardInstance lasting = event(1, 1, 2);
		Match match = match(side().hand(lasting).budget(1).deck(filler(100, 5)), side().deck(filler(200, 5)));

		Match played = ENGINE.apply(match, Side.PLAYER, new Move.PlayCard(1));
		Match ownSecondTurn = endTurns(played, 2);
		assertThat(ownSecondTurn.activeSide()).isEqualTo(Side.PLAYER);
		assertThat(ownSecondTurn.player().activeEvents()).containsExactly(new ActiveEvent(lasting, 1));

		Match ownThirdTurn = endTurns(ownSecondTurn, 2);
		assertThat(ownThirdTurn.player().activeEvents()).isEmpty();
		assertThat(ownThirdTurn.player().graveyard()).containsExactly(lasting);
		assertThat(ownThirdTurn.log()).contains(new EventExpired(Side.PLAYER, lasting));
	}

	@Test
	void lastingAurasAffectLaterCharactersAndStackWithAttackFloor() {
		CardInstance boost = event(1, 1, 4, new Effect(EffectKind.ATTACK, EffectScope.OWN_BOARD, 2));
		CardInstance boostAgain = event(2, 1, 4, new Effect(EffectKind.ATTACK, EffectScope.OWN_BOARD, 3));
		CardInstance reduce = event(3, 1, 4, new Effect(EffectKind.ATTACK, EffectScope.OWN_BOARD, -10));
		CardInstance laterUnit = character(4, 1, 1, 5, null);
		CardInstance attacker = character(5, 1, 4, 5, null);
		Match match = match(side().hand(boost, boostAgain, reduce, laterUnit).budget(8)
				.board(onBoard(attacker, true)), side());

		Match after = ENGINE.apply(match, Side.PLAYER, new Move.PlayCard(1));
		after = ENGINE.apply(after, Side.PLAYER, new Move.PlayCard(2));
		after = ENGINE.apply(after, Side.PLAYER, new Move.PlayCard(3));
		after = ENGINE.apply(after, Side.PLAYER, new Move.PlayCard(4));
		BoardCharacter playedUnderAura = after.player().board().stream()
				.filter(character -> character.instance().instanceId() == 4).findFirst().orElseThrow();
		assertThat(CharacterStats.of(playedUnderAura, Side.PLAYER, after.player().activeEvents(),
				after.opponent().activeEvents()).attack()).isZero();
		after = ENGINE.apply(after, Side.PLAYER, new Move.Attack(5, null));

		assertThat(after.log()).filteredOn(MatchEvent.Attacked.class::isInstance)
				.contains(new MatchEvent.Attacked(Side.PLAYER, attacker, null, 0, 0));
	}

	@Test
	void negativeHealthModifierDefeatsDamagedAndNewCharacters() {
		CardInstance weakened = event(1, 1, 3,
				new Effect(EffectKind.HEALTH, EffectScope.ENEMY_BOARD, -2));
		CardInstance damaged = character(2, 1, 1, 3, null);
		BoardCharacter alreadyDamaged = new BoardCharacter(damaged, 2, true, false);
		Match first = match(side().hand(weakened).budget(2), side().board(alreadyDamaged));

		Match afterFirst = ENGINE.apply(first, Side.PLAYER, new Move.PlayCard(1));

		assertThat(afterFirst.opponent().board()).isEmpty();
		assertThat(afterFirst.opponent().graveyard()).containsExactly(damaged);

		CardInstance ownWeak = event(3, 1, 3, new Effect(EffectKind.HEALTH, EffectScope.OWN_BOARD, -1));
		CardInstance fragile = character(4, 1, 1, 1, null);
		Match second = match(side().hand(ownWeak, fragile).budget(3), side());
		Match afterSecond = ENGINE.apply(ENGINE.apply(second, Side.PLAYER, new Move.PlayCard(3)), Side.PLAYER,
				new Move.PlayCard(4));

		assertThat(afterSecond.player().board()).isEmpty();
		assertThat(afterSecond.player().graveyard()).containsExactly(fragile);
	}

	@Test
	void trapDisablesGuardAndRushAndCannotBeTargetedAgain() {
		CardInstance attacker = character(1, 1, 2, 5, ability(AbilityKeyword.RUSH));
		CardInstance trappedGuard = character(2, 1, 1, 5, ability(AbilityKeyword.GUARD));
		CardInstance other = character(3, 1, 1, 5, null);
		CardInstance trap = event(4, 1, 3, new Effect(EffectKind.TRAP, EffectScope.TARGET, 0));
		CardInstance trapOwnAttacker = event(5, 1, 3, new Effect(EffectKind.TRAP, EffectScope.TARGET, 0));
		CardInstance area = event(6, 1, 0, new Effect(EffectKind.DAMAGE, EffectScope.ALL_BOARDS, 1));
		Match match = match(side().hand(trap, trapOwnAttacker, area).budget(5).board(onBoard(attacker, true)),
				side().board(onBoard(trappedGuard, false), onBoard(other, false)));

		Match afterTrap = ENGINE.apply(match, Side.PLAYER, new Move.PlayCard(4, new Target.Character(2)));
		assertThat(ENGINE.legalMoves(afterTrap, Side.PLAYER)).contains(new Move.Attack(1, 3),
				new Move.Attack(1, null));
		assertThat(ENGINE.legalMoves(afterTrap, Side.PLAYER)).noneMatch(move -> move instanceof Move.Attack attack
				&& Integer.valueOf(2).equals(attack.targetId()));
		assertThat(ENGINE.legalMoves(afterTrap, Side.PLAYER)).noneMatch(move -> move instanceof Move.PlayCard play
				&& play.target() instanceof Target.Character target && target.instanceId() == 2);
		assertIllegal(afterTrap, Side.PLAYER, new Move.Attack(1, 2));
		Match withSecondTrap = new Match(afterTrap.id(), afterTrap.turnNumber(), afterTrap.activeSide(),
				new MatchSide(FACTION, afterTrap.player().deck(), List.of(trapOwnAttacker, area), afterTrap.player().hp(),
						afterTrap.player().budgetCurrent(), afterTrap.player().budgetMax(),
						afterTrap.player().board(), afterTrap.player().graveyard(), afterTrap.player().activeEvents()),
				afterTrap.opponent(), afterTrap.status(), afterTrap.result(), afterTrap.log());
		Match afterOwnTrap = ENGINE.apply(withSecondTrap, Side.PLAYER, new Move.PlayCard(5, new Target.Character(1)));
		assertThat(ENGINE.legalMoves(afterOwnTrap, Side.PLAYER)).noneMatch(move -> move instanceof Move.Attack attack
				&& attack.attackerId() == 1);
		Match afterArea = ENGINE.apply(afterOwnTrap, Side.PLAYER, new Move.PlayCard(6));
		assertThat(afterArea.opponent().board()).filteredOn(c -> c.instance().instanceId() == 2)
				.singleElement().extracting(BoardCharacter::damage).isEqualTo(1);
	}

	@Test
	void grantedGuardRestrictsAttackTargets() {
		CardInstance guardAura = event(1, 1, 3, new Effect(EffectKind.GUARD, EffectScope.ENEMY_BOARD, 0));
		CardInstance attacker = character(2, 1, 1, 5, null);
		CardInstance firstEnemy = character(3, 1, 1, 5, null);
		CardInstance secondEnemy = character(4, 1, 1, 5, null);
		Match match = match(side().hand(guardAura).budget(2).board(onBoard(attacker, true)),
				side().board(onBoard(firstEnemy, false), onBoard(secondEnemy, false)));

		Match after = ENGINE.apply(match, Side.PLAYER, new Move.PlayCard(1));

		assertThat(ENGINE.legalMoves(after, Side.PLAYER)).containsExactly(new Move.Attack(2, 3),
				new Move.Attack(2, 4));
	}

	@Test
	void grantedRushWorksForFreshCharacterButNeverAfterItHasAttacked() {
		CardInstance rush = event(1, 1, 3, new Effect(EffectKind.RUSH, EffectScope.OWN_BOARD, 0));
		CardInstance alreadyAttacked = character(2, 1, 2, 5, null);
		CardInstance fresh = character(3, 1, 2, 5, null);
		Match match = match(side().hand(rush, fresh).budget(4)
				.board(new BoardCharacter(alreadyAttacked, 0, false, true)), side());

		Match afterRush = ENGINE.apply(match, Side.PLAYER, new Move.PlayCard(1));
		assertThat(ENGINE.legalMoves(afterRush, Side.PLAYER)).noneMatch(move -> move instanceof Move.Attack attack
				&& attack.attackerId() == 2);
		afterRush = ENGINE.apply(afterRush, Side.PLAYER, new Move.PlayCard(3));
		assertThat(ENGINE.legalMoves(afterRush, Side.PLAYER)).contains(new Move.Attack(3, null));
		afterRush = ENGINE.apply(afterRush, Side.PLAYER, new Move.Attack(3, null));
		assertThat(ENGINE.legalMoves(afterRush, Side.PLAYER)).noneMatch(move -> move instanceof Move.Attack attack
				&& attack.attackerId() == 3);
	}

	@Test
	void expiringHealthBuffRestoresEffectiveHealth() {
		CardInstance buff = event(1, 1, 1, new Effect(EffectKind.HEALTH, EffectScope.OWN_BOARD, 2));
		CardInstance unit = character(2, 1, 1, 2, null);
		Match match = match(side().hand(buff).budget(2).board(new BoardCharacter(unit, 1, true, false))
				.deck(filler(90, 2)), side().deck(filler(100, 2)));

		Match played = ENGINE.apply(match, Side.PLAYER, new Move.PlayCard(1));
		assertThat(played.player().board()).hasSize(1);
		Match expired = endTurns(played, 2);

		assertThat(expired.player().activeEvents()).isEmpty();
		assertThat(expired.player().board()).hasSize(1);
		assertThat(CharacterStats.of(expired.player().board().getFirst(), Side.PLAYER,
				expired.player().activeEvents(), expired.opponent().activeEvents()).health()).isEqualTo(2);
	}

	@Test
	void drawWithFullHandBurnsCard() {
		CardInstance top = character(300, 1, 1, 1, null);
		Match match = match(side(), side().hand(filler(200, MatchRules.HAND_LIMIT)).deck(top));

		Match after = ENGINE.apply(match, Side.PLAYER, new Move.EndTurn());

		assertThat(after.opponent().hand()).hasSize(MatchRules.HAND_LIMIT).doesNotContain(top);
		assertThat(after.opponent().graveyard()).containsExactly(top);
		assertThat(after.opponent().deck()).isEmpty();
		assertThat(after.log()).contains(new CardBurned(Side.OPPONENT, top));
		assertThat(after.status()).isEqualTo(Status.IN_PROGRESS);
	}

	@Test
	void emptyDeckAtStartOfTurnLosesMatch() {
		Match match = match(side(), side());

		Match after = ENGINE.apply(match, Side.PLAYER, new Move.EndTurn());

		assertThat(after.status()).isEqualTo(Status.FINISHED);
		assertThat(after.result()).isEqualTo(new MatchResult(Side.PLAYER, EndReason.EMPTY_DECK));
		assertThat(after.log().getLast()).isEqualTo(new MatchEnded(Side.OPPONENT, after.result()));
	}

	// ---- Po końcu ----

	@Test
	void everyMoveIsRejectedAfterMatchFinished() {
		Match match = match(side().board(onBoard(character(1, 1, 5, 5, null), true)).hand(event(2, 1, 0)).budget(5),
				side().hp(2));
		Match finished = ENGINE.apply(match, Side.PLAYER, new Move.Attack(1, null));

		assertIllegal(finished, Side.PLAYER, new Move.PlayCard(2));
		assertIllegal(finished, Side.PLAYER, new Move.EndTurn());
		assertIllegal(finished, Side.OPPONENT, new Move.EndTurn());
		assertThat(ENGINE.legalMoves(finished, Side.PLAYER)).isEmpty();
	}

	// ---- legalMoves ----

	@Test
	void legalMovesSkipUnaffordableCardsAndUnreadyAttackers() {
		Match match = match(
				side().hand(character(1, 2, 1, 1, null), character(2, 3, 1, 1, null))
					.budget(2)
					.board(onBoard(character(3, 1, 1, 1, null), true), onBoard(character(4, 1, 1, 1, null), false)),
				side().board(onBoard(character(5, 1, 1, 1, null), false)));

		assertThat(ENGINE.legalMoves(match, Side.PLAYER)).containsExactly(new Move.PlayCard(1), new Move.Attack(3, 5),
				new Move.Attack(3, null));
		assertThat(ENGINE.legalMoves(match, Side.OPPONENT)).isEmpty();
	}

	@Test
	void legalMovesRespectGuard() {
		Match match = match(side().board(onBoard(character(1, 1, 1, 1, null), true)),
				side().board(onBoard(character(2, 1, 1, 1, null), false),
						onBoard(character(3, 1, 1, 1, ability(AbilityKeyword.GUARD)), false)));

		assertThat(ENGINE.legalMoves(match, Side.PLAYER)).containsExactly(new Move.Attack(1, 3));
	}

	@Test
	void legalMovesSkipCharactersWhenBoardIsFull() {
		SideBuilder player = side().hand(character(1, 1, 1, 1, null), event(2, 1, 0)).budget(5);
		for (int i = 0; i < MatchRules.BOARD_LIMIT; i++) {
			player.board(onBoard(character(10 + i, 1, 1, 1, null), false));
		}

		assertThat(ENGINE.legalMoves(match(player, side()), Side.PLAYER)).containsExactly(new Move.PlayCard(2));
	}

	// ---- Log ----

	@Test
	void logRecordsPlayAttackAndEndTurnInOrder() {
		CardInstance rusher = character(1, 2, 3, 3, ability(AbilityKeyword.RUSH));
		CardInstance top = character(200, 1, 1, 1, null);
		Match match = match(side().hand(rusher).budget(2), side().deck(top));

		Match after = ENGINE.apply(match, Side.PLAYER, new Move.PlayCard(1));
		after = ENGINE.apply(after, Side.PLAYER, new Move.Attack(1, null));
		after = ENGINE.apply(after, Side.PLAYER, new Move.EndTurn());

		assertThat(after.log()).containsExactly(new TurnStarted(Side.PLAYER, 1), new CardPlayed(Side.PLAYER, rusher),
				new Attacked(Side.PLAYER, rusher, null, 3, 0), new HpChanged(Side.PLAYER, Side.OPPONENT, 30, 27),
				new TurnEnded(Side.PLAYER), new TurnStarted(Side.OPPONENT, 2), new CardDrawn(Side.OPPONENT, top));
	}

	@Test
	void logRecordsDefeatedCharacters() {
		CardInstance attacker = character(1, 1, 3, 3, null);
		CardInstance target = character(2, 1, 1, 2, null);
		Match match = match(side().board(onBoard(attacker, true)), side().board(onBoard(target, false)));

		Match after = ENGINE.apply(match, Side.PLAYER, new Move.Attack(1, 2));

		assertThat(after.log()).endsWith(new Attacked(Side.PLAYER, attacker, target, 3, 1),
				new CharacterDefeated(Side.OPPONENT, target));
	}

	// ---- Wydarzenia: dodatkowe przypadki ----

	@Test
	void onPlayAbilityResolvesBeforeNegativeHealthAuraDefeatsNewCharacter() {
		CardInstance aura = event(1, 1, 3, new Effect(EffectKind.HEALTH, EffectScope.OWN_BOARD, -1));
		CardInstance fragile = character(2, 1, 1, 1, ability(AbilityKeyword.ON_PLAY_DAMAGE, 2));
		Match match = match(side().hand(aura, fragile).budget(2), side());

		Match after = ENGINE.apply(ENGINE.apply(match, Side.PLAYER, new Move.PlayCard(1)), Side.PLAYER,
				new Move.PlayCard(2));

		// Zdolność ON_PLAY działa, zanim aura −HP pokona postać.
		assertThat(after.opponent().hp()).isEqualTo(28);
		assertThat(after.log()).containsSubsequence(new CardPlayed(Side.PLAYER, fragile),
				new AbilityTriggered(Side.PLAYER, fragile, AbilityKeyword.ON_PLAY_DAMAGE, 2),
				new HpChanged(Side.PLAYER, Side.OPPONENT, 30, 28), new CharacterDefeated(Side.PLAYER, fragile));
		assertThat(after.player().board()).isEmpty();
		assertThat(after.player().graveyard()).containsExactly(fragile);
	}

	@Test
	void legalMovesSkipTargetedEventWithoutLegalTargetButOfferUntargetedOne() {
		CardInstance trap = event(1, 1, 3, new Effect(EffectKind.TRAP, EffectScope.TARGET, 0));
		CardInstance buff = event(2, 1, 2, new Effect(EffectKind.ATTACK, EffectScope.TARGET, 2));
		CardInstance playerDamage = event(3, 1, 0, new Effect(EffectKind.PLAYER_DAMAGE, EffectScope.OPPONENT, 1));
		Match match = match(side().hand(trap, buff, playerDamage).budget(3), side());

		// Puste stoły: pułapka i wzmocnienie celowane nie mają dozwolonego celu.
		assertThat(ENGINE.legalMoves(match, Side.PLAYER)).containsExactly(new Move.PlayCard(3, null));
		assertIllegal(match, Side.PLAYER, new Move.PlayCard(1));
		assertIllegal(match, Side.PLAYER, new Move.PlayCard(2));
	}

	@Test
	void budgetEffectsStopAtCap() {
		CardInstance budgetGain = event(1, 1, 0, new Effect(EffectKind.BUDGET_GAIN, EffectScope.SELF, 2));
		// Po opłacie bieżący budżet to 9; +2 zatrzymuje się na limicie.
		Match budgeted = ENGINE.apply(match(side().hand(budgetGain).budget(MatchRules.BUDGET_CAP), side()),
				Side.PLAYER, new Move.PlayCard(1));
		assertThat(budgeted.player().budgetCurrent()).isEqualTo(MatchRules.BUDGET_CAP);

		CardInstance maxBudget = event(2, 1, 0, new Effect(EffectKind.BUDGET_MAX, EffectScope.SELF, 1));
		Match maxed = ENGINE.apply(match(side().hand(maxBudget).budget(MatchRules.BUDGET_CAP), side()),
				Side.PLAYER, new Move.PlayCard(2));
		assertThat(maxed.player().budgetMax()).isEqualTo(MatchRules.BUDGET_CAP);
		assertThat(maxed.player().budgetCurrent()).isEqualTo(MatchRules.BUDGET_CAP - 1);
	}

	@Test
	void targetedDamageHitsCharacterAndMixedCardStoresEventOnSameTarget() {
		CardInstance hit = event(1, 1, 0, new Effect(EffectKind.DAMAGE, EffectScope.TARGET, 3));
		CardInstance tough = character(2, 1, 1, 5, null);
		Match damaged = ENGINE.apply(match(side().hand(hit).budget(1), side().board(onBoard(tough, false))),
				Side.PLAYER, new Move.PlayCard(1, new Target.Character(2)));
		assertThat(damaged.opponent().board()).containsExactly(new BoardCharacter(tough, 3, true, false));

		CardInstance kill = event(3, 1, 0, new Effect(EffectKind.DAMAGE, EffectScope.TARGET, 3));
		CardInstance weak = character(4, 1, 1, 3, null);
		Match defeated = ENGINE.apply(match(side().hand(kill).budget(1), side().board(onBoard(weak, false))),
				Side.PLAYER, new Move.PlayCard(3, new Target.Character(4)));
		assertThat(defeated.opponent().board()).isEmpty();
		assertThat(defeated.opponent().graveyard()).containsExactly(weak);
		assertThat(defeated.log()).contains(new CharacterDefeated(Side.OPPONENT, weak));

		// Karta mieszana: obrażenia od razu, modyfikator zapisany z tym samym celem.
		CardInstance mixed = event(5, 1, 2, new Effect(EffectKind.DAMAGE, EffectScope.TARGET, 2),
				new Effect(EffectKind.ATTACK, EffectScope.TARGET, -1));
		CardInstance enemy = character(6, 1, 3, 5, null);
		CardInstance neighbour = character(7, 1, 3, 5, null);
		Match mixedAfter = ENGINE.apply(match(side().hand(mixed).budget(1),
				side().board(onBoard(enemy, false), onBoard(neighbour, false))), Side.PLAYER,
				new Move.PlayCard(5, new Target.Character(6)));
		assertThat(mixedAfter.opponent().board()).containsExactly(new BoardCharacter(enemy, 2, true, false),
				new BoardCharacter(neighbour, 0, true, false));
		assertThat(mixedAfter.player().activeEvents()).containsExactly(new ActiveEvent(mixed, 2, 6));
	}

	@Test
	void targetBoundModifiersAffectOnlyTargetAndStopWhenEventExpires() {
		CardInstance buff = event(1, 1, 1, new Effect(EffectKind.ATTACK, EffectScope.TARGET, 2),
				new Effect(EffectKind.HEALTH, EffectScope.TARGET, 3));
		CardInstance target = character(2, 1, 2, 4, null);
		CardInstance neighbour = character(3, 1, 2, 4, null);
		Match match = match(side().hand(buff).budget(1).board(onBoard(target, false), onBoard(neighbour, false))
				.deck(filler(90, 2)), side().deck(filler(100, 2)));

		Match played = ENGINE.apply(match, Side.PLAYER, new Move.PlayCard(1, new Target.Character(2)));
		CharacterStats targetStats = CharacterStats.of(played.player().board().get(0), Side.PLAYER,
				played.player().activeEvents(), played.opponent().activeEvents());
		CharacterStats neighbourStats = CharacterStats.of(played.player().board().get(1), Side.PLAYER,
				played.player().activeEvents(), played.opponent().activeEvents());
		assertThat(targetStats.attack()).isEqualTo(4);
		assertThat(targetStats.health()).isEqualTo(7);
		assertThat(neighbourStats.attack()).isEqualTo(2);
		assertThat(neighbourStats.health()).isEqualTo(4);

		Match expired = endTurns(played, 2);
		assertThat(expired.player().activeEvents()).isEmpty();
		CharacterStats afterExpiry = CharacterStats.of(expired.player().board().get(0), Side.PLAYER,
				expired.player().activeEvents(), expired.opponent().activeEvents());
		assertThat(afterExpiry.attack()).isEqualTo(2);
		assertThat(afterExpiry.health()).isEqualTo(4);
	}

	// ---- Pomocnicze budowniczki ----

	private static void assertIllegal(Match match, Side side, Move move) {
		assertThatThrownBy(() -> ENGINE.apply(match, side, move)).isInstanceOf(IllegalMoveException.class);
	}

	private static Match endTurns(Match match, int count) {
		Match current = match;
		for (int i = 0; i < count; i++) {
			current = ENGINE.apply(current, current.activeSide(), new Move.EndTurn());
		}
		return current;
	}

	// Partia w turze 1 gracza, z logiem jak po fabryce.
	private static Match match(SideBuilder player, SideBuilder opponent) {
		return new Match(UUID.randomUUID(), 1, Side.PLAYER, player.build(), opponent.build(), Status.IN_PROGRESS, null,
				List.of(new TurnStarted(Side.PLAYER, 1)));
	}

	private static SideBuilder side() {
		return new SideBuilder();
	}

	private static CardInstance character(int instanceId, int cost, int attack, int health, Ability ability) {
		return new CardInstance(instanceId, new Card("char-" + instanceId, FACTION.id(), text("Postać"),
				CardType.CHARACTER, cost, text("Opis"), attack, health, ability, null, null));
	}

	private static CardInstance event(int instanceId, int cost, int duration) {
		Effect effect = duration == 0 ? new Effect(EffectKind.HEAL, EffectScope.OWN_BOARD, 1)
				: new Effect(EffectKind.ATTACK, EffectScope.OWN_BOARD, 1);
		return event(instanceId, cost, duration, effect);
	}

	private static CardInstance event(int instanceId, int cost, int duration, Effect... effects) {
		return new CardInstance(instanceId, new Card("event-" + instanceId, FACTION.id(), text("Wydarzenie"),
				CardType.EVENT, cost, text("Opis"), null, null, null, duration, List.of(effects)));
	}

	private static CardInstance[] filler(int firstId, int count) {
		return IntStream.range(0, count).mapToObj(i -> character(firstId + i, 1, 1, 1, null)).toArray(CardInstance[]::new);
	}

	private static BoardCharacter onBoard(CardInstance instance, boolean canAttack) {
		return new BoardCharacter(instance, 0, !canAttack, false);
	}

	private static Ability ability(AbilityKeyword keyword) {
		return new Ability(keyword, 0);
	}

	private static Ability ability(AbilityKeyword keyword, int amount) {
		return new Ability(keyword, amount);
	}

	private static LocalizedText text(String value) {
		return new LocalizedText(value, value);
	}

	private static final class SideBuilder {

		private final List<CardInstance> deck = new ArrayList<>();

		private final List<CardInstance> hand = new ArrayList<>();

		private final List<BoardCharacter> board = new ArrayList<>();

		private int hp = MatchRules.MAX_HP;

		private int budgetCurrent;

		private int budgetMax;

		SideBuilder deck(CardInstance... cards) {
			deck.addAll(List.of(cards));
			return this;
		}

		SideBuilder hand(CardInstance... cards) {
			hand.addAll(List.of(cards));
			return this;
		}

		SideBuilder board(BoardCharacter... characters) {
			board.addAll(List.of(characters));
			return this;
		}

		SideBuilder hp(int hp) {
			this.hp = hp;
			return this;
		}

		SideBuilder budget(int budget) {
			this.budgetCurrent = budget;
			this.budgetMax = budget;
			return this;
		}

		SideBuilder spent() {
			this.budgetCurrent = 0;
			return this;
		}

		MatchSide build() {
			return new MatchSide(FACTION, deck, hand, hp, budgetCurrent, budgetMax, board, List.of(), List.of());
		}

	}

}
