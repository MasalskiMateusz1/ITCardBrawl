package com.example.itcardbrawl.game;

import java.time.Instant;
import java.util.List;

/**
 * The outcome of a start, a move or a read: the final match state, the computer turn's steps to replay in the SPA and
 * the deadline of the player's current turn. The steps live only in the response (except a computer turn kept for
 * the next read after a move rejected past the deadline, see {@link MatchStore.Entry#pendingReplay()}). An empty
 * {@code replay} means there is no computer turn to show.
 *
 * @param playerTurnDeadline the moment after which the server ends the player's turn; {@code null} when it is not the
 * player's turn
 */
public record MatchOutcome(Match match, List<Match> replay, Instant playerTurnDeadline) {

	public MatchOutcome {
		replay = List.copyOf(replay);
	}

}
