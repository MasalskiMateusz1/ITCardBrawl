package com.example.itcardbrawl.game;

import java.util.List;

/**
 * State of one side of a match. {@code deck} is ordered from the top, {@code board} and {@code graveyard}
 * in order of entry.
 */
public record MatchSide(Faction faction, List<CardInstance> deck, List<CardInstance> hand, int hp,
		int budgetCurrent, int budgetMax, List<BoardCharacter> board, List<CardInstance> graveyard,
		List<ActiveEvent> activeEvents) {

	public MatchSide {
		deck = List.copyOf(deck);
		hand = List.copyOf(hand);
		board = List.copyOf(board);
		graveyard = List.copyOf(graveyard);
		activeEvents = List.copyOf(activeEvents);
	}

}
