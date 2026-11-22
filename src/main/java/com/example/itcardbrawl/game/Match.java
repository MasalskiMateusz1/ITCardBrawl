package com.example.itcardbrawl.game;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * State of a player's match against the computer. {@code result} is {@code null} while the match is in progress;
 * {@code log} holds the events of the whole match in chronological order.
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

	/** The match is in progress and it is the player's turn (the only time the player's turn limit runs). */
	public boolean isPlayerTurn() {
		return status == Status.IN_PROGRESS && activeSide == Side.PLAYER;
	}

	/**
	 * A copy of the match with a log entry inserted at position {@code index}. The log is not rule state, so adding
	 * an explanation outside {@link MatchEngine} does not change the course of the game.
	 */
	public Match withEventAt(int index, MatchEvent event) {
		List<MatchEvent> entries = new ArrayList<>(log);
		entries.add(index, event);
		return new Match(id, turnNumber, activeSide, player, opponent, status, result, entries);
	}

}
