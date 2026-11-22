package com.example.itcardbrawl.game;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A validated, immutable catalog of factions, cards and decks. Invalid data throws
 * {@link IllegalStateException} already at construction, to stop the application from starting.
 */
public final class CardCatalog {

	static final int MIN_CARD_COST = 1;

	static final int MAX_CARD_COST = MatchRules.BUDGET_CAP;

	static final int MIN_ATTACK = 1;

	static final int MAX_ATTACK = 10;

	static final int MIN_HEALTH = 1;

	static final int MAX_HEALTH = 10;

	static final int MIN_DURATION = 0;

	static final int MAX_DURATION = 5;

	static final int MIN_COPIES = 1;

	static final int MAX_COPIES = 2;

	static final int PLAYABLE_FACTION_COUNT = 2;

	private final Map<String, Faction> factions;

	private final Map<String, Card> cards;

	private final Map<String, List<Card>> decks;

	private final Card secondPlayerBonus;

	/** A catalog without a second player bonus card. */
	public CardCatalog(List<Faction> factions, List<Card> cards, Map<String, List<DeckEntry>> decks) {
		this(factions, cards, decks, null);
	}

	/**
	 * @param secondPlayerBonus id of the card the non-starting side gets on top of its starting hand, or {@code null}
	 * for none; when given, it must be an existing neutral card
	 */
	public CardCatalog(List<Faction> factions, List<Card> cards, Map<String, List<DeckEntry>> decks,
			String secondPlayerBonus) {
		List<Faction> factionList = factions != null ? factions : List.of();
		List<Card> cardList = cards != null ? cards : List.of();
		Map<String, List<DeckEntry>> deckMap = decks != null ? decks : Map.of();

		List<String> errors = new ArrayList<>();
		Map<String, Faction> factionsById = indexFactions(factionList, errors);
		Map<String, Card> cardsById = indexCards(cardList, factionsById, errors);
		validatePlayableFactions(factionsById, deckMap, errors);
		Map<String, List<Card>> expandedDecks = expandDecks(deckMap, factionsById, cardsById, errors);
		Card bonus = resolveSecondPlayerBonus(secondPlayerBonus, factionsById, cardsById, errors);

		if (!errors.isEmpty()) {
			throw new IllegalStateException("Niepoprawny katalog kart:\n- " + String.join("\n- ", errors));
		}

		this.factions = Collections.unmodifiableMap(factionsById);
		this.cards = Collections.unmodifiableMap(cardsById);
		this.decks = Collections.unmodifiableMap(expandedDecks);
		this.secondPlayerBonus = bonus;
	}

	/** Playable factions in catalog file order. */
	public List<Faction> playableFactions() {
		return factions.values().stream().filter(Faction::playable).toList();
	}

	public Optional<Faction> faction(String id) {
		return Optional.ofNullable(id).map(factions::get);
	}

	public Optional<Card> card(String id) {
		return Optional.ofNullable(id).map(cards::get);
	}

	/** Neutral card = a card from the non-playable pool that is added to the decks. */
	public boolean isNeutral(Card card) {
		Faction faction = factions.get(card.faction());
		return faction != null && !faction.playable();
	}

	/** The card the non-starting side gets on top of its starting hand, outside its deck (if configured). */
	public Optional<Card> secondPlayerBonus() {
		return Optional.ofNullable(secondPlayerBonus);
	}

	/**
	 * The expanded deck of a playable faction ({@link MatchRules#DECK_SIZE} cards, copies repeated, file order).
	 *
	 * @throws IllegalArgumentException when the faction does not exist or is not playable
	 */
	public List<Card> deck(String factionId) {
		List<Card> deck = factionId != null ? decks.get(factionId) : null;
		if (deck == null) {
			throw new IllegalArgumentException("Brak talii dla frakcji: " + factionId);
		}
		return deck;
	}

	private static Map<String, Faction> indexFactions(List<Faction> factions, List<String> errors) {
		Map<String, Faction> byId = new LinkedHashMap<>();
		for (Faction faction : factions) {
			if (faction == null || isBlank(faction.id())) {
				errors.add("frakcja bez id");
				continue;
			}
			requireAllLanguages("frakcja", faction.id(), "name", faction.name(), errors);
			requireAllLanguages("frakcja", faction.id(), "description", faction.description(), errors);
			if (byId.putIfAbsent(faction.id(), faction) != null) {
				errors.add("powtórzone id frakcji '%s'".formatted(faction.id()));
			}
		}
		return byId;
	}

