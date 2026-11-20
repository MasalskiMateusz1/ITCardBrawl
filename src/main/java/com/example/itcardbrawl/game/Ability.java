package com.example.itcardbrawl.game;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Zdolność postaci: słowo kluczowe i jego siła. Słowa bez liczby ({@code GUARD}, {@code RUSH},
 * {@code OVERTIME}) mają {@code amount = 0}; w JSON pole można wtedy pominąć.
 */
public record Ability(AbilityKeyword keyword, int amount) {

	// Jackson 3 domyślnie odrzuca brak wartości dla pola prymitywnego, więc pominięte amount mapujemy na 0.
	@JsonCreator
	static Ability fromJson(@JsonProperty("keyword") AbilityKeyword keyword, @JsonProperty("amount") Integer amount) {
		return new Ability(keyword, amount != null ? amount : 0);
	}

}
