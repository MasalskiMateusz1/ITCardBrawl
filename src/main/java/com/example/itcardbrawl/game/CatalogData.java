package com.example.itcardbrawl.game;

import java.util.List;
import java.util.Map;

/**
 * Surowa zawartość pliku katalogu ({@code game/factions.json}) przed walidacją.
 */
record CatalogData(List<Faction> factions, List<Card> cards, Map<String, List<DeckEntry>> decks) {
}
