package com.example.itcardbrawl.game;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Efekt wydarzenia określony rodzajem, zasięgiem i opcjonalną wartością. */
public record Effect(EffectKind kind, EffectScope scope, int amount) {

	// Pominięte amount oznacza zero, jak dla zdolności bez wartości liczbowej.
	@JsonCreator
	static Effect fromJson(@JsonProperty("kind") EffectKind kind, @JsonProperty("scope") EffectScope scope,
			@JsonProperty("amount") Integer amount) {
		return new Effect(kind, scope, amount != null ? amount : 0);
	}

	public LocalizedText describe() {
		String description = kind.description(scope, amount);
		int separator = description.indexOf(" / ");
		return new LocalizedText(description.substring(0, separator), description.substring(separator + 3));
	}

}
