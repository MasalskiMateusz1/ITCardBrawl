package com.example.itcardbrawl.game;

/**
 * Ruch niezgodny z zasadami partii; komunikat po polsku opisuje powód odrzucenia.
 */
public class IllegalMoveException extends RuntimeException {

	public IllegalMoveException(String reason) {
		super(reason);
	}

}
