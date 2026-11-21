package com.example.itcardbrawl.game;

/**
 * A side's move in a match. Both the player and the computer make moves through {@link MatchEngine#apply}.
 */
public sealed interface Move {

	record PlayCard(int instanceId, Target target) implements Move {

		public PlayCard(int instanceId) {
			this(instanceId, null);
		}

	}

	/** A character attack; {@code targetId == null} means an attack on the player (the opponent's HP). */
	record Attack(int attackerId, Integer targetId) implements Move {
	}

	record EndTurn() implements Move {
	}

}
