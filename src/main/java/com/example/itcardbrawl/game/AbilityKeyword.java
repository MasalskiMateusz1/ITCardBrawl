package com.example.itcardbrawl.game;

/**
 * Słowa kluczowe zdolności postaci. Tekst dla gracza generuje {@link #describe(int)}, więc katalog nie
 * potrzebuje osobnych pól tekstowych dla zdolności.
 */
public enum AbilityKeyword {

	/** Po zagraniu: zadaj przeciwnikowi N obrażeń. */
	ON_PLAY_DAMAGE(true),

	/** Po zagraniu: leczenie N własnego HP (do {@link MatchRules#MAX_HP}). */
	HEAL(true),

	/** Musi zostać zaatakowana pierwsza. */
	GUARD(false),

	/** Atakuje w turze zagrania. */
	RUSH(false),

	/** Na koniec własnej tury zadaj przeciwnikowi 1 obrażenie. */
	OVERTIME(false);

	static final int MIN_AMOUNT = 1;

	static final int MAX_AMOUNT = 10;

	private final boolean takesAmount;

	AbilityKeyword(boolean takesAmount) {
		this.takesAmount = takesAmount;
	}

	/** Czy słowo kluczowe wymaga liczby {@code amount} z zakresu {@link #MIN_AMOUNT}–{@link #MAX_AMOUNT}. */
	public boolean takesAmount() {
		return takesAmount;
	}

	/** Czy {@code amount} jest poprawne dla tego słowa kluczowego. */
	public boolean acceptsAmount(int amount) {
		return takesAmount ? amount >= MIN_AMOUNT && amount <= MAX_AMOUNT : amount == 0;
	}

	public LocalizedText describe(int amount) {
		return switch (this) {
			case ON_PLAY_DAMAGE -> new LocalizedText("Po zagraniu: zadaj przeciwnikowi %d %s".formatted(amount,
					PolishNumberForms.damage(amount)),
					"On play: deal %d damage to the opponent".formatted(amount));
			case HEAL -> new LocalizedText("Po zagraniu: leczenie %d HP".formatted(amount),
					"On play: heal %d HP".formatted(amount));
			case GUARD -> new LocalizedText("Ochrona: musi zostać zaatakowana pierwsza", "Guard: must be attacked first");
			case RUSH -> new LocalizedText("Szarża: atakuje od razu", "Rush: attacks immediately");
			case OVERTIME -> new LocalizedText("Nadgodziny: na koniec tury zadaj przeciwnikowi 1 obrażenie",
					"Overtime: at end of turn deal 1 damage to the opponent");
		};
	}

}
