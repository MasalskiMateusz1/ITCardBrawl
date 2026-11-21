package com.example.itcardbrawl.game;

import java.util.ArrayList;
import java.util.List;
import java.util.random.RandomGenerator;

import com.example.itcardbrawl.game.Match.Side;
import com.example.itcardbrawl.game.Match.Status;

import org.springframework.stereotype.Component;

/**
 * Komputer grający przez {@link MatchEngine} z oceną 1 ruchu wprzód: każdy poprawny ruch jest symulowany
 * i oceniany przez {@link PositionEvaluator}. Ruch wygrywający partię jest brany od razu; ruchy, które nie
 * poprawiają oceny, odpadają; spośród pozostałych losuje z wagą {@code exp((score - best) / TEMPERATURE)}.
 * Gdy nic nie zostaje, kończy turę.
 */
@Component
public class ComputerOpponent {

	/** Bezpiecznik: po tylu ruchach w jednej turze komputer kończy ją bez względu na dostępne ruchy. */
	static final int MAX_MOVES_PER_TURN = 100;

	/** Temperatura losowania: im mniejsza, tym częściej wybierany jest ruch o najlepszej ocenie. */
	static final double TEMPERATURE = 1.0;

	/** Minimalna poprawa oceny, by ruch nie był uznany za bezcelowy (chroni przed szumem zmiennoprzecinkowym). */
	private static final double MIN_GAIN = 1e-9;

	private static final Side SIDE = Side.OPPONENT;

	private final MatchEngine engine;

	private final PositionEvaluator evaluator;

	private final RandomGenerator random;

	public ComputerOpponent(MatchEngine engine, PositionEvaluator evaluator, RandomGenerator random) {
		this.engine = engine;
		this.evaluator = evaluator;
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
		while (current.status() == Status.IN_PROGRESS && current.activeSide() == SIDE) {
			Move move = moves >= MAX_MOVES_PER_TURN ? new Move.EndTurn() : chooseMove(current);
			current = engine.apply(current, SIDE, move);
			steps.add(current);
			moves++;
		}
		return List.copyOf(steps);
	}

	/** Wybiera następny ruch komputera w bieżącym stanie; {@link Move.EndTurn}, gdy żaden ruch nie pomaga. */
	Move chooseMove(Match current) {
		List<Move> legal = engine.legalMoves(current, SIDE);
		if (legal.isEmpty()) {
			return new Move.EndTurn();
		}
		double baseline = evaluator.score(current, SIDE).total();
		List<Move> candidates = new ArrayList<>();
		List<Double> scores = new ArrayList<>();
		double best = Double.NEGATIVE_INFINITY;
		for (Move move : legal) {
			Match after = engine.apply(current, SIDE, move);
			if (after.status() == Status.FINISHED && after.result() != null && after.result().winner() == SIDE) {
				return move;
			}
			double score = evaluator.score(after, SIDE).total();
			if (score > baseline + MIN_GAIN) {
				candidates.add(move);
				scores.add(score);
				best = Math.max(best, score);
			}
		}
		if (candidates.isEmpty()) {
			return new Move.EndTurn();
		}
		double[] weights = new double[candidates.size()];
		double sum = 0;
		for (int i = 0; i < weights.length; i++) {
			weights[i] = Math.exp((scores.get(i) - best) / TEMPERATURE);
			sum += weights[i];
		}
		double roll = random.nextDouble() * sum;
		for (int i = 0; i < weights.length; i++) {
			roll -= weights[i];
			if (roll < 0) {
				return candidates.get(i);
			}
		}
		return candidates.getLast();
	}

}
