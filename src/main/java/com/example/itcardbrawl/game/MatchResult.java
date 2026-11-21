package com.example.itcardbrawl.game;

/**
 * Wynik zakończonej partii: zwycięzca i przyczyna przegranej drugiej strony.
 */
public record MatchResult(Match.Side winner, EndReason reason) {

	public enum EndReason {
		/** HP przegranego spadło do 0 lub niżej. */
		HP_DEPLETED,
		/** Przegrany musiał dobrać kartę z pustej talii. */
		EMPTY_DECK
	}

}
