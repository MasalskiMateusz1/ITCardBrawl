package com.example.itcardbrawl.game;

import java.util.List;
import java.util.Map;

/**
 * Raw contents of the catalog file ({@code game/factions.json}) before validation. {@code secondPlayerBonus} is
 * optional: the id of a neutral card dealt to the non-starting side on top of its starting hand.
 */
record CatalogData(List<Faction> factions, List<Card> cards, Map<String, List<DeckEntry>> decks,
		String secondPlayerBonus) {
}
