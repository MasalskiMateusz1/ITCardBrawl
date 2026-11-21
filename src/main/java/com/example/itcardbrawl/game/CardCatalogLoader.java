package com.example.itcardbrawl.game;

import java.io.IOException;
import java.io.InputStream;

import org.springframework.core.io.Resource;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * Loads and validates the card catalog from a JSON file. Every error (missing file, malformed JSON, invalid data)
 * ends in an {@link IllegalStateException} with a readable message.
 */
public final class CardCatalogLoader {

	public static final String CATALOG_LOCATION = "game/factions.json";

	private CardCatalogLoader() {
	}

	public static CardCatalog load(ObjectMapper mapper, Resource resource) {
		CatalogData data;
		try (InputStream in = resource.getInputStream()) {
			data = mapper.readValue(in, CatalogData.class);
		}
		catch (IOException | JacksonException ex) {
			throw new IllegalStateException("Nie udało się wczytać katalogu kart z " + resource.getDescription()
					+ ": " + ex.getMessage(), ex);
		}
		if (data == null) {
			throw new IllegalStateException("Pusty katalog kart: " + resource.getDescription());
		}
		return new CardCatalog(data.factions(), data.cards(), data.decks());
	}

}
