package com.example.itcardbrawl.game;

import java.util.List;
import java.util.UUID;

/**
 * Stan partii gracza przeciwko komputerowi. {@code result} jest {@code null}, dopóki partia trwa;
 * {@code log} trzyma zdarzenia całej partii w kolejności chronologicznej.
 */
public record Match(UUID id, int turnNumber, Side activeSide, MatchSide player, MatchSide opponent, Status status,
		MatchResult result, List<MatchEvent> log) {

	public Match {
		log = List.copyOf(log);
	}

	public enum Side {
		PLAYER,
		OPPONENT;

		public Side opponent() {
			return this == PLAYER ? OPPONENT : PLAYER;
		}
	}

	public enum Status {
		IN_PROGRESS,
		FINISHED
	}

	public MatchSide side(Side side) {
		return side == Side.PLAYER ? player : opponent;
	}

}
