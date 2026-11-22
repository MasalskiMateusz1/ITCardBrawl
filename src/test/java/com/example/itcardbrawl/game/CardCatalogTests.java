package com.example.itcardbrawl.game;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;

import tools.jackson.databind.json.JsonMapper;

class CardCatalogTests {

	private List<Faction> factions;

	private List<Card> cards;

	private Map<String, List<DeckEntry>> decks;

	// A valid in-memory catalog: 2 playable factions of 10 cards x2 each + 10 neutral cards x1 in every deck.
	@BeforeEach
	void validData() {
		factions = new ArrayList<>(List.of(
				faction("alpha", true),
				faction("beta", true),
				faction("neutral", false)));
		cards = new ArrayList<>();
		decks = new LinkedHashMap<>();
		for (String faction : List.of("alpha", "beta")) {
			List<DeckEntry> deck = new ArrayList<>();
			for (int i = 1; i <= 10; i++) {
				cards.add(card(faction + "-" + i, faction, i));
				deck.add(new DeckEntry(faction + "-" + i, 2));
			}
			for (int i = 1; i <= 10; i++) {
				deck.add(new DeckEntry("neutral-" + i, 1));
			}
			decks.put(faction, deck);
		}
		for (int i = 1; i <= 10; i++) {
			cards.add(card("neutral-" + i, "neutral", i));
		}
	}

	private static LocalizedText text(String value) {
		return new LocalizedText(value, value + " (en)");
	}

	private static Faction faction(String id, boolean playable) {
		return new Faction(id, text("Frakcja " + id), text("opis"), playable);
	}

	// Even cost = event (duration 0), odd = a 1/1 character without an ability.
	private static Card card(String id, String faction, int cost) {
		return cost % 2 == 0 ? event(id, faction, cost, 0) : character(id, faction, cost, 1, 1, null);
	}

	private static Card character(String id, String faction, int cost, Integer attack, Integer health,
			Ability ability) {
		return new Card(id, faction, text("Karta " + id), CardType.CHARACTER, cost, text("tekst"), attack, health,
				ability, null, null);
	}

	private static Card event(String id, String faction, int cost, Integer duration) {
		return new Card(id, faction, text("Karta " + id), CardType.EVENT, cost, text("tekst"), null, null, null,
				duration, List.of(new Effect(duration != null && duration > 0 ? EffectKind.ATTACK : EffectKind.DRAW,
					duration != null && duration > 0 ? EffectScope.OWN_BOARD : EffectScope.SELF, 1)));
	}

	private CardCatalog build() {
		return new CardCatalog(factions, cards, decks);
	}

	@Test
	void realCatalogLoads() {
		CardCatalog catalog = CardCatalogLoader.load(JsonMapper.builder().build(),
				new ClassPathResource(CardCatalogLoader.CATALOG_LOCATION));

		assertThat(catalog.playableFactions()).extracting(Faction::id).containsExactly("developers", "testers");
		assertThat(catalog.playableFactions()).extracting(f -> f.name().in(Language.PL))
			.containsExactly("Developerzy", "Testerzy");
		assertThat(catalog.playableFactions()).extracting(f -> f.name().in(Language.EN)).allSatisfy(
				name -> assertThat(name).isNotBlank());
		assertThat(catalog.faction("neutral")).get().extracting(Faction::playable).isEqualTo(false);
		for (Faction faction : catalog.playableFactions()) {
			List<Card> deck = catalog.deck(faction.id());
			assertThat(deck).hasSize(MatchRules.DECK_SIZE);
			assertThat(deck).anyMatch(catalog::isNeutral);
			assertThat(deck).anyMatch(c -> c.faction().equals(faction.id()));
			assertThat(deck).extracting(Card::type).contains(CardType.CHARACTER, CardType.EVENT);
		}
		assertThat(catalog.playableFactions()).allSatisfy(faction -> assertThat(catalog.deck(faction.id()))
				.filteredOn(card -> card.type() == CardType.EVENT).hasSize(15));
		assertThat(catalog.card("neu-requirements-change")).get().satisfies(card -> {
			assertThat(card.effects()).containsExactly(new Effect(EffectKind.ATTACK, EffectScope.ENEMY_BOARD, -2),
					new Effect(EffectKind.GUARD, EffectScope.OWN_BOARD, 0));
		});
		assertThat(catalog.card("neu-daily-standup")).isPresent();
		assertThat(catalog.playableFactions()).allSatisfy(faction -> assertThat(catalog.deck(faction.id()))
				.extracting(Card::id).doesNotContain("neu-daily-standup"));
	}

