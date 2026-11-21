package com.example.itcardbrawl.game;

import java.util.List;

/**
 * The outcome of a start or a move: the final match state and the computer turn's steps to replay in the SPA. The steps
 * are not stored — they live only in the response. An empty {@code replay} means there is no computer turn to show.
 */
public record MatchOutcome(Match match, List<Match> replay) {

	public MatchOutcome {
		replay = List.copyOf(replay);
	}

}
