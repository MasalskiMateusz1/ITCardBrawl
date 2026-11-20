package com.example.itcardbrawl.game;

/** Dobiera polską formę rzeczownika po liczbie całkowitej. */
final class PolishNumberForms {

	private PolishNumberForms() {
	}

	static String form(int value, String singular, String plural, String genitivePlural) {
		int absolute = Math.abs(value);
		int lastTwo = absolute % 100;
		if (absolute == 1) {
			return singular;
		}
		int last = absolute % 10;
		return last >= 2 && last <= 4 && (lastTwo < 12 || lastTwo > 14) ? plural : genitivePlural;
	}

}
