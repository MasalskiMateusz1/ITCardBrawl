package com.example.itcardbrawl.game;

/**
 * Ruch strony w partii. Gracz i komputer wykonują ruchy przez {@link MatchEngine#apply}.
 */
public sealed interface Move {

	record PlayCard(int instanceId, Target target) implements Move {

		public PlayCard(int instanceId) {
			this(instanceId, null);
		}

	}

	/** Atak postaci; {@code targetId == null} oznacza atak w gracza (HP przeciwnika). */
	record Attack(int attackerId, Integer targetId) implements Move {
	}

	record EndTurn() implements Move {
	}

}
