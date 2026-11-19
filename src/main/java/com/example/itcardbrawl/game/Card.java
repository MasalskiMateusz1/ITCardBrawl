package com.example.itcardbrawl.game;

/**
 * Karta z katalogu; {@code faction} to id frakcji, do której należy.
 */
public record Card(String id, String faction, String name, CardType type, int cost, String text) {
}