	@Test
	void everyEventHasEffects() {
		CardCatalog catalog = CardCatalogLoader.load(JsonMapper.builder().build(),
				new ClassPathResource(CardCatalogLoader.CATALOG_LOCATION));
		List<String> eventIds = List.of("dev-stackoverflow", "dev-works-on-my-machine", "dev-friday-hotfix",
				"dev-force-push", "dev-legacy-refactor", "tst-vague-bug-report", "tst-edge-case", "tst-regression",
				"tst-flaky-test", "tst-production-bug", "neu-coffee", "neu-daily-standup", "neu-sprint-planning",
				"neu-meeting", "neu-deadline", "neu-requirements-change");
		for (String id : eventIds) {
			assertThat(catalog.card(id)).as("wydarzenie %s", id).get()
					.satisfies(card -> {
						assertThat(card.type()).isEqualTo(CardType.EVENT);
						assertThat(card.effects()).isNotEmpty();
					});
		}
	}

	@Test
	void everyEffectKindHasPolishAndEnglishDescription() {
		for (EffectKind kind : EffectKind.values()) {
			EffectScope scope = java.util.Arrays.stream(EffectScope.values()).filter(kind::acceptsScope).findFirst()
					.orElseThrow();
			int amount = kind.minAmount() == kind.maxAmount() ? kind.minAmount()
					: kind.minAmount() > 0 ? kind.minAmount() : 1;
			LocalizedText description = new Effect(kind, scope, amount).describe();
			assertThat(description.pl()).as("%s po polsku", kind).isNotBlank();
			assertThat(description.en()).as("%s po angielsku", kind).isNotBlank();
			assertThat(description.en()).isNotEqualTo(description.pl());
		}
	}

	@Test
	void polishDamageDescriptionUsesCorrectNumberForms() {
		assertThat(new Effect(EffectKind.DAMAGE, EffectScope.TARGET, 1).describe().pl())
				.isEqualTo("Zadaj 1 obrażenie wybranej postaci lub graczowi");
		assertThat(new Effect(EffectKind.DAMAGE, EffectScope.TARGET, 3).describe().pl())
				.isEqualTo("Zadaj 3 obrażenia wybranej postaci lub graczowi");
		assertThat(new Effect(EffectKind.DAMAGE, EffectScope.TARGET, 5).describe().pl())
				.isEqualTo("Zadaj 5 obrażeń wybranej postaci lub graczowi");
	}

	@Test
	void realCatalogHasCardStats() {
		CardCatalog catalog = CardCatalogLoader.load(JsonMapper.builder().build(),
				new ClassPathResource(CardCatalogLoader.CATALOG_LOCATION));

		Card backend = catalog.card("dev-backend").orElseThrow();
		assertThat(backend.attack()).isEqualTo(4);
		assertThat(backend.health()).isEqualTo(6);
		assertThat(backend.ability()).isEqualTo(new Ability(AbilityKeyword.GUARD, 0));
		assertThat(backend.duration()).isNull();
		assertThat(catalog.card("dev-senior")).get().extracting(Card::ability)
			.isEqualTo(new Ability(AbilityKeyword.HEAL, 3));
		assertThat(catalog.card("dev-fullstack")).get().extracting(Card::ability).isNull();
		Card flaky = catalog.card("tst-flaky-test").orElseThrow();
		assertThat(flaky.duration()).isEqualTo(3);
		assertThat(flaky.attack()).isNull();
		assertThat(flaky.health()).isNull();
		assertThat(flaky.ability()).isNull();
	}

	@Test
	void validInMemoryDataBuildsCatalog() {
		CardCatalog catalog = build();

		assertThat(catalog.playableFactions()).extracting(Faction::id).containsExactly("alpha", "beta");
		assertThat(catalog.deck("alpha")).hasSize(MatchRules.DECK_SIZE);
		assertThat(catalog.card("alpha-3")).get().extracting(Card::cost).isEqualTo(3);
		assertThat(catalog.card("missing")).isEmpty();
		assertThat(catalog.faction("missing")).isEmpty();
		assertThat(catalog.isNeutral(catalog.card("neutral-1").orElseThrow())).isTrue();
		assertThat(catalog.isNeutral(catalog.card("alpha-1").orElseThrow())).isFalse();
	}

