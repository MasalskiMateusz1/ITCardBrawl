package com.example.itcardbrawl.game;

/**
 * An event in play: it stays active for {@code turnsLeft} more starts of its owner's turns, then goes to the graveyard.
 */
public record ActiveEvent(CardInstance instance, int turnsLeft, Integer targetId) {

	public ActiveEvent(CardInstance instance, int turnsLeft) {
		this(instance, turnsLeft, null);
	}
}
