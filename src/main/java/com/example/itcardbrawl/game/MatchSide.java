package com.example.itcardbrawl.game;

import java.util.List;

/**
 * Stan jednej strony partii. {@code deck} jest uporządkowany od wierzchu, {@code board} i {@code graveyard}
 * w kolejności wejścia.
 */
public record MatchSide(Faction faction, List<CardInstance> deck, List<CardInstance> hand, int stress,
		int resourceCurrent, int resourceMax, List<BoardCharacter> board, List<CardInstance> graveyard,
		List<ActiveEvent> activeEvents) {

	public MatchSide {
		deck = List.copyOf(deck);
		hand = List.copyOf(hand);
		board = List.copyOf(board);
		graveyard = List.copyOf(graveyard);
		activeEvents = List.copyOf(activeEvents);
	}

}
