package com.example.itcardbrawl.game;

/** An event target: a character on the board or a side of the match. */
public sealed interface Target {

	record Character(int instanceId) implements Target {
	}

	record Player(Match.Side side) implements Target {
	}

}
