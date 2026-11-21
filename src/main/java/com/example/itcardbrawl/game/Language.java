package com.example.itcardbrawl.game;

import java.util.Locale;

/**
 * Supported game content languages. Anything other than English (including no language) yields Polish.
 * <p>
 * Deliberate asymmetry with the frontend: the SPA picks English for browsers outside {@code pl*}, but always sends
 * {@code Accept-Language: pl|en} (interceptor), so the Polish default only applies to clients without that header.
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
