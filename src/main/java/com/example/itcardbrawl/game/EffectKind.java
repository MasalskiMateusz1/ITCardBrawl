package com.example.itcardbrawl.game;

import java.util.EnumSet;
import java.util.Set;

/** Rodzaje efektów wydarzeń wraz z ich kategorią, dozwolonymi zasięgami i zakresem wartości. */
public enum EffectKind {

	STRESS(true, 1, 10, EffectScope.OPPONENT),
	DAMAGE(true, 1, 10, EffectScope.TARGET, EffectScope.ENEMY_BOARD, EffectScope.ENEMY_ALL,
			EffectScope.ALL_BOARDS),
	HEAL(true, 1, 10, EffectScope.TARGET, EffectScope.OWN_BOARD, EffectScope.OWN_ALL,
			EffectScope.ALL_BOARDS),
	DRAW(true, 1, 5, EffectScope.SELF),
	RESOURCE(true, 1, 10, EffectScope.SELF),
	RESOURCE_MAX(true, 1, 10, EffectScope.SELF),
	ATTACK(false, -10, 10, EffectScope.TARGET, EffectScope.OWN_BOARD, EffectScope.ENEMY_BOARD,
			EffectScope.ALL_BOARDS),
	HEALTH(false, -10, 10, EffectScope.TARGET, EffectScope.OWN_BOARD, EffectScope.ENEMY_BOARD,
			EffectScope.ALL_BOARDS),
	GUARD(false, 0, 0, EffectScope.TARGET, EffectScope.OWN_BOARD, EffectScope.ENEMY_BOARD,
			EffectScope.ALL_BOARDS),
	RUSH(false, 0, 0, EffectScope.TARGET, EffectScope.OWN_BOARD, EffectScope.ENEMY_BOARD,
			EffectScope.ALL_BOARDS),
	TRAP(false, 0, 0, EffectScope.TARGET);

	private final boolean immediate;

	private final int minAmount;

	private final int maxAmount;

	private final Set<EffectScope> scopes;

	EffectKind(boolean immediate, int minAmount, int maxAmount, EffectScope... scopes) {
		this.immediate = immediate;
		this.minAmount = minAmount;
		this.maxAmount = maxAmount;
		this.scopes = Set.copyOf(EnumSet.copyOf(java.util.List.of(scopes)));
	}

	public boolean immediate() {
		return immediate;
	}

	public boolean modifier() {
		return !immediate;
	}

	public int minAmount() {
		return minAmount;
	}

	public int maxAmount() {
		return maxAmount;
	}

	public boolean acceptsScope(EffectScope scope) {
		return scope != null && scopes.contains(scope);
	}

	public boolean acceptsAmount(int amount) {
		return amount >= minAmount && amount <= maxAmount && (this != ATTACK || amount != 0)
				&& (this != HEALTH || amount != 0);
	}

	public String description(EffectScope scope, int amount) {
		return switch (this) {
			case STRESS -> "Zadaj przeciwnikowi %d stresu / Give the opponent %d stress".formatted(amount, amount);
			case DAMAGE -> damageDescription(scope, amount);
			case HEAL -> healDescription(scope, amount);
			case DRAW -> "Dobierz %d %s / Draw %d cards".formatted(amount,
					PolishNumberForms.form(amount, "kartę", "karty", "kart"), amount);
			case RESOURCE -> "Zyskaj %d pkt zasobu / Gain %d resource".formatted(amount, amount);
			case RESOURCE_MAX -> "Zwiększ maksymalny zasób o %d".formatted(amount)
					+ " / Increase maximum resource by %d".formatted(amount);
			case ATTACK -> modifierDescription(scope, amount, "ataku", "attack");
			case HEALTH -> modifierDescription(scope, amount, "zdrowia", "health");
			case GUARD -> keywordDescription(scope, "Ochrona", "Guard");
			case RUSH -> keywordDescription(scope, "Szarża", "Rush");
			case TRAP -> scope == EffectScope.TARGET ? "Uwięź wybraną postać / Trap a chosen character"
					: "Uwięź postacie / Trap characters";
		};
	}

	private static String damageDescription(EffectScope scope, int amount) {
		String n = PolishNumberForms.form(amount, "obrażenie", "obrażenia", "obrażeń");
		String en = "Deal %d damage".formatted(amount);
		return switch (scope) {
			case TARGET -> "Zadaj %d %s wybranej postaci lub graczowi / %s to a chosen character or player"
					.formatted(amount, n, en);
			case ENEMY_BOARD -> "Zadaj %d %s postaciom przeciwnika / %s to enemy characters"
					.formatted(amount, n, en);
			case ENEMY_ALL -> "Zadaj %d %s postaciom i %d stresu przeciwnikowi / %s to enemy characters and give the opponent %d stress"
					.formatted(amount, n, amount, en, amount);
			case ALL_BOARDS -> "Zadaj %d %s wszystkim postaciom / %s to all characters"
					.formatted(amount, n, en);
			default -> "Zadaj %d %s / %s".formatted(amount, n, en);
		};
	}

	private static String healDescription(EffectScope scope, int amount) {
		return switch (scope) {
			case TARGET -> "Przywróć %d pkt zdrowia wybranej postaci lub obniż stres gracza o %d / Restore %d health to a chosen character or reduce a player's stress by %d"
					.formatted(amount, amount, amount, amount);
			case OWN_BOARD -> "Przywróć własnym postaciom %d pkt zdrowia / Restore %d health to your characters"
					.formatted(amount, amount);
			case OWN_ALL -> "Przywróć własnym postaciom %d pkt zdrowia i obniż własny stres / Restore %d health to your characters and reduce your stress"
					.formatted(amount, amount);
			case ALL_BOARDS -> "Przywróć wszystkim postaciom %d pkt zdrowia / Restore %d health to all characters"
					.formatted(amount, amount);
			default -> "Przywróć %d pkt zdrowia / Restore %d health".formatted(amount, amount);
		};
	}

	private static String modifierDescription(EffectScope scope, int amount, String plNoun, String enNoun) {
		String sign = amount > 0 ? "+" : "−";
		String subject = switch (scope) {
			case TARGET -> "Wybrana postać";
			case OWN_BOARD -> "Własne postacie";
			case ENEMY_BOARD -> "Postacie przeciwnika";
			case ALL_BOARDS -> "Wszystkie postacie";
			default -> "Postacie";
		};
		String enSubject = switch (scope) {
			case TARGET -> "Chosen character";
			case OWN_BOARD -> "Your characters";
			case ENEMY_BOARD -> "Enemy characters";
			case ALL_BOARDS -> "All characters";
			default -> "Characters";
		};
		String amountText = sign + Math.abs(amount);
		return "%s: %s %s / %s: %s %s".formatted(subject, amountText, plNoun, enSubject, amountText, enNoun);
	}

	private static String keywordDescription(EffectScope scope, String plKeyword, String enKeyword) {
		String plSubject = switch (scope) {
			case TARGET -> "Wybrana postać";
			case OWN_BOARD -> "Własne postacie";
			case ENEMY_BOARD -> "Postacie przeciwnika";
			case ALL_BOARDS -> "Wszystkie postacie";
			default -> "Postacie";
		};
		String enSubject = switch (scope) {
			case TARGET -> "Chosen character";
			case OWN_BOARD -> "Your characters";
			case ENEMY_BOARD -> "Enemy characters";
			case ALL_BOARDS -> "All characters";
			default -> "Characters";
		};
		return "%s: %s / %s: %s".formatted(plSubject, plKeyword, enSubject, enKeyword);
	}

}
