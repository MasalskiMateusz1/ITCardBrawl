package com.example.itcardbrawl.game;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.List;
import java.util.UUID;

import com.example.itcardbrawl.game.Match.Side;
import com.example.itcardbrawl.game.Match.Status;
import com.example.itcardbrawl.game.MatchEvent.TurnStarted;
import com.example.itcardbrawl.game.MatchResult.EndReason;

import org.junit.jupiter.api.Test;

class PositionEvaluatorTests {

	private static final PositionEvaluator EVALUATOR = new PositionEvaluator();

	private static final Faction FACTION = new Faction("test", text("Test"), text("Test"), true);

	@Test
	void winAndLossAreExtremeValues() {
		Match won = finished(new MatchResult(Side.OPPONENT, EndReason.HP_DEPLETED));
		Match lost = finished(new MatchResult(Side.PLAYER, EndReason.EMPTY_DECK));
		double ongoing = EVALUATOR.score(match(side(1, List.of()), side(MatchRules.MAX_HP, List.of())), Side.OPPONENT)
			.total();

		assertThat(EVALUATOR.score(won, Side.OPPONENT).total()).isGreaterThan(PositionEvaluator.WIN_SCORE / 2);
		assertThat(EVALUATOR.score(lost, Side.OPPONENT).total()).isLessThan(-PositionEvaluator.WIN_SCORE / 2);
		assertThat(EVALUATOR.score(won, Side.PLAYER).total()).isLessThan(-PositionEvaluator.WIN_SCORE / 2);
		assertThat(Math.abs(ongoing)).isLessThan(PositionEvaluator.WIN_SCORE / 100);
	}

	@Test
	void trappedCharacterIsWorthLessThanFreeOne() {
		CardInstance own = character(1, 3, 3);
		CardInstance trap = new CardInstance(2, new Card("trap", FACTION.id(), text("Pułapka"), CardType.EVENT, 1,
				text("Opis"), null, null, null, 3, List.of(new Effect(EffectKind.TRAP, EffectScope.TARGET, 0))));
		MatchSide free = side(MatchRules.MAX_HP, List.of(new BoardCharacter(own, 0, false, false)));
		MatchSide trappedPlayer = new MatchSide(FACTION, List.of(), List.of(), MatchRules.MAX_HP, 0, 0, List.of(),
				List.of(), List.of(new ActiveEvent(trap, 3, own.instanceId())));

		PositionEvaluator.Score freeScore = EVALUATOR.score(match(side(MatchRules.MAX_HP, List.of()), free),
				Side.OPPONENT);
		PositionEvaluator.Score trappedScore = EVALUATOR.score(match(trappedPlayer, free), Side.OPPONENT);

		assertThat(trappedScore.ownBoard()).isPositive().isLessThan(freeScore.ownBoard());
		assertThat(trappedScore.total()).isLessThan(freeScore.total());
	}

	@Test
	void lowerEnemyHpRaisesScore() {
		MatchSide own = side(MatchRules.MAX_HP, List.of());

		double high = EVALUATOR.score(match(side(25, List.of()), own), Side.OPPONENT).total();
		double low = EVALUATOR.score(match(side(20, List.of()), own), Side.OPPONENT).total();

		assertThat(low).isGreaterThan(high);
	}

	@Test
	void enemyHpCountsMoreBelowThreshold() {
		MatchSide own = side(MatchRules.MAX_HP, List.of());
		int low = PositionEvaluator.ENEMY_HP_LOW;

		double aboveGain = enemyHpScore(low + 5, own) - enemyHpScore(low + 6, own);
		double belowGain = enemyHpScore(low - 3, own) - enemyHpScore(low - 2, own);

		assertThat(aboveGain).isPositive();
		assertThat(belowGain).isGreaterThan(aboveGain);
	}

	@Test
	void ownHpCountsMoreBelowThreshold() {
		MatchSide enemy = side(MatchRules.MAX_HP, List.of());
		int low = PositionEvaluator.OWN_HP_LOW;

		double aboveGain = ownHpScore(low + 6, enemy) - ownHpScore(low + 5, enemy);
		double belowGain = ownHpScore(low - 2, enemy) - ownHpScore(low - 3, enemy);

		assertThat(aboveGain).isPositive();
		assertThat(belowGain).isGreaterThan(aboveGain);
	}

	@Test
	void lowOwnHpMakesEnemyBoardMoreThreatening() {
		MatchSide enemy = side(MatchRules.MAX_HP, List.of(new BoardCharacter(character(1, 4, 4), 0, false, false)));

		double safe = EVALUATOR.score(match(enemy, side(MatchRules.MAX_HP, List.of())), Side.OPPONENT).enemyBoard();
		double danger = EVALUATOR.score(match(enemy, side(PositionEvaluator.OWN_HP_LOW - 1, List.of())),
				Side.OPPONENT).enemyBoard();

		assertThat(danger).isLessThan(safe).isNegative();
	}

