package com.example.itcardbrawl.game;

/**
 * A move sent for a different match or a different turn than the current one (e.g. a double click on "End turn").
 * The match state stays unchanged.
 */
public class StaleMoveException extends RuntimeException {

	public StaleMoveException(String reason) {
		super(reason);
	}

}
