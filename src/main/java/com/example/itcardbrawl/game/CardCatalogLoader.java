package com.example.itcardbrawl.game;

import java.io.IOException;
import java.io.InputStream;

import org.springframework.core.io.Resource;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * Wczytuje i waliduje katalog kart z pliku JSON. Każdy błąd (brak pliku, zły JSON, niepoprawne dane)
 * kończy się {@link IllegalStateException} z czytelnym komunikatem.
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
