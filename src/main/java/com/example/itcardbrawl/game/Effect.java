package com.example.itcardbrawl.game;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/** An event effect defined by its kind, scope and an optional value. */
public record Effect(EffectKind kind, EffectScope scope, int amount) {

	// An omitted amount means zero, as for abilities without a numeric value.
	@JsonCreator
	static Effect fromJson(@JsonProperty("kind") EffectKind kind, @JsonProperty("scope") EffectScope scope,
			@JsonProperty("amount") Integer amount) {
		return new Effect(kind, scope, amount != null ? amount : 0);
	}

	public LocalizedText describe() {
		return kind.description(scope, amount);
	}

}