	@Test
	void deckOfNeutralOrUnknownFactionIsRejected() {
		CardCatalog catalog = build();

		assertThatIllegalArgumentException().isThrownBy(() -> catalog.deck("neutral"));
		assertThatIllegalArgumentException().isThrownBy(() -> catalog.deck("missing"));
	}

	@Test
	void duplicateFactionIdIsRejected() {
		factions.add(faction("beta", false));

		assertThatIllegalStateException().isThrownBy(this::build)
			.withMessageContaining("powtórzone id frakcji 'beta'");
	}

	@Test
	void duplicateCardIdIsRejected() {
		cards.add(card("alpha-1", "alpha", 5));

		assertThatIllegalStateException().isThrownBy(this::build)
			.withMessageContaining("powtórzone id karty 'alpha-1'");
	}

	@Test
	void cardWithUnknownFactionIsRejected() {
		cards.add(card("ghost", "gamma", 5));

		assertThatIllegalStateException().isThrownBy(this::build)
			.withMessageContaining("karta 'ghost' wskazuje nieistniejącą frakcję 'gamma'");
	}

	@Test
	void costBelowRangeIsRejected() {
		cards.add(card("cheap", "alpha", 0));

		assertThatIllegalStateException().isThrownBy(this::build)
			.withMessageContaining("karta 'cheap' ma koszt 0");
	}

	@Test
	void costAboveRangeIsRejected() {
		cards.add(card("pricey", "alpha", 11));

		assertThatIllegalStateException().isThrownBy(this::build)
			.withMessageContaining("karta 'pricey' ma koszt 11");
	}

	@Test
	void fewerThanTwoPlayableFactionsIsRejected() {
		factions.set(1, faction("beta", false));
		decks.remove("beta");

		assertThatIllegalStateException().isThrownBy(this::build)
			.withMessageContaining("wymagane dokładnie 2 grywalne frakcje, jest 1");
	}

	@Test
	void moreThanTwoPlayableFactionsIsRejected() {
		factions.add(faction("gamma", true));
		decks.put("gamma", decks.get("alpha"));

		assertThatIllegalStateException().isThrownBy(this::build)
			.withMessageContaining("wymagane dokładnie 2 grywalne frakcje, jest 3");
	}

	@Test
	void playableFactionWithoutDeckIsRejected() {
		decks.remove("beta");

		assertThatIllegalStateException().isThrownBy(this::build)
			.withMessageContaining("grywalna frakcja 'beta' nie ma talii");
	}

	@Test
	void deckForNonPlayableFactionIsRejected() {
		decks.put("neutral", decks.get("alpha"));

		assertThatIllegalStateException().isThrownBy(this::build)
			.withMessageContaining("talia dla niegrywalnej frakcji 'neutral'");
	}

	@Test
	void deckForUnknownFactionIsRejected() {
		decks.put("gamma", decks.get("alpha"));

		assertThatIllegalStateException().isThrownBy(this::build)
			.withMessageContaining("talia dla nieistniejącej frakcji 'gamma'");
	}

	@Test
	void deckWithCardOfOtherPlayableFactionIsRejected() {
		decks.get("alpha").set(0, new DeckEntry("beta-1", 2));

		assertThatIllegalStateException().isThrownBy(this::build)
			.withMessageContaining("talia 'alpha' zawiera kartę 'beta-1' obcej frakcji 'beta'");
	}

	@Test
	void deckWithUnknownCardIsRejected() {
		decks.get("alpha").set(0, new DeckEntry("ghost", 2));

		assertThatIllegalStateException().isThrownBy(this::build)
			.withMessageContaining("talia 'alpha' wskazuje nieistniejącą kartę 'ghost'");
	}

	@Test
	void factionNameMissingInPolishIsRejected() {
		factions.set(0, new Faction("alpha", new LocalizedText(null, "Alpha"), text("opis"), true));

		assertThatIllegalStateException().isThrownBy(this::build)
			.withMessageContaining("frakcja 'alpha': brak tekstu 'name' w języku PL");
	}

	@Test
	void factionNameBlankInEnglishIsRejected() {
		factions.set(0, new Faction("alpha", new LocalizedText("Alfa", " "), text("opis"), true));

		assertThatIllegalStateException().isThrownBy(this::build)
			.withMessageContaining("frakcja 'alpha': brak tekstu 'name' w języku EN");
	}

