package com.example.itcardbrawl.game;

import java.util.ArrayList;
import java.util.List;
import java.util.random.RandomGenerator;

import com.example.itcardbrawl.game.Match.Side;
import com.example.itcardbrawl.game.Match.Status;

import org.springframework.stereotype.Component;

/**
 * Komputer grający losowo przez {@link MatchEngine}: wykonuje jednostajnie wylosowane poprawne ruchy, dopóki
 * jakieś są, a potem kończy turę. Sensowna strategia to osobny etap (S-05).
 */
@Component
public class RandomOpponent {

	/** Bezpiecznik: po tylu ruchach w jednej turze komputer kończy ją bez względu na dostępne ruchy. */
	static final int MAX_MOVES_PER_TURN = 100;

	private final MatchEngine engine;

	private final RandomGenerator random;

	public RandomOpponent(MatchEngine engine, RandomGenerator random) {
		this.engine = engine;
		this.random = random;
	}

	/**
	 * Rozgrywa turę komputera. Zwraca partię z aktywnym graczem albo zakończoną; gdy nie jest tura komputera,
	 * zwraca partię bez zmian.
	 */
	public Match playTurn(Match match) {
		List<Match> steps = playTurnSteps(match);
		return steps.isEmpty() ? match : steps.getLast();
	}

	/**
	 * Rozgrywa turę komputera i zwraca stan po każdym jego ruchu (zagranie, atak, koniec tury); ostatni element
	 * to stan końcowy. Gdy nie jest tura komputera, zwraca pustą listę.
	 */
	public List<Match> playTurnSteps(Match match) {
		List<Match> steps = new ArrayList<>();
		Match current = match;
		int moves = 0;
		while (current.status() == Status.IN_PROGRESS && current.activeSide() == Side.OPPONENT) {
			List<Move> legal = engine.legalMoves(current, Side.OPPONENT);
			Move move = legal.isEmpty() || moves >= MAX_MOVES_PER_TURN ? new Move.EndTurn()
					: legal.get(random.nextInt(legal.size()));
			current = engine.apply(current, Side.OPPONENT, move);
			steps.add(current);
			moves++;
		}
		return List.copyOf(steps);
	}

}
