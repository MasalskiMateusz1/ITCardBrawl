package com.example.itcardbrawl.game;

/**
 * Karta z katalogu; {@code faction} to id frakcji, do której należy.
 */
public record Card(String id, String faction, LocalizedText name, CardType type, int cost, LocalizedText text) {
}