	@Test
	void factionWithoutNameObjectIsRejectedInEveryLanguage() {
		factions.set(0, new Faction("alpha", null, text("opis"), true));

		assertThatIllegalStateException().isThrownBy(this::build)
			.withMessageContaining("frakcja 'alpha': brak tekstu 'name' w języku PL")
			.withMessageContaining("frakcja 'alpha': brak tekstu 'name' w języku EN");
	}

	@Test
	void factionDescriptionBlankInPolishIsRejected() {
		factions.set(0, new Faction("alpha", text("Alfa"), new LocalizedText("", "description"), true));

		assertThatIllegalStateException().isThrownBy(this::build)
			.withMessageContaining("frakcja 'alpha': brak tekstu 'description' w języku PL");
	}

	@Test
	void factionDescriptionMissingInEnglishIsRejected() {
		factions.set(0, new Faction("alpha", text("Alfa"), new LocalizedText("opis", null), true));

		assertThatIllegalStateException().isThrownBy(this::build)
			.withMessageContaining("frakcja 'alpha': brak tekstu 'description' w języku EN");
	}

	@Test
	void cardNameMissingInPolishIsRejected() {
		cards.set(0, new Card("alpha-1", "alpha", new LocalizedText(null, "Card"), CardType.CHARACTER, 1,
				text("tekst"), 1, 1, null, null, null));

		assertThatIllegalStateException().isThrownBy(this::build)
			.withMessageContaining("karta 'alpha-1': brak tekstu 'name' w języku PL");
	}

	@Test
	void cardNameBlankInEnglishIsRejected() {
		cards.set(0, new Card("alpha-1", "alpha", new LocalizedText("Karta", "  "), CardType.CHARACTER, 1,
				text("tekst"), 1, 1, null, null, null));

		assertThatIllegalStateException().isThrownBy(this::build)
			.withMessageContaining("karta 'alpha-1': brak tekstu 'name' w języku EN");
	}

	@Test
	void cardTextBlankInPolishIsRejected() {
		cards.set(0, new Card("alpha-1", "alpha", text("Karta"), CardType.CHARACTER, 1,
				new LocalizedText(" ", "text"), 1, 1, null, null, null));

		assertThatIllegalStateException().isThrownBy(this::build)
			.withMessageContaining("karta 'alpha-1': brak tekstu 'text' w języku PL");
	}

	@Test
	void cardTextMissingInEnglishIsRejected() {
		cards.set(0, new Card("alpha-1", "alpha", text("Karta"), CardType.CHARACTER, 1,
				new LocalizedText("tekst", null), 1, 1, null, null, null));

		assertThatIllegalStateException().isThrownBy(this::build)
			.withMessageContaining("karta 'alpha-1': brak tekstu 'text' w języku EN");
	}

	@Test
	void localizedTextIsReadFromJsonObject() {
		String json = """
				{ "factions": [], "cards": [
				  { "id": "x", "faction": "f", "name": { "pl": "Nazwa", "en": "Name" }, "type": "EVENT", "cost": 1,
				    "text": { "pl": "Tekst", "en": "Text" } }
				] }
				""";
		var resource = new ByteArrayResource(json.getBytes(StandardCharsets.UTF_8), "katalog dwujęzyczny");

		// The catalog is invalid (missing factions), but the message must not report missing card texts.
		assertThatIllegalStateException()
			.isThrownBy(() -> CardCatalogLoader.load(JsonMapper.builder().build(), resource))
			.withMessageContaining("karta 'x' wskazuje nieistniejącą frakcję 'f'")
			.withMessageNotContaining("brak tekstu");
	}

	@Test
	void cardWithoutTypeIsRejected() {
		cards.set(0, new Card("alpha-1", "alpha", text("Karta alpha-1"), null, 1, text("tekst"), null, null,
				null, null, null));

		assertThatIllegalStateException().isThrownBy(this::build)
			.withMessageContaining("karta 'alpha-1' nie ma typu");
	}

	@Test
	void characterWithoutAttackIsRejected() {
		cards.set(0, character("alpha-1", "alpha", 1, null, 1, null));

		assertThatIllegalStateException().isThrownBy(this::build)
			.withMessageContaining("karta 'alpha-1': brak pola 'attack'");
	}

	@Test
	void characterWithoutHealthIsRejected() {
		cards.set(0, character("alpha-1", "alpha", 1, 1, null, null));

		assertThatIllegalStateException().isThrownBy(this::build)
			.withMessageContaining("karta 'alpha-1': brak pola 'health'");
	}

