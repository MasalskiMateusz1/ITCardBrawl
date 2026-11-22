package com.example.itcardbrawl.game;

import java.util.ArrayList;
import java.util.List;

/**
 * The card pricing rule: the value of a card in cost points, and the dominance relation between cards. The rule, the
 * sources behind every price and the reasons for deviating from them are described in
 * {@code context/foundation/card-pricing.md}; every constant below refers to a section of that document.
 *
 * <p>The model is linear and additive: {@code value = CARD_BASE + body + ability} for a character and
 * {@code value = CARD_BASE + sum of effect prices} for an event. A card is priced correctly when
 * {@code |value - cost| <= TOLERANCE}. The class is a pure function of the card definition: it does not look at the
 * match state and is not wired into {@link CardCatalog} validation.
 */
public final class CardPricing {

	/**
	 * Constant term of every card ("Zasady ogólne"): a character gets {@code 2 * cost + 1} stat points, so the card
	 * itself is worth -0.5 cost points relative to its attributes.
	 */
	static final double CARD_BASE = -0.5;

	/** Price of a point of attack ("Postacie / Statystyki"): attack : health = 1.0 : 0.75, two points per cost. */
	static final double ATTACK_PRICE = 4.0 / 7.0;

	/** Price of a point of health ("Postacie / Statystyki"): 0.75 of {@link #ATTACK_PRICE}. */
	static final double HEALTH_PRICE = 3.0 / 7.0;

	/** {@code GUARD} ability ("Postacie / Zdolności"): one stat point (HS Taunt). */
	static final double GUARD_PRICE = 0.5;

	/** {@code RUSH} ability, per point of attack ("Postacie / Zdolności"): HS Charge, which also hits the player. */
	static final double RUSH_PRICE_PER_ATTACK = 0.33;

	/**
	 * A point of damage that can hit only the opponent ("Postacie / Zdolności", "Wydarzenia"): {@code ON_PLAY_DAMAGE}
	 * and {@code PLAYER_DAMAGE}.
	 */
	static final double PLAYER_DAMAGE_PRICE = 0.65;

	/** A point of own HP healed ("Postacie / Zdolności", "Wydarzenia"): {@code HEAL} ability and HEAL on a player. */
	static final double PLAYER_HEAL_PRICE = 0.3;

	/** {@code OVERTIME} ability ("Postacie / Zdolności"): starts at the price of {@code ON_PLAY_DAMAGE 2}. */
	static final double OVERTIME_PRICE = 2 * PLAYER_DAMAGE_PRICE;

	/** {@code DAMAGE} to a chosen character or player, per point up to {@link #DAMAGE_STEP} ("Wydarzenia"). */
	static final double DAMAGE_PRICE = 0.8;

	/** Damage amount above which a point of {@code DAMAGE} costs {@link #DAMAGE_PRICE_ABOVE_STEP} ("Wydarzenia"). */
	static final int DAMAGE_STEP = 3;

	/** Marginal price of a point of {@code DAMAGE} above {@link #DAMAGE_STEP} ("Wydarzenia"). */
	static final double DAMAGE_PRICE_ABOVE_STEP = 1.2;

	/**
	 * A point of health restored to one character ("Wydarzenia"): character damage clears at the end of every turn,
	 * so healing a character is worth almost nothing.
	 */
	static final double CHARACTER_HEAL_PRICE = 0.1;

	/** The first card drawn by {@code DRAW} ("Wydarzenia"): HS Draw Card. */
	static final double DRAW_FIRST_PRICE = 1.8;

	/** Every further card drawn by the same {@code DRAW} ("Wydarzenia"): MTG marginal price. */
	static final double DRAW_NEXT_PRICE = 1.2;

	/** A point of {@code BUDGET_GAIN} ("Wydarzenia"): own assumption. */
	static final double BUDGET_GAIN_PRICE = 0.5;

	/** A point of {@code BUDGET_MAX} ("Wydarzenia"): own assumption. */
	static final double BUDGET_MAX_PRICE = 1.5;

	/** Attack of a typical character, used to price a granted {@code RUSH} ("Wydarzenia / Modyfikatory"). */
	static final int REFERENCE_ATTACK = 3;

	/** {@code RUSH} granted to one character for one turn ("Wydarzenia / Modyfikatory"). */
	static final double RUSH_GRANT_PRICE = RUSH_PRICE_PER_ATTACK * REFERENCE_ATTACK;

	/** {@code TRAP} on one character for one turn ("Wydarzenia / Modyfikatory"): above HS Freeze (1.02). */
	static final double TRAP_PRICE = 2.0;

	/** Upper bound of a {@code TRAP} of any duration ("Wydarzenia / Modyfikatory"): HS Destroy Minion (5.33). */
	static final double TRAP_MAX = 5.33;

