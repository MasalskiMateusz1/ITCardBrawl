package com.example.itcardbrawl.game;

/**
 * A move that breaks the match rules; the Polish message describes the reason for rejection.
 */
public class IllegalMoveException extends RuntimeException {

	public IllegalMoveException(String reason) {
		super(reason);
	}

}