	@Test
	void characterAttackAboveRangeIsRejected() {
		cards.set(0, character("alpha-1", "alpha", 1, 11, 1, null));

		assertThatIllegalStateException().isThrownBy(this::build)
			.withMessageContaining("karta 'alpha-1': pole 'attack' ma wartość 11 spoza zakresu 1–10");
	}

	@Test
	void characterHealthBelowRangeIsRejected() {
		cards.set(0, character("alpha-1", "alpha", 1, 1, 0, null));

		assertThatIllegalStateException().isThrownBy(this::build)
			.withMessageContaining("karta 'alpha-1': pole 'health' ma wartość 0 spoza zakresu 1–10");
	}

	@Test
	void characterWithDurationIsRejected() {
		cards.set(0, new Card("alpha-1", "alpha", text("Karta"), CardType.CHARACTER, 1, text("tekst"), 1, 1, null,
				2, null));

		assertThatIllegalStateException().isThrownBy(this::build)
			.withMessageContaining("postać 'alpha-1' nie może mieć pola 'duration'");
	}

	@Test
	void eventWithoutDurationIsRejected() {
		cards.set(1, event("alpha-2", "alpha", 2, null));

		assertThatIllegalStateException().isThrownBy(this::build)
			.withMessageContaining("karta 'alpha-2': brak pola 'duration'");
	}

	@Test
	void eventDurationAboveRangeIsRejected() {
		cards.set(1, event("alpha-2", "alpha", 2, 6));

		assertThatIllegalStateException().isThrownBy(this::build)
			.withMessageContaining("karta 'alpha-2': pole 'duration' ma wartość 6 spoza zakresu 0–5");
	}

	@Test
	void eventWithAttackIsRejected() {
		cards.set(1, new Card("alpha-2", "alpha", text("Karta"), CardType.EVENT, 2, text("tekst"), 3, null, null,
				0, List.of(new Effect(EffectKind.DRAW, EffectScope.SELF, 1))));

		assertThatIllegalStateException().isThrownBy(this::build)
			.withMessageContaining("wydarzenie 'alpha-2' nie może mieć pola 'attack'");
	}

	@Test
	void eventWithoutEffectsIsRejected() {
		cards.set(1, new Card("alpha-2", "alpha", text("Karta"), CardType.EVENT, 2, text("tekst"), null, null,
				null, 0, null));

		assertThatIllegalStateException().isThrownBy(this::build)
			.withMessageContaining("wydarzenie 'alpha-2' musi mieć co najmniej jeden efekt");
	}

	@Test
	void eventWithEmptyEffectsIsRejected() {
		cards.set(1, new Card("alpha-2", "alpha", text("Karta"), CardType.EVENT, 2, text("tekst"), null, null,
				null, 0, List.of()));

		assertThatIllegalStateException().isThrownBy(this::build)
			.withMessageContaining("wydarzenie 'alpha-2' musi mieć co najmniej jeden efekt");
	}

	@Test
	void eventEffectWithoutKindIsRejected() {
		cards.set(1, new Card("alpha-2", "alpha", text("Karta"), CardType.EVENT, 2, text("tekst"), null, null,
				null, 0, java.util.Arrays.asList(new Effect(null, EffectScope.SELF, 1))));

		assertThatIllegalStateException().isThrownBy(this::build)
			.withMessageContaining("wydarzenie 'alpha-2' ma efekt bez rodzaju");
	}

	@Test
	void eventEffectWithDisallowedScopeIsRejected() {
		cards.set(1, new Card("alpha-2", "alpha", text("Karta"), CardType.EVENT, 2, text("tekst"), null, null,
				null, 0, List.of(new Effect(EffectKind.DRAW, EffectScope.OPPONENT, 1))));

		assertThatIllegalStateException().isThrownBy(this::build)
			.withMessageContaining("efekt DRAW ma niedozwolony zasięg OPPONENT");
	}

	@Test
	void eventEffectOutsideAmountRangeIsRejected() {
		cards.set(1, new Card("alpha-2", "alpha", text("Karta"), CardType.EVENT, 2, text("tekst"), null, null,
				null, 0, List.of(new Effect(EffectKind.DRAW, EffectScope.SELF, 6))));

		assertThatIllegalStateException().isThrownBy(this::build)
			.withMessageContaining("efekt DRAW ma wartość 6 spoza zakresu 1–5");
	}

