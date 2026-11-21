package com.example.itcardbrawl.game;

/**
 * A catalog faction. A non-playable faction (e.g. "Neutral") is only a pool of cards added to the playable decks.
 */
public record Faction(String id, LocalizedText name, LocalizedText description, boolean playable) {
}
