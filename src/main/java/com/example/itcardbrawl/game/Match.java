package com.example.itcardbrawl.game;

import java.util.UUID;

/**
 * Stan partii gracza przeciwko komputerowi.
 */
public record Match(UUID id, int turnNumber, Side activeSide, MatchSide player, MatchSide opponent) {

	public enum Side {
		PLAYER,
		OPPONENT
	}

}
