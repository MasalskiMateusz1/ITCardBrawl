package com.example.itcardbrawl.game;

/**
 * A concrete card instance in a match; {@code instanceId} is unique within a single match.
 */
public record CardInstance(int instanceId, Card card) {
}
