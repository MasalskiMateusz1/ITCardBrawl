package com.example.itcardbrawl.game;

import java.util.ArrayList;
import java.util.List;
import java.util.random.RandomGenerator;

import com.example.itcardbrawl.game.Match.Side;
import com.example.itcardbrawl.game.Match.Status;

/**
 * A baseline in tests: a player (side {@link Side#PLAYER}) that plays randomly. It uniformly picks an action
 * (a card from hand or an attacking character), then one of its variants (target), while there are legal moves.
 */
final class RandomPlayer {

	private static final Side SIDE = Side.PLAYER;

	private final MatchEngine engine;

	private final RandomGenerator random;

	RandomPlayer(MatchEngine engine, RandomGenerator random) {
		this.engine = engine;
		this.random = random;
	}

	/** Plays the player's turn; outside the player's turn it returns the match unchanged. */
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
