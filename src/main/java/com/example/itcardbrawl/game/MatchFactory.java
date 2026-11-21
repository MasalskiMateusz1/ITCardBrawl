package com.example.itcardbrawl.game;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.random.RandomGenerator;

import org.springframework.stereotype.Component;

/**
 * Creates a match in its starting state according to {@link MatchRules}. Deck shuffling and the choice of the
 * starting side both go through the same injected {@link RandomGenerator}.
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
	 * @throws IllegalArgumentException when the faction does not exist or is not playable
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

		// The starting state is already the start of turn 1: the starting side does not draw,
		// so the log has only TurnStarted.
		return new Match(UUID.randomUUID(), 1, activeSide,
				deal(playerFaction, playerDeck, activeSide == Match.Side.PLAYER),
				deal(opponentFaction, opponentDeck, activeSide == Match.Side.OPPONENT), Match.Status.IN_PROGRESS,
				null, List.of(new MatchEvent.TurnStarted(activeSide, 1)));
	}

	private List<CardInstance> shuffledDeck(Faction faction, int[] nextInstanceId) {
		// Shuffle first, then number: instanceId reveals only the position in the deck, not the card.
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
		int budget = active ? 1 : 0;
		return new MatchSide(faction, rest, hand, MatchRules.MAX_HP, budget, budget, List.of(), List.of(), List.of());
	}

}
