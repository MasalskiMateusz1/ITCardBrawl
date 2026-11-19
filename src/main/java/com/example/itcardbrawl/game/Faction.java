package com.example.itcardbrawl.game;

/**
 * Frakcja z katalogu. Frakcja niegrywalna (np. „Neutralne”) jest tylko pulą kart dokładaną do talii grywalnych.
 */
public record Faction(String id, LocalizedText name, LocalizedText description, boolean playable) {
}