	@Test
	void enemyDamageCountsInFullOnlyWhenItCanBeFinishedThisTurn() {
		CardInstance enemy = character(1, 4, 6);
		MatchSide undamaged = side(MatchRules.MAX_HP, List.of(new BoardCharacter(enemy, 0, false, false)));
		MatchSide damaged = side(MatchRules.MAX_HP, List.of(new BoardCharacter(enemy, 3, false, false)));
		MatchSide canFinish = side(MatchRules.MAX_HP, List.of(new BoardCharacter(character(2, 3, 5), 0, false, false)));
		MatchSide attacked = side(MatchRules.MAX_HP, List.of(new BoardCharacter(character(2, 3, 5), 0, false, true)));
		MatchSide tooWeak = side(MatchRules.MAX_HP, List.of(new BoardCharacter(character(2, 2, 5), 0, false, false)));

		double full = EVALUATOR.score(match(undamaged, canFinish), Side.OPPONENT).enemyBoard();
		double setup = EVALUATOR.score(match(damaged, canFinish), Side.OPPONENT).enemyBoard();
		double afterAttack = EVALUATOR.score(match(damaged, attacked), Side.OPPONENT).enemyBoard();
		double weak = EVALUATOR.score(match(damaged, tooWeak), Side.OPPONENT).enemyBoard();

		// 3 of 6 health damaged and finishable: half of the value is gone.
		assertThat(setup).isCloseTo(full * (1 - PositionEvaluator.SETUP_DAMAGE_SHARE * 0.5), within(1e-9));
		// Damage that clears at the end of the turn counts only with the small discount.
		double cleared = full * (1 - PositionEvaluator.DAMAGE_DISCOUNT * 0.5);
		assertThat(afterAttack).isCloseTo(cleared, within(1e-9));
		assertThat(weak).isCloseTo(cleared, within(1e-9));
		assertThat(full).isLessThan(afterAttack);
		assertThat(afterAttack).isLessThan(setup);
	}

	@Test
	void oneFollowUpAttackFinishesOnlyOneDamagedEnemy() {
		MatchSide enemy = side(MatchRules.MAX_HP, List.of(new BoardCharacter(character(1, 4, 6), 3, false, false),
				new BoardCharacter(character(2, 1, 6), 3, false, false)));
		MatchSide own = side(MatchRules.MAX_HP, List.of(new BoardCharacter(character(3, 3, 5), 0, false, false)));
		double big = PositionEvaluator.CHARACTER_BASE + PositionEvaluator.ATTACK_WEIGHT * 4
				+ PositionEvaluator.HEALTH_WEIGHT * 6;
		double small = PositionEvaluator.CHARACTER_BASE + PositionEvaluator.ATTACK_WEIGHT * 1
				+ PositionEvaluator.HEALTH_WEIGHT * 6;

		// The capacity of 3 finishes the more valuable character; the other one's damage will clear.
		assertThat(EVALUATOR.score(match(enemy, own), Side.OPPONENT).enemyBoard()).isCloseTo(
				-(big * (1 - PositionEvaluator.SETUP_DAMAGE_SHARE * 0.5)
						+ small * (1 - PositionEvaluator.DAMAGE_DISCOUNT * 0.5)),
				within(1e-9));
	}

	@Test
	void ownDamageDuringOwnTurnIsNearlyFree() {
		CardInstance own = character(1, 3, 6);
		MatchSide enemy = side(MatchRules.MAX_HP, List.of(new BoardCharacter(character(2, 5, 5), 0, false, false)));

		double undamaged = EVALUATOR.score(match(enemy,
				side(MatchRules.MAX_HP, List.of(new BoardCharacter(own, 0, false, true)))), Side.OPPONENT).ownBoard();
		double damaged = EVALUATOR.score(match(enemy,
				side(MatchRules.MAX_HP, List.of(new BoardCharacter(own, 3, false, true)))), Side.OPPONENT).ownBoard();

		assertThat(damaged).isLessThan(undamaged)
			.isCloseTo(undamaged * (1 - PositionEvaluator.DAMAGE_DISCOUNT * 0.5), within(1e-9));
	}

	private static double enemyHpScore(int enemyHp, MatchSide own) {
		return EVALUATOR.score(match(side(enemyHp, List.of()), own), Side.OPPONENT).total();
	}

	private static double ownHpScore(int ownHp, MatchSide enemy) {
		return EVALUATOR.score(match(enemy, side(ownHp, List.of())), Side.OPPONENT).total();
	}

	private static Match finished(MatchResult result) {
		MatchSide side = side(MatchRules.MAX_HP, List.of());
		return new Match(UUID.randomUUID(), 5, Side.OPPONENT, side, side, Status.FINISHED, result,
				List.of(new MatchEvent.MatchEnded(Side.OPPONENT, result)));
	}

	private static Match match(MatchSide player, MatchSide opponent) {
		return new Match(UUID.randomUUID(), 1, Side.OPPONENT, player, opponent, Status.IN_PROGRESS, null,
				List.of(new TurnStarted(Side.OPPONENT, 1)));
	}

	private static MatchSide side(int hp, List<BoardCharacter> board) {
		return new MatchSide(FACTION, List.of(), List.of(), hp, 0, 0, board, List.of(), List.of());
	}

	private static CardInstance character(int instanceId, int attack, int health) {
		return new CardInstance(instanceId, new Card("char-" + instanceId, FACTION.id(), text("Postać"),
				CardType.CHARACTER, 1, text("Opis"), attack, health, null, null, null));
	}

	private static LocalizedText text(String value) {
		return new LocalizedText(value, value);
	}

}
