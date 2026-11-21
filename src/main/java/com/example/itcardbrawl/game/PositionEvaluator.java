package com.example.itcardbrawl.game;

import java.util.List;

import com.example.itcardbrawl.game.Match.Side;
import com.example.itcardbrawl.game.Match.Status;

import org.springframework.stereotype.Component;

/**
 * A numeric evaluation of the match state from one side's perspective, split into named components ({@link Score}). It
 * reads only data that is public to both players: HP, budgets, boards with active events (via {@link CharacterStats})
 * and the number of cards in hand and in the deck — never the hand contents or the deck order.
 *
 * <p>Balanced style: a point of opponent HP and a point of board value weigh about the same. When the opponent's
 * HP drops below {@link #ENEMY_HP_LOW}, the weight of HP damage grows; when own HP drops below
 * {@link #OWN_HP_LOW}, the weight of own HP, Guard and the threat from the opponent's board grows.
 */
@Component
public class PositionEvaluator {

	/** The value of a win; a loss is {@code -WIN_SCORE}. Effectively infinity. */
	static final double WIN_SCORE = 1_000_000;

	/** Weight of a point of opponent HP (every point taken raises the score). */
	static final double ENEMY_HP_WEIGHT = 1.0;

	/** The "opponent HP low" threshold: below it, every point weighs an extra {@link #ENEMY_HP_LOW_EXTRA}. */
	static final int ENEMY_HP_LOW = 10;

	static final double ENEMY_HP_LOW_EXTRA = 1.0;

	/** Weight of a point of own HP. */
	static final double OWN_HP_WEIGHT = 0.7;

	/** The "own HP low" threshold: below it, every point weighs an extra {@link #OWN_HP_LOW_EXTRA}. */
	static final int OWN_HP_LOW = 12;

	static final double OWN_HP_LOW_EXTRA = 1.0;

	/** Fixed value of a character being on the board. */
	static final double CHARACTER_BASE = 1.0;

	static final double ATTACK_WEIGHT = 1.0;

	static final double HEALTH_WEIGHT = 0.7;

	/**
	 * Character damage clears at the end of the turn, so it counts only partially: a character's health is
	 * {@code health - DAMAGE_DISCOUNT * damage}.
	 */
	static final double DAMAGE_DISCOUNT = 0.5;

	/** A trapped character neither attacks nor blocks — it keeps only this fraction of its value. */
	static final double TRAPPED_FACTOR = 0.3;

	static final double GUARD_VALUE = 1.0;

	/** Extra value of own Guard when own HP is low. */
	static final double GUARD_LOW_HP_EXTRA = 2.0;

	static final double OVERTIME_VALUE = 1.5;

	/** Multiplier of the opponent's board value (the threat) when own HP is low. */
	static final double ENEMY_BOARD_THREAT_FACTOR = 1.3;

	/** Value of a card in hand (only the number of cards counts). */
	static final double HAND_CARD_VALUE = 1.0;

	/** Penalty for an empty own deck: the next draw loses the match. */
	static final double EMPTY_DECK_PENALTY = 15.0;

	/**
	 * Value of a point of current budget. The budget refills at the start of the turn, so the weight is small:
	 * playing a card must not lose out to unspent budget.
	 */
	static final double BUDGET_CURRENT_WEIGHT = 0.25;

	/** Value of a point of maximum budget (a lasting gain). */
	static final double BUDGET_MAX_WEIGHT = 1.0;

	/**
	 * The score of a state from one side's perspective. {@link #total()} is the sum of the components; a finished
	 * match has an {@code outcome} component equal to {@code ±WIN_SCORE}.
	 */
	public record Score(double outcome, double ownHp, double enemyHp, double ownBoard, double enemyBoard,
			double cards, double resources) {

		public double total() {
			return outcome + ownHp + enemyHp + ownBoard + enemyBoard + cards + resources;
		}

	}

	public Score score(Match match, Side perspective) {
		MatchSide own = match.side(perspective);
		MatchSide enemy = match.side(perspective.opponent());
		double outcome = 0;
		if (match.status() == Status.FINISHED && match.result() != null) {
			outcome = match.result().winner() == perspective ? WIN_SCORE : -WIN_SCORE;
		}
		boolean ownHpLow = own.hp() < OWN_HP_LOW;

		double ownHp = OWN_HP_WEIGHT * own.hp() - OWN_HP_LOW_EXTRA * Math.max(0, OWN_HP_LOW - own.hp());
		double enemyHp = -ENEMY_HP_WEIGHT * enemy.hp() + ENEMY_HP_LOW_EXTRA * Math.max(0, ENEMY_HP_LOW - enemy.hp());

		double ownBoard = boardValue(match, perspective, ownHpLow);
		double enemyBoard = -boardValue(match, perspective.opponent(), false)
				* (ownHpLow ? ENEMY_BOARD_THREAT_FACTOR : 1.0);

		double cards = HAND_CARD_VALUE * (own.hand().size() - enemy.hand().size())
				- (own.deck().isEmpty() ? EMPTY_DECK_PENALTY : 0) + (enemy.deck().isEmpty() ? EMPTY_DECK_PENALTY : 0);
		double resources = BUDGET_CURRENT_WEIGHT * own.budgetCurrent() + BUDGET_MAX_WEIGHT * own.budgetMax();

		return new Score(outcome, ownHp, enemyHp, ownBoard, enemyBoard, cards, resources);
	}

	private static double boardValue(Match match, Side owner, boolean extraGuard) {
		List<ActiveEvent> playerEvents = match.player().activeEvents();
		List<ActiveEvent> opponentEvents = match.opponent().activeEvents();
		double total = 0;
		for (BoardCharacter character : match.side(owner).board()) {
			CharacterStats stats = CharacterStats.of(character, owner, playerEvents, opponentEvents);
			total += characterValue(stats, character.damage(), extraGuard);
		}
		return total;
	}

	static double characterValue(CharacterStats stats, int damage, boolean extraGuard) {
		double health = Math.max(0, stats.health() - DAMAGE_DISCOUNT * damage);
		double value = CHARACTER_BASE + ATTACK_WEIGHT * stats.attack() + HEALTH_WEIGHT * health;
		if (stats.has(AbilityKeyword.GUARD)) {
			value += GUARD_VALUE + (extraGuard ? GUARD_LOW_HP_EXTRA : 0);
		}
		if (stats.has(AbilityKeyword.OVERTIME)) {
			value += OVERTIME_VALUE;
		}
		return stats.trapped() ? value * TRAPPED_FACTOR : value;
	}

}
