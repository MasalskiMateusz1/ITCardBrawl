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
import com.example.itcardbrawl.game.MatchEvent.MatchEnded;
import com.example.itcardbrawl.game.MatchEvent.StressChanged;
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
		Match match = match(side().hand(dev).resource(5).deck(filler(100, 3)), side().deck(filler(200, 3)));

		Match after = ENGINE.apply(match, Side.PLAYER, new Move.PlayCard(1));

		assertThat(after.player().hand()).isEmpty();
		assertThat(after.player().resourceCurrent()).isEqualTo(2);
		assertThat(after.player().resourceMax()).isEqualTo(5);
		assertThat(after.player().board()).containsExactly(new BoardCharacter(dev, 0, false));
	}

	@Test
	void notEnoughResourceIsRejected() {
		Match match = match(side().hand(character(1, 3, 2, 4, null)).resource(2), side());

		assertIllegal(match, Side.PLAYER, new Move.PlayCard(1));
	}

	@Test
	void sixthCharacterIsRejected() {
		SideBuilder player = side().hand(character(1, 1, 1, 1, null)).resource(10);
		for (int i = 0; i < MatchRules.BOARD_LIMIT; i++) {
			player.board(onBoard(character(10 + i, 1, 1, 1, null), false));
		}

		assertIllegal(match(player, side()), Side.PLAYER, new Move.PlayCard(1));
	}

	@Test
	void cardNotInHandIsRejected() {
		Match match = match(side().hand(character(1, 1, 1, 1, null)).resource(5).deck(character(2, 1, 1, 1, null)),
				side().hand(character(3, 1, 1, 1, null)));

		assertIllegal(match, Side.PLAYER, new Move.PlayCard(2));
		assertIllegal(match, Side.PLAYER, new Move.PlayCard(3));
	}

	@Test
	void moveOutsideOwnTurnIsRejected() {
		Match match = match(side().resource(5), side().hand(character(1, 1, 1, 1, null)).resource(5));

		assertIllegal(match, Side.OPPONENT, new Move.PlayCard(1));
		assertIllegal(match, Side.OPPONENT, new Move.EndTurn());
	}

	@Test
	void instantEventGoesToGraveyardAndLastingEventBecomesActive() {
		CardInstance instant = event(1, 1, 0);
		CardInstance lasting = event(2, 1, 2);
		Match match = match(side().hand(instant, lasting).resource(5), side());

		Match after = ENGINE.apply(ENGINE.apply(match, Side.PLAYER, new Move.PlayCard(1)), Side.PLAYER,
				new Move.PlayCard(2));

		assertThat(after.player().graveyard()).containsExactly(instant);
		assertThat(after.player().activeEvents()).containsExactly(new ActiveEvent(lasting, 2));
		assertThat(after.player().resourceCurrent()).isEqualTo(3);
		assertThat(after.player().board()).isEmpty();
	}

	@Test
	void targetedDamageCanChooseAnyCharacterOrPlayerWithoutGuardRestrictions() {
		CardInstance spell = event(1, 1, 0, new Effect(EffectKind.DAMAGE, EffectScope.TARGET, 3));
		CardInstance guard = character(2, 1, 1, 5, ability(AbilityKeyword.GUARD));
		CardInstance own = character(3, 1, 1, 5, null);
		Match match = match(side().hand(spell).resource(2).board(onBoard(own, false)),
				side().board(onBoard(guard, false)).stress(4));

		assertThat(ENGINE.legalMoves(match, Side.PLAYER)).contains(new Move.PlayCard(1, new Target.Character(2)),
				new Move.PlayCard(1, new Target.Character(3)), new Move.PlayCard(1, new Target.Player(Side.PLAYER)),
				new Move.PlayCard(1, new Target.Player(Side.OPPONENT)));
		Match after = ENGINE.apply(match, Side.PLAYER, new Move.PlayCard(1, new Target.Player(Side.OPPONENT)));

		assertThat(after.opponent().stress()).isEqualTo(7);
		assertThat(after.log()).contains(new EffectApplied(Side.PLAYER, spell,
				new Effect(EffectKind.DAMAGE, EffectScope.TARGET, 3), null, Side.OPPONENT));
	}

	@Test
	void targetIntersectionRejectsPlayersWhenCardAlsoTargetsCharactersOnly() {
		CardInstance spell = event(1, 1, 0, new Effect(EffectKind.DAMAGE, EffectScope.TARGET, 2),
				new Effect(EffectKind.TRAP, EffectScope.TARGET, 0));
		Match match = match(side().hand(spell).resource(2), side().board(onBoard(character(2, 1, 1, 5, null), false)));

		assertThat(ENGINE.legalMoves(match, Side.PLAYER)).containsExactly(new Move.PlayCard(1, new Target.Character(2)));
		assertIllegal(match, Side.PLAYER, new Move.PlayCard(1, new Target.Player(Side.PLAYER)));
		assertIllegal(match, Side.PLAYER, new Move.PlayCard(1));
	}

	@Test
	void targetedEventRequiresTargetAndRejectsTargetWhenNoneIsNeeded() {
		CardInstance targeted = event(1, 1, 0, new Effect(EffectKind.HEAL, EffectScope.TARGET, 2));
		CardInstance untargeted = event(2, 1, 0, new Effect(EffectKind.STRESS, EffectScope.OPPONENT, 2));
		Match match = match(side().hand(targeted, untargeted).resource(3), side());

		assertIllegal(match, Side.PLAYER, new Move.PlayCard(1));
		assertIllegal(match, Side.PLAYER, new Move.PlayCard(2, new Target.Player(Side.OPPONENT)));
		assertThat(ENGINE.legalMoves(match, Side.PLAYER)).contains(new Move.PlayCard(2, null));
	}

	@Test
	void areaDamageAffectsTheCorrectSidesAndDefeatsCharactersInOpponentThenOwnerOrder() {
		CardInstance spell = event(1, 1, 0, new Effect(EffectKind.DAMAGE, EffectScope.ALL_BOARDS, 2));
		CardInstance own = character(2, 1, 1, 2, null);
		CardInstance enemy = character(3, 1, 1, 2, null);
		Match match = match(side().hand(spell).resource(2).board(onBoard(own, false)),
				side().board(onBoard(enemy, false)));

		Match after = ENGINE.apply(match, Side.PLAYER, new Move.PlayCard(1));

		assertThat(after.player().board()).isEmpty();
		assertThat(after.opponent().board()).isEmpty();
		assertThat(after.log()).containsSubsequence(new CharacterDefeated(Side.OPPONENT, enemy),
				new CharacterDefeated(Side.PLAYER, own));
	}

	@Test
	void healsStressAndCharacterDamageWithoutGoingBelowZero() {
		CardInstance stressHeal = event(1, 1, 0, new Effect(EffectKind.HEAL, EffectScope.TARGET, 5));
		CardInstance damaged = character(2, 1, 1, 5, null);
		Match match = match(side().hand(stressHeal).resource(2).stress(2)
				.board(new BoardCharacter(damaged, 3, false)), side());

		Match stressAfter = ENGINE.apply(match, Side.PLAYER, new Move.PlayCard(1, new Target.Player(Side.PLAYER)));
		assertThat(stressAfter.player().stress()).isZero();
		assertThat(stressAfter.player().board().getFirst().damage()).isEqualTo(3);

		CardInstance characterHeal = event(3, 1, 0, new Effect(EffectKind.HEAL, EffectScope.TARGET, 5));
		Match characterAfter = ENGINE.apply(match(side().hand(characterHeal).resource(2)
				.board(new BoardCharacter(damaged, 3, false)), side()), Side.PLAYER,
				new Move.PlayCard(3, new Target.Character(2)));
		assertThat(characterAfter.player().board().getFirst().damage()).isZero();
	}

	@Test
	void everyImmediateScopeAppliesToExpectedState() {
		CardInstance stress = event(1, 1, 0, new Effect(EffectKind.STRESS, EffectScope.OPPONENT, 2));
		Match stressed = ENGINE.apply(match(side().hand(stress).resource(2), side()), Side.PLAYER,
				new Move.PlayCard(1));
		assertThat(stressed.opponent().stress()).isEqualTo(2);

		CardInstance draw = event(2, 1, 0, new Effect(EffectKind.DRAW, EffectScope.SELF, 2));
		CardInstance first = character(10, 1, 1, 1, null);
		CardInstance second = character(11, 1, 1, 1, null);
		Match drawn = ENGINE.apply(match(side().hand(draw).resource(2).deck(first, second), side()), Side.PLAYER,
				new Move.PlayCard(2));
		assertThat(drawn.player().hand()).containsExactly(first, second);

		CardInstance resource = event(3, 1, 0, new Effect(EffectKind.RESOURCE, EffectScope.SELF, 8));
		Match resourced = ENGINE.apply(match(side().hand(resource).resource(2), side()), Side.PLAYER,
				new Move.PlayCard(3));
		assertThat(resourced.player().resourceCurrent()).isEqualTo(MatchRules.RESOURCE_CAP - 1);

		CardInstance maxResource = event(4, 1, 0,
				new Effect(EffectKind.RESOURCE_MAX, EffectScope.SELF, 8));
		Match maxed = ENGINE.apply(match(side().hand(maxResource).resource(2), side()), Side.PLAYER,
				new Move.PlayCard(4));
		assertThat(maxed.player().resourceMax()).isEqualTo(MatchRules.RESOURCE_CAP);
		assertThat(maxed.player().resourceCurrent()).isEqualTo(1);

		CardInstance enemyBoard = event(5, 1, 0, new Effect(EffectKind.DAMAGE, EffectScope.ENEMY_BOARD, 1));
		CardInstance enemyUnit = character(20, 1, 1, 5, null);
		Match enemyHit = ENGINE.apply(match(side().hand(enemyBoard).resource(2),
				side().board(onBoard(enemyUnit, false))), Side.PLAYER, new Move.PlayCard(5));
		assertThat(enemyHit.opponent().board().getFirst().damage()).isEqualTo(1);

		CardInstance enemyAll = event(6, 1, 0, new Effect(EffectKind.DAMAGE, EffectScope.ENEMY_ALL, 2));
		Match enemyAllHit = ENGINE.apply(match(side().hand(enemyAll).resource(2),
				side().board(onBoard(enemyUnit, false))), Side.PLAYER, new Move.PlayCard(6));
		assertThat(enemyAllHit.opponent().board().getFirst().damage()).isEqualTo(2);
		assertThat(enemyAllHit.opponent().stress()).isEqualTo(2);

		CardInstance healOwn = event(7, 1, 0, new Effect(EffectKind.HEAL, EffectScope.OWN_BOARD, 2));
		CardInstance ownUnit = character(21, 1, 1, 5, null);
		Match ownHealed = ENGINE.apply(match(side().hand(healOwn).resource(2)
				.board(new BoardCharacter(ownUnit, 2, false)), side()), Side.PLAYER, new Move.PlayCard(7));
		assertThat(ownHealed.player().board().getFirst().damage()).isZero();

		CardInstance healOwnAll = event(8, 1, 0, new Effect(EffectKind.HEAL, EffectScope.OWN_ALL, 2));
		Match ownAllHealed = ENGINE.apply(match(side().hand(healOwnAll).resource(2).stress(2)
				.board(new BoardCharacter(ownUnit, 2, false)), side()), Side.PLAYER, new Move.PlayCard(8));
		assertThat(ownAllHealed.player().board().getFirst().damage()).isZero();
		assertThat(ownAllHealed.player().stress()).isZero();

		CardInstance healAll = event(9, 1, 0, new Effect(EffectKind.HEAL, EffectScope.ALL_BOARDS, 1));
		Match allHealed = ENGINE.apply(match(side().hand(healAll).resource(2)
				.board(new BoardCharacter(ownUnit, 2, false)),
				side().board(new BoardCharacter(enemyUnit, 2, false))), Side.PLAYER, new Move.PlayCard(9));
		assertThat(allHealed.player().board().getFirst().damage()).isEqualTo(1);
		assertThat(allHealed.opponent().board().getFirst().damage()).isEqualTo(1);
	}

	@Test
	void drawEffectBurnsAtFullHandAndEmptyDeckEndsMatchAndStopsNextEffect() {
		CardInstance top = character(50, 1, 1, 1, null);
		CardInstance next = character(51, 1, 1, 1, null);
		CardInstance draw = event(1, 1, 0, new Effect(EffectKind.DRAW, EffectScope.SELF, 2));
		Match full = ENGINE.apply(match(side().hand(draw).resource(2)
				.hand(filler(100, MatchRules.HAND_LIMIT - 1)).deck(next, top), side()), Side.PLAYER,
				new Move.PlayCard(1));
		assertThat(full.player().graveyard()).contains(top);
		assertThat(full.log()).contains(new CardBurned(Side.PLAYER, top));

		CardInstance empty = event(2, 1, 0, new Effect(EffectKind.DRAW, EffectScope.SELF, 1),
				new Effect(EffectKind.STRESS, EffectScope.OPPONENT, 3));
		Match lost = ENGINE.apply(match(side().hand(empty).resource(2), side()), Side.PLAYER,
				new Move.PlayCard(2));
		assertThat(lost.status()).isEqualTo(Status.FINISHED);
		assertThat(lost.result()).isEqualTo(new MatchResult(Side.OPPONENT, EndReason.EMPTY_DECK));
		assertThat(lost.opponent().stress()).isZero();
		assertThat(lost.log()).noneMatch(e -> e instanceof EffectApplied applied
				&& applied.effect().kind() == EffectKind.STRESS);
	}

	@Test
	void ownStressDamageAtLimitLosesAndStopsRemainingEffects() {
		CardInstance spell = event(1, 1, 0, new Effect(EffectKind.DAMAGE, EffectScope.TARGET, 2),
				new Effect(EffectKind.RESOURCE, EffectScope.SELF, 2));
		Match match = match(side().hand(spell).resource(2).stress(MatchRules.STRESS_LIMIT - 1), side());

		Match after = ENGINE.apply(match, Side.PLAYER, new Move.PlayCard(1, new Target.Player(Side.PLAYER)));

		assertThat(after.result()).isEqualTo(new MatchResult(Side.OPPONENT, EndReason.STRESS_LIMIT));
		assertThat(after.player().resourceCurrent()).isEqualTo(1);
		assertThat(after.log()).noneMatch(e -> e instanceof EffectApplied applied
				&& applied.effect().kind() == EffectKind.RESOURCE);
	}

	// ---- Gotowość ----

	@Test
	void freshCharacterCannotAttackButRushCan() {
		CardInstance plain = character(1, 1, 2, 2, null);
		CardInstance rush = character(2, 1, 2, 2, ability(AbilityKeyword.RUSH));
		Match match = match(side().hand(plain, rush).resource(5), side());

		Match after = ENGINE.apply(ENGINE.apply(match, Side.PLAYER, new Move.PlayCard(1)), Side.PLAYER,
				new Move.PlayCard(2));

		assertIllegal(after, Side.PLAYER, new Move.Attack(1, null));
		Match attacked = ENGINE.apply(after, Side.PLAYER, new Move.Attack(2, null));
		assertThat(attacked.opponent().stress()).isEqualTo(2);
	}

	@Test
	void secondAttackInSameTurnIsRejected() {
		Match match = match(side().board(onBoard(character(1, 1, 2, 5, null), true)), side());

		Match after = ENGINE.apply(match, Side.PLAYER, new Move.Attack(1, null));

		assertThat(after.player().board().getFirst().canAttack()).isFalse();
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

		assertThat(after.player().board()).containsExactly(new BoardCharacter(attacker, 1, false));
		assertThat(after.opponent().board()).containsExactly(new BoardCharacter(target, 2, false));
		assertThat(after.player().board().getFirst().remainingHealth()).isEqualTo(4);
	}

	@Test
	void guardBlocksStressAndNonGuardTargets() {
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
	void attackOnStressCanExceedLimitAndEndsMatch() {
		Match match = match(side().board(onBoard(character(1, 1, 5, 5, null), true)), side().stress(28));

		Match after = ENGINE.apply(match, Side.PLAYER, new Move.Attack(1, null));

		assertThat(after.opponent().stress()).isEqualTo(33);
		assertThat(after.status()).isEqualTo(Status.FINISHED);
		assertThat(after.result()).isEqualTo(new MatchResult(Side.PLAYER, EndReason.STRESS_LIMIT));
		assertThat(after.log().getLast()).isEqualTo(new MatchEnded(Side.PLAYER, after.result()));
	}

	// ---- Zdolności ----

	@Test
	void onPlayStressEndsMatchWhenLimitReached() {
		Match match = match(side().hand(character(1, 2, 1, 1, ability(AbilityKeyword.ON_PLAY_STRESS, 3))).resource(2),
				side().stress(27));

		Match after = ENGINE.apply(match, Side.PLAYER, new Move.PlayCard(1));

		assertThat(after.opponent().stress()).isEqualTo(30);
		assertThat(after.status()).isEqualTo(Status.FINISHED);
		assertThat(after.result()).isEqualTo(new MatchResult(Side.PLAYER, EndReason.STRESS_LIMIT));
	}

	@Test
	void healDoesNotGoBelowZero() {
		Match match = match(side().hand(character(1, 1, 1, 1, ability(AbilityKeyword.HEAL, 4))).resource(1).stress(3),
				side());

		Match after = ENGINE.apply(match, Side.PLAYER, new Move.PlayCard(1));

		assertThat(after.player().stress()).isZero();
		assertThat(after.status()).isEqualTo(Status.IN_PROGRESS);
	}

	@Test
	void overtimeAddsStressAtEndOfTurn() {
		CardInstance worker = character(1, 1, 1, 1, ability(AbilityKeyword.OVERTIME));
		Match match = match(side().board(onBoard(worker, false)), side().stress(4).deck(filler(200, 1)));

		Match after = ENGINE.apply(match, Side.PLAYER, new Move.EndTurn());

		assertThat(after.opponent().stress()).isEqualTo(5);
		assertThat(after.activeSide()).isEqualTo(Side.OPPONENT);
	}

	@Test
	void overtimeCanEndMatchAndStopsRemainingSteps() {
		CardInstance first = character(1, 1, 1, 1, ability(AbilityKeyword.OVERTIME));
		CardInstance second = character(2, 1, 1, 1, ability(AbilityKeyword.OVERTIME));
		Match match = match(side().board(onBoard(first, false), onBoard(second, false)),
				side().stress(29).deck(filler(200, 1)));

		Match after = ENGINE.apply(match, Side.PLAYER, new Move.EndTurn());

		assertThat(after.opponent().stress()).isEqualTo(30);
		assertThat(after.status()).isEqualTo(Status.FINISHED);
		assertThat(after.result()).isEqualTo(new MatchResult(Side.PLAYER, EndReason.STRESS_LIMIT));
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
		Match match = match(side().board(new BoardCharacter(mine, 3, false)),
				side().board(new BoardCharacter(theirs, 2, false)).deck(filler(200, 1)));

		Match after = ENGINE.apply(match, Side.PLAYER, new Move.EndTurn());

		assertThat(after.player().board()).containsExactly(new BoardCharacter(mine, 0, false));
		assertThat(after.opponent().board()).containsExactly(new BoardCharacter(theirs, 0, true));
	}

	@Test
	void endTurnSwitchesSideDrawsAndRaisesResource() {
		CardInstance top = character(200, 1, 1, 1, null);
		Match match = match(side().resource(1), side().resource(0).deck(top, character(201, 1, 1, 1, null)));

		Match after = ENGINE.apply(match, Side.PLAYER, new Move.EndTurn());

		assertThat(after.activeSide()).isEqualTo(Side.OPPONENT);
		assertThat(after.turnNumber()).isEqualTo(2);
		assertThat(after.opponent().hand()).containsExactly(top);
		assertThat(after.opponent().deck()).hasSize(1);
		assertThat(after.opponent().resourceMax()).isEqualTo(1);
		assertThat(after.opponent().resourceCurrent()).isEqualTo(1);
	}

	@Test
	void resourceStopsAtCap() {
		Match match = match(side(), side().resource(MatchRules.RESOURCE_CAP).spent().deck(filler(200, 1)));

		Match after = ENGINE.apply(match, Side.PLAYER, new Move.EndTurn());

		assertThat(after.opponent().resourceMax()).isEqualTo(MatchRules.RESOURCE_CAP);
		assertThat(after.opponent().resourceCurrent()).isEqualTo(MatchRules.RESOURCE_CAP);
	}

	@Test
	void eventExpiresAfterDurationOfOwnersTurns() {
		CardInstance lasting = event(1, 1, 2);
		Match match = match(side().hand(lasting).resource(1).deck(filler(100, 5)), side().deck(filler(200, 5)));

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
		Match match = match(side().board(onBoard(character(1, 1, 5, 5, null), true)).hand(event(2, 1, 0)).resource(5),
				side().stress(28));
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
					.resource(2)
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
		SideBuilder player = side().hand(character(1, 1, 1, 1, null), event(2, 1, 0)).resource(5);
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
		Match match = match(side().hand(rusher).resource(2), side().deck(top));

		Match after = ENGINE.apply(match, Side.PLAYER, new Move.PlayCard(1));
		after = ENGINE.apply(after, Side.PLAYER, new Move.Attack(1, null));
		after = ENGINE.apply(after, Side.PLAYER, new Move.EndTurn());

		assertThat(after.log()).containsExactly(new TurnStarted(Side.PLAYER, 1), new CardPlayed(Side.PLAYER, rusher),
				new Attacked(Side.PLAYER, rusher, null, 3, 0), new StressChanged(Side.PLAYER, Side.OPPONENT, 0, 3),
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
		return new BoardCharacter(instance, 0, canAttack);
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

		private int stress;

		private int resourceCurrent;

		private int resourceMax;

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

		SideBuilder stress(int stress) {
			this.stress = stress;
			return this;
		}

		SideBuilder resource(int resource) {
			this.resourceCurrent = resource;
			this.resourceMax = resource;
			return this;
		}

		SideBuilder spent() {
			this.resourceCurrent = 0;
			return this;
		}

		MatchSide build() {
			return new MatchSide(FACTION, deck, hand, stress, resourceCurrent, resourceMax, board, List.of(), List.of());
		}

	}

}
