package com.example.itcardbrawl.game;

/**
 * Strukturalny, niezależny od języka wpis logu partii. {@code side} to strona, której ruch lub tura
 * wywołały zdarzenie (dla {@link CharacterDefeated} — właściciel pokonanej postaci). Zdania dla gracza
 * składa dopiero widok.
 */
public sealed interface MatchEvent {

	Match.Side side();

	record TurnStarted(Match.Side side, int turn) implements MatchEvent {
	}

	record CardDrawn(Match.Side side, CardInstance card) implements MatchEvent {
	}

	/** Karta dobrana przy pełnej ręce trafia od razu na cmentarz. */
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
	 * Atak postaci; {@code target == null} oznacza atak w stres przeciwnika. {@code damageDealt} to obrażenia
	 * (albo stres) zadane przez atakującego, {@code damageTaken} — obrażenia otrzymane w odwecie.
	 */
	record Attacked(Match.Side side, CardInstance attacker, CardInstance target, int damageDealt, int damageTaken)
			implements MatchEvent {
	}

	record StressChanged(Match.Side side, Match.Side affected, int from, int to) implements MatchEvent {
	}

	record CharacterDefeated(Match.Side side, CardInstance card) implements MatchEvent {
	}

	record EventExpired(Match.Side side, CardInstance card) implements MatchEvent {
	}

	record TurnEnded(Match.Side side) implements MatchEvent {
	}

	record MatchEnded(Match.Side side, MatchResult result) implements MatchEvent {
	}

}
