package com.example.itcardbrawl.game;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
 *
 * <p>Character damage clears at the end of the turn, so it counts in full only on a character of the non-active side
 * that the active side can still finish this turn ({@link #SETUP_DAMAGE_SHARE}); any other damage counts with the small
 * {@link #DAMAGE_DISCOUNT}.
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

	/**
	 * Fixed value of a character being on the board. Deliberately not derived from {@code CardPricing.CARD_BASE}
	 * (-0.5): that constant prices the card slot, which the evaluator models with {@link #HAND_CARD_VALUE}; here the
	 * base keeps the board presence of any character (a target and a blocker) positive.
	 */
	static final double CHARACTER_BASE = 1.0;

	/**
	 * Score points (points of opponent HP) per cost point of {@code context/foundation/card-pricing.md}. The rule
	 * prices a point of damage to the opponent at {@link CardPricing#PLAYER_DAMAGE_PRICE} (0.65) cost points, and a
	 * point of opponent HP weighs {@link #ENEMY_HP_WEIGHT} (1.0) here, so one cost point is worth 1 / 0.65 ≈ 1.54 HP.
	 * Every weight below derived from a price is that price times this factor.
	 */
	private static final double HP_PER_COST_POINT = ENEMY_HP_WEIGHT / CardPricing.PLAYER_DAMAGE_PRICE;

	/** A point of attack: {@code ATTACK_PRICE} (4/7) of {@code card-pricing.md} on the HP scale, ≈ 0.88. */
	static final double ATTACK_WEIGHT = CardPricing.ATTACK_PRICE * HP_PER_COST_POINT;

	/**
	 * A point of health: {@code HEALTH_PRICE} (3/7) of {@code card-pricing.md} on the HP scale, ≈ 0.66 (attack :
	 * health = 1.0 : 0.75, as in the rule).
	 */
	static final double HEALTH_WEIGHT = CardPricing.HEALTH_PRICE * HP_PER_COST_POINT;

	/**
	 * Character damage clears at the end of the turn, so damage that will not lead to a defeat this turn is nearly
	 * worthless: a damaged character loses this share of its value in proportion to {@code damage / health}. It applies
	 * to the active side's own characters (counter-damage from its attacks) and to enemy characters the active side
	 * cannot finish this turn. Kept slightly above zero, so that of two otherwise equal moves the one taking less
	 * counter-damage wins.
	 */
	static final double DAMAGE_DISCOUNT = 0.1;

	/**
	 * Share of value lost in proportion to {@code damage / health} by a damaged character of the non-active side that
	 * the active side can still finish this turn (see {@link #finishable}): the damage is a setup for the finishing
	 * attack, so it counts in full. This is a one-move proxy for "hit, then finish with a second attacker" — without it,
	 * a hit on a character could never outscore an attack on the player with the same attacker.
	 */
	static final double SETUP_DAMAGE_SHARE = 1.0;

	/** A trapped character neither attacks nor blocks — it keeps only this fraction of its value. */
	static final double TRAPPED_FACTOR = 0.3;

	/** The {@code GUARD} ability: {@code GUARD_PRICE} (0.5) of {@code card-pricing.md} on the HP scale, ≈ 0.77. */
	static final double GUARD_VALUE = CardPricing.GUARD_PRICE * HP_PER_COST_POINT;

	/** Extra value of own Guard when own HP is low. */
	static final double GUARD_LOW_HP_EXTRA = 2.0;

	/**
	 * The {@code OVERTIME} ability: {@code OVERTIME_PRICE} (1.3 = {@code ON_PLAY_DAMAGE 2}) of {@code card-pricing.md}
	 * on the HP scale, i.e. exactly 2 points of opponent HP.
	 */
	static final double OVERTIME_VALUE = CardPricing.OVERTIME_PRICE * HP_PER_COST_POINT;

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
		List<BoardCharacter> board = match.side(owner).board();
		Set<Integer> finishable = owner == match.activeSide() ? Set.of() : finishable(match, owner);
		double total = 0;
		for (BoardCharacter character : board) {
			CharacterStats stats = CharacterStats.of(character, owner, playerEvents, opponentEvents);
			double damageShare = finishable.contains(character.instance().instanceId()) ? SETUP_DAMAGE_SHARE
					: DAMAGE_DISCOUNT;
			total += characterValue(stats, character.damage(), damageShare, extraGuard);
		}
		return total;
	}

	/**
	 * The damaged characters of the non-active side {@code owner} that the active side can still finish this turn. The
	 * follow-up capacity is the sum of attack of the active side's characters that can still attack (not attacked yet,
	 * not entered this turn unless they have Rush, not trapped); a target must be attackable (not trapped, and with Guard
	 * when its board has Guard). Targets are taken greedily by descending setup value, each consuming its remaining
	 * health from the capacity, so one capacity is not counted for several targets. Event cards in hand are not counted
	 * (the hand is hidden information for this evaluation).
	 */
	private static Set<Integer> finishable(Match match, Side owner) {
		List<ActiveEvent> playerEvents = match.player().activeEvents();
		List<ActiveEvent> opponentEvents = match.opponent().activeEvents();
		Side active = owner.opponent();
		int capacity = 0;
		for (BoardCharacter attacker : match.side(active).board()) {
			CharacterStats stats = CharacterStats.of(attacker, active, playerEvents, opponentEvents);
			if (!attacker.attacked() && (!attacker.enteredThisTurn() || stats.has(AbilityKeyword.RUSH))
					&& !stats.trapped()) {
				capacity += stats.attack();
			}
		}
		if (capacity == 0) {
			return Set.of();
		}
		List<BoardCharacter> board = match.side(owner).board();
		List<CharacterStats> stats = board.stream()
			.map(character -> CharacterStats.of(character, owner, playerEvents, opponentEvents))
			.toList();
		boolean guarded = stats.stream().anyMatch(s -> !s.trapped() && s.has(AbilityKeyword.GUARD));
		List<Integer> damaged = new ArrayList<>();
		for (int i = 0; i < board.size(); i++) {
			CharacterStats target = stats.get(i);
			if (board.get(i).damage() > 0 && !target.trapped() && (!guarded || target.has(AbilityKeyword.GUARD))) {
				damaged.add(i);
			}
		}
		damaged.sort(Comparator.comparingDouble((Integer i) -> characterValue(stats.get(i), 0, 0, false)
				- characterValue(stats.get(i), board.get(i).damage(), SETUP_DAMAGE_SHARE, false)).reversed());
		Set<Integer> result = new HashSet<>();
		for (int i : damaged) {
			int remaining = stats.get(i).remainingHealth(board.get(i).damage());
			if (remaining <= capacity) {
				capacity -= remaining;
				result.add(board.get(i).instance().instanceId());
			}
		}
		return result;
	}

	/**
	 * The value of a character: base, attack, health and abilities at full health, reduced by
	 * {@code damageShare * damage / health} of itself for damage taken this turn ({@link #DAMAGE_DISCOUNT} or
	 * {@link #SETUP_DAMAGE_SHARE}).
	 */
	static double characterValue(CharacterStats stats, int damage, double damageShare, boolean extraGuard) {
		double value = CHARACTER_BASE + ATTACK_WEIGHT * stats.attack() + HEALTH_WEIGHT * Math.max(0, stats.health());
		if (stats.has(AbilityKeyword.GUARD)) {
			value += GUARD_VALUE + (extraGuard ? GUARD_LOW_HP_EXTRA : 0);
		}
		if (stats.has(AbilityKeyword.OVERTIME)) {
			value += OVERTIME_VALUE;
		}
		if (damage > 0) {
			double damagedShare = stats.health() > 0 ? Math.min(1.0, (double) damage / stats.health()) : 1.0;
			value *= 1 - damageShare * damagedShare;
		}
		return stats.trapped() ? value * TRAPPED_FACTOR : value;
	}

}