	private static Map<String, Card> indexCards(List<Card> cards, Map<String, Faction> factions, List<String> errors) {
		Map<String, Card> byId = new LinkedHashMap<>();
		for (Card card : cards) {
			if (card == null || isBlank(card.id())) {
				errors.add("karta bez id");
				continue;
			}
			if (byId.putIfAbsent(card.id(), card) != null) {
				errors.add("powtórzone id karty '%s'".formatted(card.id()));
			}
			if (!factions.containsKey(card.faction())) {
				errors.add("karta '%s' wskazuje nieistniejącą frakcję '%s'".formatted(card.id(), card.faction()));
			}
			requireAllLanguages("karta", card.id(), "name", card.name(), errors);
			requireAllLanguages("karta", card.id(), "text", card.text(), errors);
			if (card.type() == null) {
				errors.add("karta '%s' nie ma typu".formatted(card.id()));
			}
			if (card.cost() < MIN_CARD_COST || card.cost() > MAX_CARD_COST) {
				errors.add("karta '%s' ma koszt %d spoza zakresu %d–%d"
					.formatted(card.id(), card.cost(), MIN_CARD_COST, MAX_CARD_COST));
			}
			if (card.type() == CardType.CHARACTER) {
				validateCharacter(card, errors);
			}
			else if (card.type() == CardType.EVENT) {
				validateEvent(card, errors);
			}
		}
		return byId;
	}

	private static void validateCharacter(Card card, List<String> errors) {
		requireInRange(card.id(), "attack", card.attack(), MIN_ATTACK, MAX_ATTACK, errors);
		requireInRange(card.id(), "health", card.health(), MIN_HEALTH, MAX_HEALTH, errors);
		if (card.duration() != null) {
			errors.add("postać '%s' nie może mieć pola 'duration'".formatted(card.id()));
		}
		if (card.effects() != null) {
			errors.add("postać '%s' nie może mieć pola 'effects'".formatted(card.id()));
		}
		Ability ability = card.ability();
		if (ability == null) {
			return;
		}
		if (ability.keyword() == null) {
			errors.add("postać '%s' ma zdolność bez słowa kluczowego".formatted(card.id()));
		}
		else if (!ability.keyword().acceptsAmount(ability.amount())) {
			errors.add(ability.keyword().takesAmount()
					? "postać '%s': zdolność %s ma wartość %d spoza zakresu %d–%d".formatted(card.id(),
							ability.keyword(), ability.amount(), AbilityKeyword.MIN_AMOUNT, AbilityKeyword.MAX_AMOUNT)
					: "postać '%s': zdolność %s nie przyjmuje wartości, jest %d".formatted(card.id(),
							ability.keyword(), ability.amount()));
		}
	}

	private static void validateEvent(Card card, List<String> errors) {
		requireInRange(card.id(), "duration", card.duration(), MIN_DURATION, MAX_DURATION, errors);
		if (card.attack() != null) {
			errors.add("wydarzenie '%s' nie może mieć pola 'attack'".formatted(card.id()));
		}
		if (card.health() != null) {
			errors.add("wydarzenie '%s' nie może mieć pola 'health'".formatted(card.id()));
		}
		if (card.ability() != null) {
			errors.add("wydarzenie '%s' nie może mieć pola 'ability'".formatted(card.id()));
		}
		List<Effect> effects = card.effects();
		if (effects == null || effects.isEmpty()) {
			errors.add("wydarzenie '%s' musi mieć co najmniej jeden efekt".formatted(card.id()));
			return;
		}
		boolean hasModifier = false;
		for (int i = 0; i < effects.size(); i++) {
			Effect effect = effects.get(i);
			if (effect == null) {
				errors.add("wydarzenie '%s' ma pusty efekt na pozycji %d".formatted(card.id(), i + 1));
				continue;
			}
			if (effect.kind() == null) {
				errors.add("wydarzenie '%s' ma efekt bez rodzaju".formatted(card.id()));
				continue;
			}
			EffectKind kind = effect.kind();
			if (!kind.acceptsScope(effect.scope())) {
				errors.add("wydarzenie '%s': efekt %s ma niedozwolony zasięg %s".formatted(card.id(), kind,
						effect.scope()));
			}
			if (!kind.acceptsAmount(effect.amount())) {
				errors.add("wydarzenie '%s': efekt %s ma wartość %d spoza zakresu %d–%d".formatted(card.id(), kind,
						effect.amount(), kind.minAmount(), kind.maxAmount()));
			}
			hasModifier |= kind.modifier();
		}
		if (card.duration() != null && card.duration() == 0 && hasModifier) {
			errors.add("wydarzenie '%s' o czasie trwania 0 nie może mieć modyfikatorów".formatted(card.id()));
		}
		if (card.duration() != null && card.duration() > 0 && !hasModifier) {
			errors.add("wydarzenie '%s' o dodatnim czasie trwania musi mieć modyfikator".formatted(card.id()));
		}
	}

