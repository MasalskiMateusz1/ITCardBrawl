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

	// Poprawny katalog w pamięci: 2 grywalne frakcje po 10 kart x2 + 10 neutralnych x1 w każdej talii.
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

	private static Card card(String id, String faction, int cost) {
		return new Card(id, faction, text("Karta " + id), cost % 2 == 0 ? CardType.EVENT : CardType.CHARACTER, cost,
				text("tekst"));
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
				text("tekst")));

		assertThatIllegalStateException().isThrownBy(this::build)
			.withMessageContaining("karta 'alpha-1': brak tekstu 'name' w języku PL");
	}

	@Test
	void cardNameBlankInEnglishIsRejected() {
		cards.set(0, new Card("alpha-1", "alpha", new LocalizedText("Karta", "  "), CardType.CHARACTER, 1,
				text("tekst")));

		assertThatIllegalStateException().isThrownBy(this::build)
			.withMessageContaining("karta 'alpha-1': brak tekstu 'name' w języku EN");
	}

	@Test
	void cardTextBlankInPolishIsRejected() {
		cards.set(0, new Card("alpha-1", "alpha", text("Karta"), CardType.CHARACTER, 1,
				new LocalizedText(" ", "text")));

		assertThatIllegalStateException().isThrownBy(this::build)
			.withMessageContaining("karta 'alpha-1': brak tekstu 'text' w języku PL");
	}

	@Test
	void cardTextMissingInEnglishIsRejected() {
		cards.set(0, new Card("alpha-1", "alpha", text("Karta"), CardType.CHARACTER, 1,
				new LocalizedText("tekst", null)));

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

		// Katalog jest niepoprawny (brak frakcji), ale komunikat nie może zgłaszać braku tekstów karty.
		assertThatIllegalStateException()
			.isThrownBy(() -> CardCatalogLoader.load(JsonMapper.builder().build(), resource))
			.withMessageContaining("karta 'x' wskazuje nieistniejącą frakcję 'f'")
			.withMessageNotContaining("brak tekstu");
	}

	@Test
	void cardWithoutTypeIsRejected() {
		cards.set(0, new Card("alpha-1", "alpha", text("Karta alpha-1"), null, 1, text("tekst")));

		assertThatIllegalStateException().isThrownBy(this::build)
			.withMessageContaining("karta 'alpha-1' nie ma typu");
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