	@Test
	void zeroAttackModifierIsRejected() {
		cards.set(1, new Card("alpha-2", "alpha", text("Karta"), CardType.EVENT, 2, text("tekst"), null, null,
				null, 0, List.of(new Effect(EffectKind.ATTACK, EffectScope.OWN_BOARD, 0))));

		assertThatIllegalStateException().isThrownBy(this::build)
			.withMessageContaining("efekt ATTACK ma wartość 0 spoza zakresu -10–10");
	}

	@Test
	void modifierOnZeroDurationEventIsRejected() {
		cards.set(1, new Card("alpha-2", "alpha", text("Karta"), CardType.EVENT, 2, text("tekst"), null, null,
				null, 0, List.of(new Effect(EffectKind.ATTACK, EffectScope.OWN_BOARD, 1))));

		assertThatIllegalStateException().isThrownBy(this::build)
			.withMessageContaining("o czasie trwania 0 nie może mieć modyfikatorów");
	}

	@Test
	void positiveDurationWithoutModifierIsRejected() {
		cards.set(1, new Card("alpha-2", "alpha", text("Karta"), CardType.EVENT, 2, text("tekst"), null, null,
				null, 2, List.of(new Effect(EffectKind.DRAW, EffectScope.SELF, 1))));

		assertThatIllegalStateException().isThrownBy(this::build)
			.withMessageContaining("o dodatnim czasie trwania musi mieć modyfikator");
	}

	@Test
	void characterWithEffectsIsRejected() {
		cards.set(0, new Card("alpha-1", "alpha", text("Karta"), CardType.CHARACTER, 1, text("tekst"), 1, 1,
				null, null, List.of(new Effect(EffectKind.DRAW, EffectScope.SELF, 1))));

		assertThatIllegalStateException().isThrownBy(this::build)
			.withMessageContaining("postać 'alpha-1' nie może mieć pola 'effects'");
	}

	@Test
	void onPlayDamageWithZeroAmountIsRejected() {
		cards.set(0, character("alpha-1", "alpha", 1, 1, 1, new Ability(AbilityKeyword.ON_PLAY_DAMAGE, 0)));

		assertThatIllegalStateException().isThrownBy(this::build)
			.withMessageContaining("postać 'alpha-1': zdolność ON_PLAY_DAMAGE ma wartość 0 spoza zakresu 1–10");
	}

	@Test
	void abilityWithoutAmountIsReadFromJson() {
		String json = """
				{ "factions": [], "cards": [
				  { "id": "x", "faction": "f", "name": { "pl": "Nazwa", "en": "Name" }, "type": "CHARACTER", "cost": 1,
				    "text": { "pl": "Tekst", "en": "Text" }, "attack": 2, "health": 3, "ability": { "keyword": "GUARD" } }
				] }
				""";
		var resource = new ByteArrayResource(json.getBytes(StandardCharsets.UTF_8), "katalog ze zdolnością");

		// The catalog is invalid (missing factions), but GUARD without amount must pass card validation.
		assertThatIllegalStateException()
			.isThrownBy(() -> CardCatalogLoader.load(JsonMapper.builder().build(), resource))
			.withMessageContaining("karta 'x' wskazuje nieistniejącą frakcję 'f'")
			.withMessageNotContaining("postać 'x'")
			.withMessageNotContaining("karta 'x':");
	}

	@Test
	void effectWithoutAmountDefaultsToZero() throws Exception {
		Effect effect = JsonMapper.builder().build().readValue("{\"kind\":\"GUARD\",\"scope\":\"TARGET\"}",
				Effect.class);

		assertThat(effect.amount()).isZero();
	}

	@Test
	void everyAbilityKeywordIsDescribedInEveryLanguage() {
		for (AbilityKeyword keyword : AbilityKeyword.values()) {
			LocalizedText description = keyword.describe(2);
			for (Language language : Language.values()) {
				assertThat(description.in(language)).as("%s w języku %s", keyword, language).isNotBlank();
			}
		}
		assertThat(AbilityKeyword.ON_PLAY_DAMAGE.describe(4).pl()).isEqualTo("Po zagraniu: zadaj przeciwnikowi 4 obrażenia");
		assertThat(AbilityKeyword.ON_PLAY_DAMAGE.describe(4).en()).isEqualTo("On play: deal 4 damage to the opponent");
		assertThat(AbilityKeyword.ON_PLAY_DAMAGE.describe(5).pl()).isEqualTo("Po zagraniu: zadaj przeciwnikowi 5 obrażeń");
		assertThat(AbilityKeyword.HEAL.describe(3).pl()).isEqualTo("Po zagraniu: leczenie 3 HP");
		assertThat(AbilityKeyword.HEAL.describe(3).en()).isEqualTo("On play: heal 3 HP");
		assertThat(AbilityKeyword.OVERTIME.describe(0).pl()).isEqualTo("Nadgodziny: na koniec tury zadaj przeciwnikowi 1 obrażenie");
		assertThat(AbilityKeyword.OVERTIME.describe(0).en()).isEqualTo("Overtime: at end of turn deal 1 damage to the opponent");
	}

