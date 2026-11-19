package com.example.itcardbrawl.game;

import java.util.Locale;

/**
 * Obsługiwane języki treści gry. Wszystko spoza angielskiego (także brak języka) daje polski.
 */
public enum Language {

	PL(Locale.forLanguageTag("pl")),

	EN(Locale.ENGLISH);

	private final Locale locale;

	Language(Locale locale) {
		this.locale = locale;
	}

	public Locale locale() {
		return locale;
	}

	public static Language from(Locale locale) {
		return locale != null && "en".equals(locale.getLanguage()) ? EN : PL;
	}

}
