package com.example.itcardbrawl.game;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import tools.jackson.databind.json.JsonMapper;

/**
 * The card pricing rule ({@code context/foundation/card-pricing.md}) applied to the real catalog, plus unit tests of
 * the formula on in-memory cards.
 */
class CardPricingTests {

	private static final Offset<Double> PRECISION = within(1e-9);

	/**
	 * Cards allowed outside {@link CardPricing#TOLERANCE}: card id → justification. Only a conscious author decision
	 * belongs here (e.g. a deliberately weak joke card); the target is an empty list.
	 */
	private static final Map<String, String> TOLERANCE_EXCEPTIONS = Map.of();

	/** Cards allowed to be dominated: id of the dominated card → justification. */
	private static final Map<String, String> DOMINANCE_EXCEPTIONS = Map.of();

	// Every catalog card, including cards that are in no deck (e.g. neu-daily-standup).
	private static List<Card> realCards() {
		try (InputStream in = new ClassPathResource(CardCatalogLoader.CATALOG_LOCATION).getInputStream()) {
			CatalogData data = JsonMapper.builder().build().readValue(in, CatalogData.class);
			// Validate the data the same way the application does before pricing it.
			new CardCatalog(data.factions(), data.cards(), data.decks());
			return data.cards();
		}
		catch (IOException ex) {
			throw new IllegalStateException(ex);
		}
	}

	private static String describe(Card card) {
		return String.format(Locale.ROOT, "%s: wartość %.2f, koszt %d, odchylenie %+.2f", card.id(),
				CardPricing.value(card), card.cost(), CardPricing.deviation(card));
	}

	// ---- Real catalog ----

	@Test
	void everyRealCardIsWithinToleranceOrListed() {
		List<String> violations = realCards().stream()
			.filter(card -> !CardPricing.withinTolerance(card))
			.filter(card -> !TOLERANCE_EXCEPTIONS.containsKey(card.id()))
			.map(CardPricingTests::describe)
			.toList();

		assertThat(violations)
			.withFailMessage("Karty poza tolerancją ±%.1f (context/foundation/card-pricing.md):%n%s",
					CardPricing.TOLERANCE, String.join("\n", violations))
			.isEmpty();
	}

	@Test
	void noRealCardIsDominatedUnlessListed() {
		List<String> violations = dominatedPairs(realCards()).stream()
			.filter(pair -> !DOMINANCE_EXCEPTIONS.containsKey(pair.get(1).id()))
			.map(pair -> "%s dominuje %s (%s | %s)".formatted(pair.get(0).id(), pair.get(1).id(),
					describe(pair.get(0)), describe(pair.get(1))))
			.toList();

		assertThat(violations)
			.withFailMessage("Karty zdominowane (context/foundation/card-pricing.md):%n%s", String.join("\n", violations))
			.isEmpty();
	}

	@Test
	void dominanceScanFindsAStrictlyBetterCharacter() {
		// The phase 3 catalog pair: tst-automation (5, 4/5 OVERTIME) over neu-devops (5, 4/4 OVERTIME).
		Card automation = character(5, 4, 5, new Ability(AbilityKeyword.OVERTIME, 0));
		Card devops = character(5, 4, 4, new Ability(AbilityKeyword.OVERTIME, 0));

		assertThat(CardPricing.dominates(automation, devops)).isTrue();
		assertThat(CardPricing.dominates(devops, automation)).isFalse();
		assertThat(dominatedPairs(List.of(automation, devops))).singleElement().satisfies(pair -> {
			assertThat(pair.get(0)).isSameAs(automation);
			assertThat(pair.get(1)).isSameAs(devops);
		});
	}

	@Test
	void everyExceptionRefersToARealCardWithAJustification() {
		List<String> ids = realCards().stream().map(Card::id).toList();
		for (Map<String, String> exceptions : List.of(TOLERANCE_EXCEPTIONS, DOMINANCE_EXCEPTIONS)) {
			assertThat(ids).containsAll(exceptions.keySet());
			assertThat(exceptions.values()).allSatisfy(reason -> assertThat(reason).isNotBlank());
		}
	}

	private static List<List<Card>> dominatedPairs(List<Card> cards) {
		List<List<Card>> pairs = new ArrayList<>();
		for (Card a : cards) {
			for (Card b : cards) {
				if (a != b && CardPricing.dominates(a, b)) {
					pairs.add(List.of(a, b));
				}
			}
		}
		return pairs;
	}

	// ---- Coverage: every ability keyword and effect kind has a non-trivial price ----

