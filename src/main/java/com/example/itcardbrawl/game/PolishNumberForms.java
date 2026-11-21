package com.example.itcardbrawl.game;

/** Picks the Polish form of a noun that follows an integer. */
public final class PolishNumberForms {

	private PolishNumberForms() {
	}

	public static String form(int value, String singular, String plural, String genitivePlural) {
		int absolute = Math.abs(value);
		int lastTwo = absolute % 100;
		if (absolute == 1) {
			return singular;
		}
		int last = absolute % 10;
		return last >= 2 && last <= 4 && (lastTwo < 12 || lastTwo > 14) ? plural : genitivePlural;
	}

	/** The form "obrażenie / obrażenia / obrażeń" (Polish for "damage") for the given number. */
	public static String damage(int value) {
		return form(value, "obrażenie", "obrażenia", "obrażeń");
	}

}