	/** Multiplier of a whole board relative to one character ("Wydarzenia / Zasięg"): HS Board Damage 1.84 / 0.82. */
	static final double BOARD_MULTIPLIER = 2.2;

	/**
	 * Multiplier of a whole board for a granted {@code RUSH} ("Wydarzenia / Zasięg"): only characters entering the
	 * board benefit, usually one per turn.
	 */
	static final double RUSH_BOARD_MULTIPLIER = 1.0;

	/** Discount of {@code ALL_BOARDS} relative to a one-sided board scope ("Wydarzenia / Zasięg"). */
	static final double ALL_BOARDS_DISCOUNT = 0.5;

	/** Weight of every further turn of a modifier relative to the previous one ("Wydarzenia / Czas trwania"). */
	static final double DURATION_DECAY = 0.75;

	/** Allowed {@code |value - cost|} ("Tolerancja"): costs are integers, so 0.5 means "cost = rounded value". */
	public static final double TOLERANCE = 0.5;

	// Guards the tolerance comparison against floating-point noise.
	private static final double EPSILON = 1e-9;

	private CardPricing() {
	}

	/** The value of a card in cost points. */
	public static double value(Card card) {
		return switch (card.type()) {
			case CHARACTER -> CARD_BASE + card.attack() * ATTACK_PRICE + card.health() * HEALTH_PRICE
					+ abilityValue(card.ability(), card.attack());
			case EVENT -> {
				int duration = card.duration() != null ? card.duration() : 0;
				double total = CARD_BASE;
				for (Effect effect : card.effects()) {
					total += effectValue(effect, duration);
				}
				yield total;
			}
		};
	}

	/** {@code value(card) - cost}: positive = the card is stronger than its cost. */
	public static double deviation(Card card) {
		return value(card) - card.cost();
	}

	/** Whether the card's value is within {@link #TOLERANCE} of its cost. */
	public static boolean withinTolerance(Card card) {
		return Math.abs(deviation(card)) <= TOLERANCE + EPSILON;
	}

	/**
	 * Whether {@code a} dominates {@code b} ("Karty zdominowane"): the same type, {@code a} costs no more, every stat
	 * and amount of {@code a} is at least as good, {@code a} has every ability/effect of {@code b}, and at least one
	 * of these comparisons is strict.
	 */
	public static boolean dominates(Card a, Card b) {
		if (a == null || b == null || a.type() == null || a.type() != b.type() || a.cost() > b.cost()) {
			return false;
		}
		boolean strict = a.cost() < b.cost();
		if (a.type() == CardType.CHARACTER) {
			if (a.attack() < b.attack() || a.health() < b.health()) {
				return false;
			}
			strict |= a.attack() > b.attack() || a.health() > b.health();
			Ability ownAbility = a.ability();
			Ability otherAbility = b.ability();
			if (otherAbility == null) {
				return strict || ownAbility != null;
			}
			if (ownAbility == null || ownAbility.keyword() != otherAbility.keyword()
					|| ownAbility.amount() < otherAbility.amount()) {
				return false;
			}
			return strict || ownAbility.amount() > otherAbility.amount();
		}
		int ownDuration = a.duration() != null ? a.duration() : 0;
		int otherDuration = b.duration() != null ? b.duration() : 0;
		if (ownDuration < otherDuration) {
			return false;
		}
		strict |= ownDuration > otherDuration;
		List<Effect> unmatched = new ArrayList<>(a.effects());
		for (Effect wanted : b.effects()) {
			Effect best = null;
			for (Effect candidate : unmatched) {
				if (sameFamily(candidate, wanted) && (best == null || strength(candidate) > strength(best))) {
					best = candidate;
				}
			}
			if (best == null || strength(best) < strength(wanted)) {
				return false;
			}
			strict |= strength(best) > strength(wanted);
			unmatched.remove(best);
		}
		return strict || !unmatched.isEmpty();
	}

	/** Price of a character ability ("Postacie / Zdolności"); {@code attack} scales {@code RUSH}. */
	static double abilityValue(Ability ability, int attack) {
		if (ability == null) {
			return 0;
		}
		return switch (ability.keyword()) {
			case ON_PLAY_DAMAGE -> ability.amount() * PLAYER_DAMAGE_PRICE;
			case HEAL -> ability.amount() * PLAYER_HEAL_PRICE;
			case GUARD -> GUARD_PRICE;
			case RUSH -> attack * RUSH_PRICE_PER_ATTACK;
			case OVERTIME -> OVERTIME_PRICE;
		};
	}