	@Test
	void everyAbilityKeywordAddsValue() {
		Card vanilla = character(3, 3, 3, null);
		for (AbilityKeyword keyword : AbilityKeyword.values()) {
			Card withAbility = character(3, 3, 3, new Ability(keyword, keyword.takesAmount() ? 1 : 0));
			assertThat(CardPricing.value(withAbility)).as("cena %s", keyword).isGreaterThan(CardPricing.value(vanilla));
		}
	}

	@Test
	void everyEffectKindAddsValueInABeneficialScope() {
		for (EffectKind kind : EffectKind.values()) {
			EffectScope scope = Arrays.stream(EffectScope.values())
				.filter(kind::acceptsScope)
				.filter(s -> s != EffectScope.ENEMY_BOARD && s != EffectScope.ALL_BOARDS)
				.findFirst()
				.orElseThrow();
			int amount = kind.minAmount() == kind.maxAmount() ? kind.minAmount() : 1;
			Card event = event(kind.modifier() ? 1 : 0, new Effect(kind, scope, amount));
			assertThat(CardPricing.value(event)).as("cena %s/%s", kind, scope).isGreaterThan(CardPricing.CARD_BASE);
		}
	}

	// ---- Characters: stats ----

	@Test
	void vanillaCharacterGetsTwoStatPointsPerCostPlusOne() {
		for (int cost = 1; cost <= 9; cost++) {
			assertThat(CardPricing.value(character(cost, cost, cost + 1, null))).as("%d/%d", cost, cost + 1)
				.isCloseTo(cost, within(CardPricing.TOLERANCE));
			assertThat(CardPricing.value(character(cost, cost + 1, cost, null))).as("%d/%d", cost + 1, cost)
				.isCloseTo(cost, within(CardPricing.TOLERANCE));
		}
		// The top of the validation range: a bare 10/10 is the only vanilla line for cost 10, at the edge.
		assertThat(CardPricing.deviation(character(10, 10, 10, null))).isCloseTo(-0.5, PRECISION);
	}

	@Test
	void attackCostsMoreThanHealthInRatioOneToThreeQuarters() {
		assertThat(CardPricing.HEALTH_PRICE / CardPricing.ATTACK_PRICE).isCloseTo(0.75, PRECISION);
		assertThat(CardPricing.value(character(3, 4, 3, null))).isGreaterThan(CardPricing.value(character(3, 3, 4, null)));
		assertThat(CardPricing.value(character(1, 1, 1, null)))
			.isCloseTo(CardPricing.CARD_BASE + CardPricing.ATTACK_PRICE + CardPricing.HEALTH_PRICE, PRECISION);
	}

	// ---- Characters: abilities ----

	@Test
	void onPlayDamageIsPricedPerPoint() {
		assertThat(abilityPrice(new Ability(AbilityKeyword.ON_PLAY_DAMAGE, 1), 3))
			.isCloseTo(CardPricing.PLAYER_DAMAGE_PRICE, PRECISION);
		assertThat(abilityPrice(new Ability(AbilityKeyword.ON_PLAY_DAMAGE, 4), 3))
			.isCloseTo(4 * CardPricing.PLAYER_DAMAGE_PRICE, PRECISION);
	}

	@Test
	void healAbilityIsPricedPerPointAndIsTheCheapestAmount() {
		assertThat(abilityPrice(new Ability(AbilityKeyword.HEAL, 3), 3))
			.isCloseTo(3 * CardPricing.PLAYER_HEAL_PRICE, PRECISION);
		assertThat(CardPricing.PLAYER_HEAL_PRICE).isLessThan(CardPricing.PLAYER_DAMAGE_PRICE);
	}

	@Test
	void guardIsWorthOneStatPoint() {
		assertThat(abilityPrice(new Ability(AbilityKeyword.GUARD, 0), 3)).isCloseTo(0.5, PRECISION);
		assertThat(abilityPrice(new Ability(AbilityKeyword.GUARD, 0), 8)).isCloseTo(0.5, PRECISION);
	}

	@Test
	void rushGrowsWithAttack() {
		assertThat(abilityPrice(new Ability(AbilityKeyword.RUSH, 0), 1))
			.isCloseTo(CardPricing.RUSH_PRICE_PER_ATTACK, PRECISION);
		assertThat(abilityPrice(new Ability(AbilityKeyword.RUSH, 0), 6))
			.isCloseTo(6 * CardPricing.RUSH_PRICE_PER_ATTACK, PRECISION);
	}

	@Test
	void overtimeStartsAtThePriceOfOnPlayDamageTwo() {
		assertThat(abilityPrice(new Ability(AbilityKeyword.OVERTIME, 0), 3))
			.isCloseTo(abilityPrice(new Ability(AbilityKeyword.ON_PLAY_DAMAGE, 2), 3), PRECISION);
	}

