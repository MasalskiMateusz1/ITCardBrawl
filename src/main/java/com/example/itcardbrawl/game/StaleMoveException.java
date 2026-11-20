package com.example.itcardbrawl.game;

/**
 * Ruch wysłany dla innej partii albo innej tury niż bieżąca (np. podwójne kliknięcie „Zakończ turę”).
 * Stan partii pozostaje bez zmian.
 */
public class StaleMoveException extends RuntimeException {

	public StaleMoveException(String reason) {
		super(reason);
	}

}
