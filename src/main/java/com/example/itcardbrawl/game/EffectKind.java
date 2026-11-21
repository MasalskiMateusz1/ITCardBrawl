package com.example.itcardbrawl.game;

import java.util.EnumSet;
import java.util.Set;

/** Rodzaje efektów wydarzeń wraz z ich kategorią, dozwolonymi zasięgami i zakresem wartości. */
public enum EffectKind {

	PLAYER_DAMAGE(true, 1, 10, EffectScope.OPPONENT),
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

	public LocalizedText description(EffectScope scope, int amount) {
		return switch (this) {
			case PLAYER_DAMAGE -> text(
					"Zadaj przeciwnikowi %d %s".formatted(amount,
							PolishNumberForms.form(amount, "obrażenie", "obrażenia", "obrażeń")),
					"Deal %d damage to the opponent".formatted(amount));
			case DAMAGE -> damageDescription(scope, amount);
			case HEAL -> healDescription(scope, amount);
			case DRAW -> text("Dobierz %d %s".formatted(amount, PolishNumberForms.form(amount, "kartę", "karty", "kart")),
					"Draw %d %s".formatted(amount, amount == 1 ? "card" : "cards"));
			case RESOURCE -> text("Zyskaj %d pkt zasobu".formatted(amount), "Gain %d resource".formatted(amount));
			case RESOURCE_MAX -> text("Zwiększ maksymalny zasób o %d".formatted(amount),
					"Increase maximum resource by %d".formatted(amount));
			case ATTACK -> modifierDescription(scope, amount, "ataku", "attack");
			case HEALTH -> modifierDescription(scope, amount, "zdrowia", "health");
			case GUARD -> keywordDescription(scope, "Ochrona", "Guard");
			case RUSH -> keywordDescription(scope, "Szarża", "Rush");
			case TRAP -> scope == EffectScope.TARGET ? text("Uwięź wybraną postać", "Trap a chosen character")
					: text("Uwięź postacie", "Trap characters");
		};
	}

	private static LocalizedText text(String pl, String en) {
		return new LocalizedText(pl, en);
	}

	private static LocalizedText damageDescription(EffectScope scope, int amount) {
		String pl = "Zadaj %d %s".formatted(amount, PolishNumberForms.form(amount, "obrażenie", "obrażenia", "obrażeń"));
		String en = "Deal %d damage".formatted(amount);
		return switch (scope) {
			case TARGET -> text(pl + " wybranej postaci lub graczowi", en + " to a chosen character or player");
			case ENEMY_BOARD -> text(pl + " postaciom przeciwnika", en + " to enemy characters");
			case ENEMY_ALL -> text(pl + " wrogim postaciom i przeciwnikowi", en + " to enemy characters and the opponent");
			case ALL_BOARDS -> text(pl + " wszystkim postaciom", en + " to all characters");
			default -> text(pl, en);
		};
	}

	private static LocalizedText healDescription(EffectScope scope, int amount) {
		return switch (scope) {
			case TARGET -> text(
					"Przywróć %d pkt zdrowia wybranej postaci lub przywróć graczowi %d HP".formatted(amount, amount),
					"Restore %d health to a chosen character or restore %d HP to a player".formatted(amount, amount));
			case OWN_BOARD -> text("Przywróć własnym postaciom %d pkt zdrowia".formatted(amount),
					"Restore %d health to your characters".formatted(amount));
			case OWN_ALL -> text(
					"Przywróć własnym postaciom %d pkt zdrowia i przywróć sobie %d HP".formatted(amount, amount),
					"Restore %d health to your characters and restore %d HP to yourself".formatted(amount, amount));
			case ALL_BOARDS -> text("Przywróć wszystkim postaciom %d pkt zdrowia".formatted(amount),
					"Restore %d health to all characters".formatted(amount));
			default -> text("Przywróć %d pkt zdrowia".formatted(amount), "Restore %d health".formatted(amount));
		};
	}

	private static LocalizedText modifierDescription(EffectScope scope, int amount, String plNoun, String enNoun) {
		String amountText = (amount > 0 ? "+" : "−") + Math.abs(amount);
		LocalizedText subject = subject(scope);
		return text("%s: %s %s".formatted(subject.in(Language.PL), amountText, plNoun),
				"%s: %s %s".formatted(subject.in(Language.EN), amountText, enNoun));
	}

	private static LocalizedText keywordDescription(EffectScope scope, String plKeyword, String enKeyword) {
		LocalizedText subject = subject(scope);
		return text("%s: %s".formatted(subject.in(Language.PL), plKeyword),
				"%s: %s".formatted(subject.in(Language.EN), enKeyword));
	}

	private static LocalizedText subject(EffectScope scope) {
		return switch (scope) {
			case TARGET -> text("Wybrana postać", "Chosen character");
			case OWN_BOARD -> text("Własne postacie", "Your characters");
			case ENEMY_BOARD -> text("Postacie przeciwnika", "Enemy characters");
			case ALL_BOARDS -> text("Wszystkie postacie", "All characters");
			default -> text("Postacie", "Characters");
		};
	}

}