	private static double abilityPrice(Ability ability, int attack) {
		return CardPricing.value(character(5, attack, 5, ability)) - CardPricing.value(character(5, attack, 5, null));
	}

	// ---- Events: immediate effects ----

	@Test
	void playerDamageHasTheSamePricePerPointAsOnPlayDamage() {
		assertThat(effectPrice(0, new Effect(EffectKind.PLAYER_DAMAGE, EffectScope.OPPONENT, 3)))
			.isCloseTo(abilityPrice(new Ability(AbilityKeyword.ON_PLAY_DAMAGE, 3), 3), PRECISION);
	}

	@Test
	void damageToATargetHasAGrowingMarginalPrice() {
		double three = effectPrice(0, new Effect(EffectKind.DAMAGE, EffectScope.TARGET, 3));
		double five = effectPrice(0, new Effect(EffectKind.DAMAGE, EffectScope.TARGET, 5));
		assertThat(three).isCloseTo(3 * CardPricing.DAMAGE_PRICE, PRECISION);
		assertThat(five - three).isCloseTo(2 * CardPricing.DAMAGE_PRICE_ABOVE_STEP, PRECISION);
		// Any target is an upside over the opponent only.
		assertThat(CardPricing.DAMAGE_PRICE).isGreaterThan(CardPricing.PLAYER_DAMAGE_PRICE);
	}

	@Test
	void healOnAPlayerCostsMoreThanHealOnCharacters() {
		assertThat(effectPrice(0, new Effect(EffectKind.HEAL, EffectScope.TARGET, 4)))
			.isCloseTo(4 * CardPricing.PLAYER_HEAL_PRICE, PRECISION);
		assertThat(effectPrice(0, new Effect(EffectKind.HEAL, EffectScope.OWN_BOARD, 4)))
			.isCloseTo(4 * CardPricing.CHARACTER_HEAL_PRICE * CardPricing.BOARD_MULTIPLIER, PRECISION);
		assertThat(effectPrice(0, new Effect(EffectKind.HEAL, EffectScope.OWN_ALL, 4)))
			.isCloseTo(effectPrice(0, new Effect(EffectKind.HEAL, EffectScope.OWN_BOARD, 4))
					+ effectPrice(0, new Effect(EffectKind.HEAL, EffectScope.TARGET, 4)), PRECISION);
	}

	@Test
	void drawHasADecreasingPricePerCard() {
		assertThat(effectPrice(0, new Effect(EffectKind.DRAW, EffectScope.SELF, 1)))
			.isCloseTo(CardPricing.DRAW_FIRST_PRICE, PRECISION);
		assertThat(effectPrice(0, new Effect(EffectKind.DRAW, EffectScope.SELF, 3)))
			.isCloseTo(CardPricing.DRAW_FIRST_PRICE + 2 * CardPricing.DRAW_NEXT_PRICE, PRECISION);
		assertThat(CardPricing.DRAW_NEXT_PRICE).isLessThan(CardPricing.DRAW_FIRST_PRICE);
	}

	@Test
	void budgetGainIsPricedPerPoint() {
		assertThat(effectPrice(0, new Effect(EffectKind.BUDGET_GAIN, EffectScope.SELF, 2)))
			.isCloseTo(2 * CardPricing.BUDGET_GAIN_PRICE, PRECISION);
	}

	@Test
	void budgetMaxIsALastingGainWorthMoreThanBudgetGain() {
		assertThat(effectPrice(0, new Effect(EffectKind.BUDGET_MAX, EffectScope.SELF, 1)))
			.isCloseTo(CardPricing.BUDGET_MAX_PRICE, PRECISION);
		assertThat(CardPricing.BUDGET_MAX_PRICE).isGreaterThan(CardPricing.BUDGET_GAIN_PRICE);
	}

	// ---- Events: modifiers ----

	@Test
	void attackModifierIsPricedLikeAttackPerTurn() {
		assertThat(effectPrice(1, new Effect(EffectKind.ATTACK, EffectScope.TARGET, 2)))
			.isCloseTo(2 * CardPricing.ATTACK_PRICE, PRECISION);
		// A debuff on a chosen character is worth as much as a buff.
		assertThat(effectPrice(1, new Effect(EffectKind.ATTACK, EffectScope.TARGET, -2)))
			.isCloseTo(2 * CardPricing.ATTACK_PRICE, PRECISION);
	}

