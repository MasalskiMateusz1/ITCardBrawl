package com.example.itcardbrawl.game;

/**
 * Ruch strony w partii. Gracz i komputer wykonują ruchy przez {@link MatchEngine#apply}.
 */
public sealed interface Move {

	record PlayCard(int instanceId) implements Move {
	}

	/** Atak postaci; {@code targetId == null} oznacza atak w stres przeciwnika. */
	record Attack(int attackerId, Integer targetId) implements Move {
	}

	record EndTurn() implements Move {
	}

}
