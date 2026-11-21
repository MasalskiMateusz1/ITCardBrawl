package com.example.itcardbrawl.game;

import java.util.List;

/**
 * A catalog card; {@code faction} is the id of the faction it belongs to.
 * <p>
 * A character has {@code attack}, {@code health} and an optional {@code ability}; an event has {@code duration}
 * (the number of turns it stays active, 0 = immediate). Fields not used by a given type are {@code null}.
 */
public record Card(String id, String faction, LocalizedText name, CardType type, int cost, LocalizedText text,
		Integer attack, Integer health, Ability ability, Integer duration, List<Effect> effects) {
}
