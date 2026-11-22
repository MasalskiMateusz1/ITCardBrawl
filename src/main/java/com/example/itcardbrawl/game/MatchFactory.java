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
		// The bonus card is numbered after both decks, so deck instanceIds still reveal only the deck position.
		CardInstance bonus = catalog.secondPlayerBonus().map(card -> new CardInstance(nextInstanceId[0]++, card))
			.orElse(null);

		// The starting state is already the start of turn 1: the starting side does not draw,
		// so the log has only TurnStarted.
		return new Match(UUID.randomUUID(), 1, activeSide,
				deal(playerFaction, playerDeck, activeSide == Match.Side.PLAYER, bonus),
				deal(opponentFaction, opponentDeck, activeSide == Match.Side.OPPONENT, bonus),
				Match.Status.IN_PROGRESS, null, List.of(new MatchEvent.TurnStarted(activeSide, 1)));
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

	/**
	 * The non-starting side gets {@code bonus} (when configured) on top of its starting hand: compensation for the
	 * starting side's tempo advantage (context/foundation/card-pricing.md, "Pomiar balansu").
	 */
	private static MatchSide deal(Faction faction, List<CardInstance> deck, boolean active, CardInstance bonus) {
		List<CardInstance> hand = new ArrayList<>(deck.subList(0, MatchRules.STARTING_HAND));
		if (!active && bonus != null) {
			hand.add(bonus);
		}
		List<CardInstance> rest = deck.subList(MatchRules.STARTING_HAND, deck.size());
		int budget = active ? 1 : 0;
		return new MatchSide(faction, rest, hand, MatchRules.MAX_HP, budget, budget, List.of(), List.of(), List.of());
	}

}
