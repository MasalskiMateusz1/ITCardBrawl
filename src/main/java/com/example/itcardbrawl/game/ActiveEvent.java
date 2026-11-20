package com.example.itcardbrawl.game;

/**
 * Wydarzenie w grze: działa jeszcze {@code turnsLeft} początków tur właściciela, potem trafia na cmentarz.
 */
public record ActiveEvent(CardInstance instance, int turnsLeft, Integer targetId) {

	public ActiveEvent(CardInstance instance, int turnsLeft) {
		this(instance, turnsLeft, null);
	}
}
