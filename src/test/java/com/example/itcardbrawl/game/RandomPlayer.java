package com.example.itcardbrawl.game;

import java.util.ArrayList;
import java.util.List;
import java.util.random.RandomGenerator;

import com.example.itcardbrawl.game.Match.Side;
import com.example.itcardbrawl.game.Match.Status;

/**
 * Punkt odniesienia w testach: gracz (strona {@link Side#PLAYER}) grający losowo. Wybiera jednostajnie akcję
 * (kartę z ręki albo atakującą postać), potem jeden z jej wariantów (cel), dopóki są poprawne ruchy.
 */
final class RandomPlayer {

	private static final Side SIDE = Side.PLAYER;

	private final MatchEngine engine;

	private final RandomGenerator random;

	RandomPlayer(MatchEngine engine, RandomGenerator random) {
		this.engine = engine;
		this.random = random;
	}

	/** Rozgrywa turę gracza; poza turą gracza zwraca partię bez zmian. */
	Match playTurn(Match match) {
		Match current = match;
		int moves = 0;
		while (current.status() == Status.IN_PROGRESS && current.activeSide() == SIDE) {
			List<Move> legal = engine.legalMoves(current, SIDE);
			Move move = legal.isEmpty() || moves >= ComputerOpponent.MAX_MOVES_PER_TURN ? new Move.EndTurn()
					: chooseMove(legal);
			current = engine.apply(current, SIDE, move);
			moves++;
		}
		return current;
	}

	private Move chooseMove(List<Move> legal) {
		List<List<Move>> actions = new ArrayList<>();
		for (Move move : legal) {
			List<Move> action = actions.stream().filter(group -> sameAction(group.getFirst(), move))
				.findFirst().orElse(null);
			if (action == null) {
				action = new ArrayList<>();
				actions.add(action);
			}
			action.add(move);
		}
		List<Move> selected = actions.get(random.nextInt(actions.size()));
		return selected.get(random.nextInt(selected.size()));
	}

	private static boolean sameAction(Move existing, Move candidate) {
		return switch (candidate) {
			case Move.PlayCard play -> existing instanceof Move.PlayCard other && other.instanceId() == play.instanceId();
			case Move.Attack attack -> existing instanceof Move.Attack other && other.attackerId() == attack.attackerId();
			case Move.EndTurn ignored -> existing instanceof Move.EndTurn;
		};
	}

}
