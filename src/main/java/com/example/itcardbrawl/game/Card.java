package com.example.itcardbrawl.game;

/**
 * Karta z katalogu; {@code faction} to id frakcji, do której należy.
 * <p>
 * Postać ma {@code attack}, {@code health} i opcjonalną {@code ability}; wydarzenie ma {@code duration}
 * (liczba tur działania, 0 = natychmiastowe). Pola nieużywane przez dany typ są {@code null}.
 */
public record Card(String id, String faction, LocalizedText name, CardType type, int cost, LocalizedText text,
		Integer attack, Integer health, Ability ability, Integer duration) {
}
