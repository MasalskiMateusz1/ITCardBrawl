package com.example.itcardbrawl.game;

/**
 * Stałe zasad partii w jednym miejscu.
 *
 * <p>Stan startowy = początek tury 1 zaczynającego: obie strony mają {@link #STARTING_HAND} kart na ręce,
 * zaczynający ma zasób 1/1, druga strona 0/0. Zasób rośnie o 1 na własną turę do {@link #RESOURCE_CAP}.
 * Każda tura zaczyna się dobraniem {@link #DRAW_PER_TURN} karty, z wyjątkiem pierwszej tury zaczynającego
 * (reguła wykonywana od S-03).
 */
public final class MatchRules {

	public static final int DECK_SIZE = 30;

	public static final int STARTING_HAND = 5;

	public static final int STRESS_LIMIT = 30;

	public static final int RESOURCE_CAP = 10;

	public static final int DRAW_PER_TURN = 1;

	private MatchRules() {
	}

}
