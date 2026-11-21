package com.example.itcardbrawl.game;

import java.util.List;
import java.util.Map;

/**
 * Raw contents of the catalog file ({@code game/factions.json}) before validation.
 */
record CatalogData(List<Faction> factions, List<Card> cards, Map<String, List<DeckEntry>> decks) {
}
