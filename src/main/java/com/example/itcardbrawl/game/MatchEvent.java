package com.example.itcardbrawl.game;

/**
 * A structured, language-independent match log entry. {@code side} is the side whose move or turn
 * caused the event (for {@link CharacterDefeated} — the owner of the defeated character). Player-facing
 * sentences are composed only by the view.
 */
public sealed interface MatchEvent {

	Match.Side side();

	record TurnStarted(Match.Side side, int turn) implements MatchEvent {
	}

	record CardDrawn(Match.Side side, CardInstance card) implements MatchEvent {
	}

	/** A card drawn with a full hand goes straight to the graveyard. */
	record CardBurned(Match.Side side, CardInstance card) implements MatchEvent {
	}

	record CardPlayed(Match.Side side, CardInstance card) implements MatchEvent {
	}

	record EffectApplied(Match.Side side, CardInstance card, Effect effect, CardInstance targetCharacter,
			Match.Side targetPlayer) implements MatchEvent {
	}

	record AbilityTriggered(Match.Side side, CardInstance card, AbilityKeyword keyword, int amount)
			implements MatchEvent {
	}

	/**
	 * A character attack; {@code target == null} means an attack on the player (the opponent's HP). {@code damageDealt}
	 * is the damage dealt by the attacker, {@code damageTaken} — the damage taken in retaliation.
	 */
	record Attacked(Match.Side side, CardInstance attacker, CardInstance target, int damageDealt, int damageTaken)
			implements MatchEvent {
	}

	record HpChanged(Match.Side side, Match.Side affected, int from, int to) implements MatchEvent {
	}

	record CharacterDefeated(Match.Side side, CardInstance card) implements MatchEvent {
	}

	record EventExpired(Match.Side side, CardInstance card) implements MatchEvent {
	}

	record TurnEnded(Match.Side side) implements MatchEvent {
	}

	record MatchEnded(Match.Side side, MatchResult result) implements MatchEvent {
	}

	/**
	 * The reason for a computer move, written to the log right before that move's events. {@code card} is the card
	 * being played or the attacking character ({@code null} for end of turn), {@code target} — the target character
	 * ({@code null} when the target is the player or there is no target), {@code amount} — the number for the
	 * sentence (e.g. HP damage) or 0. The reason refers only to facts that are public to the player.
	 */
	record MoveExplained(Match.Side side, MoveReason reason, CardInstance card, CardInstance target, int amount)
			implements MatchEvent {
	}

}
