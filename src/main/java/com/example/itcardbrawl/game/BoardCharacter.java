package com.example.itcardbrawl.game;

/**
 * Postać na stole: egzemplarz karty, obrażenia otrzymane w bieżącej turze oraz stan gotowości
 * ({@code enteredThisTurn}, {@code attacked}). Atak, HP, słowa kluczowe i uwięzienie zależą od aktywnych
 * wydarzeń - liczy je {@link CharacterStats}, nie ten rekord.
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
