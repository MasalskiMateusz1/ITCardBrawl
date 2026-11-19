package com.example.itcardbrawl.game;

/**
 * Frakcja z katalogu. Frakcja niegrywalna (np. „Neutralne”) jest tylko pulą kart dokładaną do talii grywalnych.
 */
public record Faction(String id, String name, String description, boolean playable) {
}
