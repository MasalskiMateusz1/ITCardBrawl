package com.example.itcardbrawl.game;

/**
 * Postać na stole: egzemplarz karty, obrażenia otrzymane w bieżącej turze i gotowość do ataku.
 * Pozostałe HP = {@code health - damage}.
 */
public record BoardCharacter(CardInstance instance, int damage, boolean canAttack) {

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
		return new BoardCharacter(instance, damage, canAttack);
	}

	public BoardCharacter withCanAttack(boolean canAttack) {
		return new BoardCharacter(instance, damage, canAttack);
	}

}
