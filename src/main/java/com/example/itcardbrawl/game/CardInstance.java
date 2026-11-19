package com.example.itcardbrawl.game;

/**
 * Konkretny egzemplarz karty w partii; {@code instanceId} jest unikalny w obrębie jednej partii.
 */
public record CardInstance(int instanceId, Card card) {
}
