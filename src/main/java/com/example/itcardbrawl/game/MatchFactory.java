package com.example.itcardbrawl.game;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.random.RandomGenerator;

import org.springframework.stereotype.Component;

/**
 * Tworzy partię w stanie startowym według {@link MatchRules}. Tasowanie talii i wybór zaczynającego
 * idą przez ten sam wstrzyknięty {@link RandomGenerator}.
 */
@Component
public class MatchFactory {

	private final CardCatalog catalog;

	private final RandomGenerator random;

	public MatchFactory(CardCatalog catalog, RandomGenerator random) {
		this.catalog = catalog;
		this.random = random;
	}

	/**
	 * @throws IllegalArgumentException gdy frakcja nie istnieje albo nie jest grywalna
	 */
	public Match create(String playerFactionId) {
		Faction playerFaction = catalog.faction(playerFactionId)
			.filter(Faction::playable)
			.orElseThrow(() -> new IllegalArgumentException("Nieznana lub niegrywalna frakcja: " + playerFactionId));
		Faction opponentFaction = catalog.playableFactions().stream()
			.filter(f -> !f.id().equals(playerFaction.id()))
			.findFirst()
			.orElseThrow(() -> new IllegalStateException("Brak drugiej grywalnej frakcji dla komputera"));

		int[] nextInstanceId = { 1 };
		List<CardInstance> playerDeck = shuffledDeck(playerFaction, nextInstanceId);
		List<CardInstance> opponentDeck = shuffledDeck(opponentFaction, nextInstanceId);
		Match.Side activeSide = random.nextBoolean() ? Match.Side.PLAYER : Match.Side.OPPONENT;

		return new Match(UUID.randomUUID(), 1, activeSide,
				deal(playerFaction, playerDeck, activeSide == Match.Side.PLAYER),
				deal(opponentFaction, opponentDeck, activeSide == Match.Side.OPPONENT));
	}

	private List<CardInstance> shuffledDeck(Faction faction, int[] nextInstanceId) {
		// Najpierw tasowanie, potem numeracja: instanceId zdradza tylko pozycję w talii, nie kartę.
		List<Card> cards = new ArrayList<>(catalog.deck(faction.id()));
		Collections.shuffle(cards, random);
		List<CardInstance> deck = new ArrayList<>(MatchRules.DECK_SIZE);
		for (Card card : cards) {
			deck.add(new CardInstance(nextInstanceId[0]++, card));
		}
		return deck;
	}

	private static MatchSide deal(Faction faction, List<CardInstance> deck, boolean active) {
		List<CardInstance> hand = deck.subList(0, MatchRules.STARTING_HAND);
		List<CardInstance> rest = deck.subList(MatchRules.STARTING_HAND, deck.size());
		int resource = active ? 1 : 0;
		return new MatchSide(faction, rest, hand, 0, resource, resource);
	}

}
