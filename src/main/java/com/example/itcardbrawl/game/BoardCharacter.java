package com.example.itcardbrawl.game;

/**
 * Postać na stole: egzemplarz karty, obrażenia otrzymane w bieżącej turze i gotowość do ataku.
 * Pozostałe HP = {@code health - damage}.
 */
public record BoardCharacter(CardInstance instance, int damage, boolean enteredThisTurn, boolean attacked) {

	public BoardCharacter(CardInstance instance, int damage, boolean canAttack) {
		this(instance, damage, !canAttack, false);
	}

	public int attack() {
		return instance.card().attack();
	}

	public int remainingHealth() {
		return instance.card().health() - damage;
	}

	public boolean isDefeated() {
		return remainingHealth() <= 0;
	}

	public boolean has(AbilityKeyword keyword) {
		Ability ability = instance.card().ability();
		return ability != null && ability.keyword() == keyword;
	}

	public BoardCharacter withDamage(int damage) {
		return new BoardCharacter(instance, damage, enteredThisTurn, attacked);
	}

	public boolean canAttack() {
		return !attacked && (!enteredThisTurn || has(AbilityKeyword.RUSH));
	}

	public BoardCharacter withAttackReady(boolean ready) {
		return new BoardCharacter(instance, damage, !ready, false);
	}

	public BoardCharacter markAttacked() {
		return new BoardCharacter(instance, damage, enteredThisTurn, true);
	}

}
