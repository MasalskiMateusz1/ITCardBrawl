package com.example.itcardbrawl.game;

/**
 * Wynik zakończonej partii: zwycięzca i przyczyna przegranej drugiej strony.
 */
public record MatchResult(Match.Side winner, EndReason reason) {

	public enum EndReason {
		/** Stres przegranego osiągnął {@link MatchRules#STRESS_LIMIT}. */
		STRESS_LIMIT,
		/** Przegrany musiał dobrać kartę z pustej talii. */
		EMPTY_DECK
	}

}
