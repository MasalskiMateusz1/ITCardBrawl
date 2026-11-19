package com.example.itcardbrawl.game;

/**
 * Tekst w każdym obsługiwanym języku; w katalogu JSON zapisany jako {@code { "pl": "…", "en": "…" }}.
 */
public record LocalizedText(String pl, String en) {

	public String in(Language language) {
		return switch (language) {
			case PL -> pl;
			case EN -> en;
		};
	}

}
