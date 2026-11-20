package com.example.itcardbrawl.game;

/** Cel wydarzenia: postać na stole albo strona partii. */
public sealed interface Target {

	record Character(int instanceId) implements Target {
	}

	record Player(Match.Side side) implements Target {
	}

}