	@Test
	void secondPlayerBonusIsOptional() {
		assertThat(build().secondPlayerBonus()).isEmpty();
	}

	@Test
	void neutralSecondPlayerBonusIsAccepted() {
		CardCatalog catalog = new CardCatalog(factions, cards, decks, "neutral-2");

		assertThat(catalog.secondPlayerBonus()).get().extracting(Card::id).isEqualTo("neutral-2");
	}

	@Test
	void unknownSecondPlayerBonusIsRejected() {
		assertThatIllegalStateException().isThrownBy(() -> new CardCatalog(factions, cards, decks, "ghost"))
			.withMessageContaining("secondPlayerBonus wskazuje nieistniejącą kartę 'ghost'");
	}

	@Test
	void factionSecondPlayerBonusIsRejected() {
		assertThatIllegalStateException().isThrownBy(() -> new CardCatalog(factions, cards, decks, "alpha-2"))
			.withMessageContaining("secondPlayerBonus: karta 'alpha-2' nie jest neutralna");
	}

	@Test
	void emptyDeckEntryIsRejected() {
		decks.get("alpha").set(0, null);

		assertThatIllegalStateException().isThrownBy(this::build)
			.withMessageContaining("talia 'alpha' ma pustą pozycję");
	}

	@Test
	void deckWithFactionlessCardFailsWithReadableError() {
		cards.set(0, card("alpha-1", null, 1));

		assertThatIllegalStateException().isThrownBy(this::build)
			.withMessageContaining("karta 'alpha-1' wskazuje nieistniejącą frakcję 'null'");
	}

	@Test
	void hugeCopiesFailsWithReadableErrorWithoutExpanding() {
		decks.get("alpha").set(0, new DeckEntry("alpha-1", Integer.MAX_VALUE));

		assertThatIllegalStateException().isThrownBy(this::build)
			.withMessageContaining("talia 'alpha': karta 'alpha-1' ma %d kopii".formatted(Integer.MAX_VALUE));
	}

	@Test
	void zeroCopiesIsRejected() {
		decks.get("alpha").set(10, new DeckEntry("neutral-1", 0));
		decks.get("alpha").set(11, new DeckEntry("neutral-2", 2));

		assertThatIllegalStateException().isThrownBy(this::build)
			.withMessageContaining("talia 'alpha': karta 'neutral-1' ma 0 kopii");
	}

	@Test
	void moreThanTwoCopiesIsRejected() {
		decks.get("alpha").set(0, new DeckEntry("alpha-1", 3));
		decks.get("alpha").remove(19);

		assertThatIllegalStateException().isThrownBy(this::build)
			.withMessageContaining("talia 'alpha': karta 'alpha-1' ma 3 kopii");
	}

	@Test
	void deckSmallerThanThirtyIsRejected() {
		decks.get("alpha").remove(19);

		assertThatIllegalStateException().isThrownBy(this::build)
			.withMessageContaining("talia 'alpha' ma 29 kart, wymagane 30");
	}

	@Test
	void deckLargerThanThirtyIsRejected() {
		decks.get("alpha").set(10, new DeckEntry("neutral-1", 2));

		assertThatIllegalStateException().isThrownBy(this::build)
			.withMessageContaining("talia 'alpha' ma 31 kart, wymagane 30");
	}

	@Test
	void malformedJsonFailsWithReadableMessage() {
		var resource = new ByteArrayResource("{ \"factions\": [".getBytes(StandardCharsets.UTF_8), "zepsuty katalog");

		assertThatIllegalStateException()
			.isThrownBy(() -> CardCatalogLoader.load(JsonMapper.builder().build(), resource))
			.withMessageContaining("Nie udało się wczytać katalogu kart");
	}

}