	private static void requireInRange(String id, String field, Integer value, int min, int max,
			List<String> errors) {
		if (value == null) {
			errors.add("karta '%s': brak pola '%s'".formatted(id, field));
		}
		else if (value < min || value > max) {
			errors.add("karta '%s': pole '%s' ma wartość %d spoza zakresu %d–%d"
				.formatted(id, field, value, min, max));
		}
	}

	private static Card resolveSecondPlayerBonus(String id, Map<String, Faction> factions, Map<String, Card> cards,
			List<String> errors) {
		if (id == null) {
			return null;
		}
		Card card = cards.get(id);
		if (card == null) {
			errors.add("secondPlayerBonus wskazuje nieistniejącą kartę '%s'".formatted(id));
			return null;
		}
		if (!isNonPlayable(factions.get(card.faction()))) {
			errors.add("secondPlayerBonus: karta '%s' nie jest neutralna".formatted(id));
			return null;
		}
		return card;
	}

	private static void validatePlayableFactions(Map<String, Faction> factions, Map<String, List<DeckEntry>> decks,
			List<String> errors) {
		List<Faction> playable = factions.values().stream().filter(Faction::playable).toList();
		if (playable.size() != PLAYABLE_FACTION_COUNT) {
			errors.add("wymagane dokładnie %d grywalne frakcje, jest %d"
				.formatted(PLAYABLE_FACTION_COUNT, playable.size()));
		}
		for (Faction faction : playable) {
			if (!decks.containsKey(faction.id())) {
				errors.add("grywalna frakcja '%s' nie ma talii".formatted(faction.id()));
			}
		}
	}

	private static Map<String, List<Card>> expandDecks(Map<String, List<DeckEntry>> decks,
			Map<String, Faction> factions, Map<String, Card> cards, List<String> errors) {
		Map<String, List<Card>> expanded = new LinkedHashMap<>();
		for (Map.Entry<String, List<DeckEntry>> deck : decks.entrySet()) {
			String factionId = deck.getKey();
			Faction faction = factions.get(factionId);
			if (faction == null) {
				errors.add("talia dla nieistniejącej frakcji '%s'".formatted(factionId));
				continue;
			}
			if (!faction.playable()) {
				errors.add("talia dla niegrywalnej frakcji '%s'".formatted(factionId));
				continue;
			}
			List<Card> deckCards = new ArrayList<>();
			int total = 0;
			for (DeckEntry entry : deck.getValue() != null ? deck.getValue() : List.<DeckEntry>of()) {
				if (entry == null) {
					errors.add("talia '%s' ma pustą pozycję".formatted(factionId));
					continue;
				}
				Card card = cards.get(entry.card());
				if (card == null) {
					errors.add("talia '%s' wskazuje nieistniejącą kartę '%s'".formatted(factionId, entry.card()));
				}
				else if (!factionId.equals(card.faction()) && !isNonPlayable(factions.get(card.faction()))) {
					errors.add("talia '%s' zawiera kartę '%s' obcej frakcji '%s'"
						.formatted(factionId, card.id(), card.faction()));
				}
				boolean copiesInRange = entry.copies() >= MIN_COPIES && entry.copies() <= MAX_COPIES;
				if (!copiesInRange) {
					errors.add("talia '%s': karta '%s' ma %d kopii, dozwolone %d–%d"
						.formatted(factionId, entry.card(), entry.copies(), MIN_COPIES, MAX_COPIES));
				}
				total += entry.copies();
				// Expand only valid entries — a typo in copies must not exhaust memory before errors are reported.
				if (card != null && copiesInRange) {
					for (int i = 0; i < entry.copies(); i++) {
						deckCards.add(card);
					}
				}
			}
			if (total != MatchRules.DECK_SIZE) {
				errors.add("talia '%s' ma %d kart, wymagane %d".formatted(factionId, total, MatchRules.DECK_SIZE));
			}
			expanded.put(factionId, List.copyOf(deckCards));
		}
		return expanded;
	}

	// Every text field must have a non-blank value in every supported language.
	private static void requireAllLanguages(String kind, String id, String field, LocalizedText value,
			List<String> errors) {
		for (Language language : Language.values()) {
			if (value == null || isBlank(value.in(language))) {
				errors.add("%s '%s': brak tekstu '%s' w języku %s".formatted(kind, id, field, language));
			}
		}
	}

	private static boolean isNonPlayable(Faction faction) {
		return faction != null && !faction.playable();
	}

	private static boolean isBlank(String value) {
		return value == null || value.isBlank();
	}

}