	@Test
	void healthModifierIsPricedLikeHealthPerTurn() {
		assertThat(effectPrice(1, new Effect(EffectKind.HEALTH, EffectScope.OWN_BOARD, 1)))
			.isCloseTo(CardPricing.HEALTH_PRICE * CardPricing.BOARD_MULTIPLIER, PRECISION);
	}

	@Test
	void grantedGuardIsPricedLikeTheGuardAbility() {
		assertThat(effectPrice(1, new Effect(EffectKind.GUARD, EffectScope.TARGET, 0)))
			.isCloseTo(CardPricing.GUARD_PRICE, PRECISION);
	}

	@Test
	void grantedRushUsesTheReferenceAttackAndHelpsOneCharacterPerTurn() {
		assertThat(effectPrice(1, new Effect(EffectKind.RUSH, EffectScope.TARGET, 0)))
			.isCloseTo(CardPricing.REFERENCE_ATTACK * CardPricing.RUSH_PRICE_PER_ATTACK, PRECISION);
		assertThat(effectPrice(1, new Effect(EffectKind.RUSH, EffectScope.OWN_BOARD, 0)))
			.isCloseTo(CardPricing.RUSH_GRANT_PRICE * CardPricing.RUSH_BOARD_MULTIPLIER, PRECISION);
	}

	@Test
	void trapIsPricedAboveFreezeAndCappedAtDestroy() {
		double hsFreeze = 1.02;
		assertThat(effectPrice(1, new Effect(EffectKind.TRAP, EffectScope.TARGET, 0)))
			.isCloseTo(CardPricing.TRAP_PRICE, PRECISION)
			.isGreaterThan(hsFreeze);
		assertThat(effectPrice(5, new Effect(EffectKind.TRAP, EffectScope.TARGET, 0)))
			.isCloseTo(CardPricing.TRAP_MAX, PRECISION);
	}

	// ---- Scope ----

	@Test
	void enemyBoardDamageIsABoardMultipleOfTargetDamage() {
		assertThat(effectPrice(0, new Effect(EffectKind.DAMAGE, EffectScope.ENEMY_BOARD, 2)))
			.isCloseTo(CardPricing.BOARD_MULTIPLIER * effectPrice(0, new Effect(EffectKind.DAMAGE, EffectScope.TARGET, 2)),
					PRECISION);
	}

	@Test
	void enemyAllDamageAddsThePlayerPart() {
		assertThat(effectPrice(0, new Effect(EffectKind.DAMAGE, EffectScope.ENEMY_ALL, 3)))
			.isCloseTo(effectPrice(0, new Effect(EffectKind.DAMAGE, EffectScope.ENEMY_BOARD, 3))
					+ effectPrice(0, new Effect(EffectKind.PLAYER_DAMAGE, EffectScope.OPPONENT, 3)), PRECISION);
	}

	@Test
	void allBoardsIsDiscountedAgainstTheEnemyOnlyScope() {
		assertThat(effectPrice(0, new Effect(EffectKind.DAMAGE, EffectScope.ALL_BOARDS, 4)))
			.isCloseTo(CardPricing.ALL_BOARDS_DISCOUNT
					* effectPrice(0, new Effect(EffectKind.DAMAGE, EffectScope.ENEMY_BOARD, 4)), PRECISION);
		assertThat(effectPrice(2, new Effect(EffectKind.ATTACK, EffectScope.ALL_BOARDS, -1)))
			.isCloseTo(CardPricing.ALL_BOARDS_DISCOUNT
					* effectPrice(2, new Effect(EffectKind.ATTACK, EffectScope.ENEMY_BOARD, -1)), PRECISION);
	}

	@Test
	void modifierHelpingTheWrongSideHasNegativeValue() {
		assertThat(effectPrice(1, new Effect(EffectKind.ATTACK, EffectScope.ENEMY_BOARD, -2))).isPositive();
		assertThat(effectPrice(1, new Effect(EffectKind.ATTACK, EffectScope.ENEMY_BOARD, 2))).isNegative();
		assertThat(effectPrice(1, new Effect(EffectKind.HEALTH, EffectScope.OWN_BOARD, -1))).isNegative();
		assertThat(effectPrice(1, new Effect(EffectKind.GUARD, EffectScope.ENEMY_BOARD, 0))).isNegative();
	}

	// ---- Duration ----

	@Test
	void durationAddsDecayingTurns() {
		assertThat(CardPricing.durationFactor(0)).isZero();
		assertThat(CardPricing.durationFactor(1)).isCloseTo(1.0, PRECISION);
		assertThat(CardPricing.durationFactor(2)).isCloseTo(1.75, PRECISION);
		assertThat(CardPricing.durationFactor(3)).isCloseTo(1.75 + 0.5625, PRECISION);
		Effect buff = new Effect(EffectKind.ATTACK, EffectScope.OWN_BOARD, 1);
		assertThat(effectPrice(3, buff)).isCloseTo(CardPricing.durationFactor(3) * effectPrice(1, buff), PRECISION);
	}

