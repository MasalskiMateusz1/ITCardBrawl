package com.example.itcardbrawl.game;

/**
 * A character on the board: the card instance, the damage taken this turn and its readiness state
 * ({@code enteredThisTurn}, {@code attacked}). Attack, HP, keywords and being trapped depend on active
 * events - they are computed by {@link CharacterStats}, not by this record.
 */
public record BoardCharacter(CardInstance instance, int damage, boolean enteredThisTurn, boolean attacked) {

	public BoardCharacter withDamage(int damage) {
		return new BoardCharacter(instance, damage, enteredThisTurn, attacked);
	}

	public BoardCharacter withAttackReady(boolean ready) {
		return new BoardCharacter(instance, damage, !ready, false);
	}

	public BoardCharacter markAttacked() {
		return new BoardCharacter(instance, damage, enteredThisTurn, true);
	}

}
