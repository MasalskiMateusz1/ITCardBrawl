package com.example.itcardbrawl.game;

import java.time.Duration;

/**
 * Match rule constants in one place.
 *
 * <p>Starting state = the start of the starting side's turn 1: both sides have {@link #STARTING_HAND} cards in hand,
 * the starting side has a budget of 1/1, the other side 0/0. The non-starting side also gets the catalog's second player
 * bonus card ({@link CardCatalog#secondPlayerBonus()}) on top of its hand. The budget grows by 1 per own turn up to
 * {@link #BUDGET_CAP}. Every turn starts by drawing {@link #DRAW_PER_TURN} card, except the starting side's first turn
 * (rule enforced since S-03).
 */
public final class MatchRules {

	public static final int DECK_SIZE = 30;

	public static final int STARTING_HAND = 5;

	/** A player's starting and maximum HP; healing does not raise HP above this value. */
	public static final int MAX_HP = 30;

	public static final int BUDGET_CAP = 10;

	public static final int DRAW_PER_TURN = 1;

	/** A card drawn with a full hand is burned (goes to the graveyard). */
	public static final int HAND_LIMIT = 10;

	/** Maximum number of characters on one side's board. */
	public static final int BOARD_LIMIT = 5;

	/** Time the player has for one turn; after it the server ends the turn on the next request. */
	public static final Duration TURN_LIMIT = Duration.ofSeconds(45);

	/** Extra server-side allowance on top of the turn limit for network and rendering delays. */
	public static final Duration TURN_GRACE = Duration.ofSeconds(3);

	/** Delay between replayed computer moves in the SPA (mirror of its {@code REPLAY_STEP_MS}). */
	public static final Duration REPLAY_STEP = Duration.ofMillis(700);

	private MatchRules() {
	}

}
