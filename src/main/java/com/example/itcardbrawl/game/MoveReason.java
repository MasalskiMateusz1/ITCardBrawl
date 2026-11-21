package com.example.itcardbrawl.game;

/**
 * The kind of reason for a computer move ({@link MatchEvent.MoveExplained}).
 * Player-facing sentences are composed by the view.
 */
public enum MoveReason {

	/** The move wins the match. */
	LETHAL,

	/** The move defeats a player's character. */
	REMOVE_THREAT,

	/** An attack on a character that weakens the player's board more than its own. */
	FAVORABLE_TRADE,

	/** An attack on a character with Guard, which blocks other targets. */
	OPEN_GUARD,

	/** Damage to the player's HP. */
	PRESSURE,

	/** Playing a character. */
	DEVELOP_BOARD,

	/** Strengthening own characters. */
	STRENGTHEN,

	/** Weakening or trapping a player's character. */
	WEAKEN,

	/** Healing own HP, or Guard when own HP is low. */
	DEFEND,

	/** Drawing cards. */
	DRAW_CARDS,

	/** Increasing the budget. */
	GAIN_BUDGET,

	/** End of turn: no move improves the position. */
	END_NO_GAIN,

	/** End of turn: no legal moves. */
	END_NO_MOVES

}
