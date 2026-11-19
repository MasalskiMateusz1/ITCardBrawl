package com.example.itcardbrawl.game;

import java.util.List;

/**
 * Stan jednej strony partii. {@code deck} jest uporządkowany od wierzchu.
 */
public record MatchSide(Faction faction, List<CardInstance> deck, List<CardInstance> hand, int stress,
		int resourceCurrent, int resourceMax) {

	public MatchSide {
		deck = List.copyOf(deck);
		hand = List.copyOf(hand);
	}

}
