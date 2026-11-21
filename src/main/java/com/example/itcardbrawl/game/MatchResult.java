package com.example.itcardbrawl.game;

/**
 * The result of a finished match: the winner and the reason the other side lost.
 */
public record MatchResult(Match.Side winner, EndReason reason) {

	public enum EndReason {
		/** The loser's HP dropped to 0 or below. */
		HP_DEPLETED,
		/** The loser had to draw a card from an empty deck. */
		EMPTY_DECK
	}

}