	@Test
	void durationDoesNotScaleImmediateEffects() {
		Effect damage = new Effect(EffectKind.DAMAGE, EffectScope.ENEMY_BOARD, 2);
		Effect buff = new Effect(EffectKind.ATTACK, EffectScope.OWN_BOARD, 1);
		assertThat(CardPricing.value(event(3, damage, buff)) - CardPricing.value(event(3, buff)))
			.isCloseTo(effectPrice(0, damage), PRECISION);
	}

	@Test
	void eventValueIsTheSumOfItsEffects() {
		Effect draw = new Effect(EffectKind.DRAW, EffectScope.SELF, 1);
		Effect damage = new Effect(EffectKind.PLAYER_DAMAGE, EffectScope.OPPONENT, 1);
		assertThat(CardPricing.value(event(0, draw, damage)))
			.isCloseTo(CardPricing.CARD_BASE + effectPrice(0, draw) + effectPrice(0, damage), PRECISION);
	}

	private static double effectPrice(int duration, Effect effect) {
		return CardPricing.value(event(duration, effect)) - CardPricing.CARD_BASE;
	}

	// ---- Dominance ----

	@Test
	void cheaperCharacterWithTheSameStatsDominates() {
		assertThat(CardPricing.dominates(character(3, 3, 3, null), character(4, 3, 3, null))).isTrue();
		assertThat(CardPricing.dominates(character(4, 3, 3, null), character(3, 3, 3, null))).isFalse();
	}

	@Test
	void identicalCardsDoNotDominateEachOther() {
		Card guard = character(3, 2, 4, new Ability(AbilityKeyword.GUARD, 0));
		assertThat(CardPricing.dominates(guard, character(3, 2, 4, new Ability(AbilityKeyword.GUARD, 0)))).isFalse();
	}

	@Test
	void abilityMustCoverTheOtherAbility() {
		Card plain = character(3, 3, 3, null);
		Card guard = character(3, 3, 3, new Ability(AbilityKeyword.GUARD, 0));
		Card rush = character(3, 3, 3, new Ability(AbilityKeyword.RUSH, 0));
		assertThat(CardPricing.dominates(guard, plain)).isTrue();
		assertThat(CardPricing.dominates(plain, guard)).isFalse();
		assertThat(CardPricing.dominates(guard, rush)).isFalse();
		assertThat(CardPricing.dominates(character(3, 3, 3, new Ability(AbilityKeyword.HEAL, 3)),
				character(3, 3, 3, new Ability(AbilityKeyword.HEAL, 2)))).isTrue();
	}

	@Test
	void eventDominanceComparesAmountsInTheOwnersFavour() {
		Card strongDebuff = event(2, new Effect(EffectKind.ATTACK, EffectScope.ENEMY_BOARD, -3));
		Card weakDebuff = event(2, new Effect(EffectKind.ATTACK, EffectScope.ENEMY_BOARD, -2));
		assertThat(CardPricing.dominates(strongDebuff, weakDebuff)).isTrue();
		assertThat(CardPricing.dominates(weakDebuff, strongDebuff)).isFalse();

		Card withExtra = event(0, new Effect(EffectKind.DRAW, EffectScope.SELF, 1),
				new Effect(EffectKind.PLAYER_DAMAGE, EffectScope.OPPONENT, 1));
		Card drawOnly = event(0, new Effect(EffectKind.DRAW, EffectScope.SELF, 1));
		assertThat(CardPricing.dominates(withExtra, drawOnly)).isTrue();
		assertThat(CardPricing.dominates(drawOnly, withExtra)).isFalse();
	}

	@Test
	void cardsOfDifferentTypesAreNotCompared() {
		assertThat(CardPricing.dominates(character(1, 10, 10, null),
				event(0, new Effect(EffectKind.DRAW, EffectScope.SELF, 1)))).isFalse();
	}

	// ---- In-memory cards ----

	private static LocalizedText text(String value) {
		return new LocalizedText(value, value);
	}

	private static Card character(int cost, int attack, int health, Ability ability) {
		return new Card("c", "f", text("Postać"), CardType.CHARACTER, cost, text("tekst"), attack, health, ability,
				null, null);
	}

	private static Card event(int duration, Effect... effects) {
		return new Card("e", "f", text("Wydarzenie"), CardType.EVENT, 5, text("tekst"), null, null, null, duration,
				List.of(effects));
	}

}
