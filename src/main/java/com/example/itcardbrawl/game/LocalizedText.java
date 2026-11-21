package com.example.itcardbrawl.game;

/**
 * Text in every supported language; stored in the JSON catalog as {@code { "pl": "…", "en": "…" }}.
 */
public record LocalizedText(String pl, String en) {

	public String in(Language language) {
		return switch (language) {
			case PL -> pl;
			case EN -> en;
		};
	}

}
