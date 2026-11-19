package com.example.itcardbrawl.game;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;

import org.junit.jupiter.api.Test;

class LanguageTests {

	@Test
	void englishLocalesMapToEnglish() {
		assertThat(Language.from(Locale.forLanguageTag("en"))).isEqualTo(Language.EN);
		assertThat(Language.from(Locale.forLanguageTag("en-GB"))).isEqualTo(Language.EN);
	}

	@Test
	void everythingElseMapsToPolish() {
		assertThat(Language.from(Locale.forLanguageTag("pl"))).isEqualTo(Language.PL);
		assertThat(Language.from(Locale.forLanguageTag("de"))).isEqualTo(Language.PL);
		assertThat(Language.from(null)).isEqualTo(Language.PL);
	}

	@Test
	void localizedTextPicksValueForLanguage() {
		LocalizedText text = new LocalizedText("Tekst", "Text");

		assertThat(text.in(Language.PL)).isEqualTo("Tekst");
		assertThat(text.in(Language.EN)).isEqualTo("Text");
	}

}