	/**
	 * Price of one event effect ("Wydarzenia"). Immediate effects happen once; a modifier is priced per character
	 * and turn, multiplied by the scope and by {@link #durationFactor(int)}.
	 */
	static double effectValue(Effect effect, int duration) {
		int amount = effect.amount();
		EffectScope scope = effect.scope();
		return switch (effect.kind()) {
			case PLAYER_DAMAGE -> amount * PLAYER_DAMAGE_PRICE;
			case DAMAGE -> switch (scope) {
				case TARGET -> damage(amount);
				case ENEMY_BOARD -> damage(amount) * BOARD_MULTIPLIER;
				case ENEMY_ALL -> damage(amount) * BOARD_MULTIPLIER + amount * PLAYER_DAMAGE_PRICE;
				case ALL_BOARDS -> damage(amount) * BOARD_MULTIPLIER * ALL_BOARDS_DISCOUNT;
				default -> 0;
			};
			case HEAL -> switch (scope) {
				// A chosen target: the player is the better choice, since character damage clears every turn.
				case TARGET -> amount * Math.max(PLAYER_HEAL_PRICE, CHARACTER_HEAL_PRICE);
				case OWN_BOARD -> amount * CHARACTER_HEAL_PRICE * BOARD_MULTIPLIER;
				case OWN_ALL -> amount * (CHARACTER_HEAL_PRICE * BOARD_MULTIPLIER + PLAYER_HEAL_PRICE);
				case ALL_BOARDS -> amount * CHARACTER_HEAL_PRICE * BOARD_MULTIPLIER * ALL_BOARDS_DISCOUNT;
				default -> 0;
			};
			case DRAW -> amount <= 0 ? 0 : DRAW_FIRST_PRICE + (amount - 1) * DRAW_NEXT_PRICE;
			case BUDGET_GAIN -> amount * BUDGET_GAIN_PRICE;
			case BUDGET_MAX -> amount * BUDGET_MAX_PRICE;
			case ATTACK -> modifier(amount * ATTACK_PRICE, scope, BOARD_MULTIPLIER) * durationFactor(duration);
			case HEALTH -> modifier(amount * HEALTH_PRICE, scope, BOARD_MULTIPLIER) * durationFactor(duration);
			case GUARD -> modifier(GUARD_PRICE, scope, BOARD_MULTIPLIER) * durationFactor(duration);
			case RUSH -> modifier(RUSH_GRANT_PRICE, scope, RUSH_BOARD_MULTIPLIER) * durationFactor(duration);
			case TRAP -> Math.min(TRAP_PRICE * durationFactor(duration), TRAP_MAX);
		};
	}

	/**
	 * Sum of turn weights of a modifier active for {@code duration} turns ("Wydarzenia / Czas trwania"): the first
	 * turn counts fully, each further one {@link #DURATION_DECAY} of the previous.
	 */
	static double durationFactor(int duration) {
		double factor = 0;
		double weight = 1;
		for (int turn = 0; turn < duration; turn++) {
			factor += weight;
			weight *= DURATION_DECAY;
		}
		return factor;
	}

	// DAMAGE to one character or player: the marginal price grows above DAMAGE_STEP.
	private static double damage(int amount) {
		return Math.min(amount, DAMAGE_STEP) * DAMAGE_PRICE + Math.max(0, amount - DAMAGE_STEP) * DAMAGE_PRICE_ABOVE_STEP;
	}

	// The owner's gain from a per-character modifier worth `perCharacter` (negative = a debuff) in the given scope.
	private static double modifier(double perCharacter, EffectScope scope, double boardMultiplier) {
		return switch (scope) {
			// A chosen character: the owner picks the side where the modifier helps.
			case TARGET -> Math.abs(perCharacter);
			case OWN_BOARD -> perCharacter * boardMultiplier;
			case ENEMY_BOARD -> -perCharacter * boardMultiplier;
			case ALL_BOARDS -> Math.abs(perCharacter) * boardMultiplier * ALL_BOARDS_DISCOUNT;
			default -> 0;
		};
	}

	// Effects are comparable only within the same kind, scope and direction of the amount.
	private static boolean sameFamily(Effect x, Effect y) {
		return x.kind() == y.kind() && x.scope() == y.scope() && Integer.signum(x.amount()) == Integer.signum(y.amount());
	}

	// How good the amount is for the owner: a buff to the enemy board or a debuff to the own board is a drawback.
	private static int strength(Effect effect) {
		boolean drawback = effect.kind().modifier()
				&& (effect.scope() == EffectScope.ENEMY_BOARD && effect.amount() > 0
						|| effect.scope() == EffectScope.OWN_BOARD && effect.amount() < 0);
		return drawback ? -Math.abs(effect.amount()) : Math.abs(effect.amount());
	}

}
