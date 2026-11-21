package com.example.itcardbrawl.game;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A character's ability: a keyword and its strength. Keywords without a number ({@code GUARD}, {@code RUSH},
 * {@code OVERTIME}) have {@code amount = 0}; the field can then be omitted in JSON.
 */
public record Ability(AbilityKeyword keyword, int amount) {

	// Jackson 3 rejects a missing value for a primitive field by default, so an omitted amount is mapped to 0.
	@JsonCreator
	static Ability fromJson(@JsonProperty("keyword") AbilityKeyword keyword, @JsonProperty("amount") Integer amount) {
		return new Ability(keyword, amount != null ? amount : 0);
	}

}
