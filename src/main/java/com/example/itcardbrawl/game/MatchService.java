package com.example.itcardbrawl.game;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import com.example.itcardbrawl.game.Match.Side;
import com.example.itcardbrawl.game.Match.Status;

import org.springframework.stereotype.Component;

/**
 * Orchestration of a player's match: start, player moves and the computer turn. Every move, together with any
 * resulting computer turn, runs in a single atomic update {@link MatchStore#update}.
 */
@Component
public class MatchService {

	private final MatchFactory factory;

	private final MatchEngine engine;

	private final ComputerOpponent opponent;

	private final MatchStore store;

	public MatchService(MatchFactory factory, MatchEngine engine, ComputerOpponent opponent, MatchStore store) {
		this.factory = factory;
		this.engine = engine;
		this.opponent = opponent;
		this.store = store;
	}

	/**
	 * Creates a new player match, replacing the previous one. When the computer starts, its turn is played right away;
	 * {@code replay} is then the starting state and the state after each computer move, otherwise it is empty.
	 *
	 * @throws IllegalArgumentException when the faction does not exist or is not playable
	 */
	public MatchOutcome start(String playerKey, String factionId) {
		Match match = factory.create(factionId);
		List<Match> replay = List.of();
		if (match.activeSide() == Side.OPPONENT) {
			List<Match> steps = opponent.playTurnSteps(match);
			replay = withFirst(match, steps);
			match = steps.isEmpty() ? match : steps.getLast();
		}
		store.save(playerKey, match);
		return new MatchOutcome(match, replay);
	}

	/**
	 * Applies a player move; after an end of turn in a match in progress it also plays the computer turn.
	 * {@code replay} is then the state after the player's end of turn and the state after each computer move; for
	 * any other move it is empty.
	 *
	 * @return the new match state, or {@link Optional#empty()} when the player has no current match
	 * @throws StaleMoveException when {@code matchId} or {@code turn} does not match the current match
	 * @throws IllegalMoveException when the move breaks the rules
	 */
	public Optional<MatchOutcome> move(String playerKey, UUID matchId, int turn, Move move) {
		// The steps are produced in the same atomic update, but the store accepts only the final state —
		// so we carry them out of the lambda through a local holder. The store may invoke the lambda again (write
		// conflict), so every attempt starts with empty steps.
		AtomicReference<List<Match>> replay = new AtomicReference<>(List.of());
		return store.update(playerKey, current -> {
			replay.set(List.of());
			if (!current.id().equals(matchId)) {
				throw new StaleMoveException("Ruch dotyczy innej partii niż bieżąca");
			}
			if (current.turnNumber() != turn) {
				throw new StaleMoveException("Ruch dotyczy innej tury niż bieżąca");
			}
			Match after = engine.apply(current, Side.PLAYER, move);
			if (move instanceof Move.EndTurn && after.status() == Status.IN_PROGRESS) {
				List<Match> steps = opponent.playTurnSteps(after);
				replay.set(withFirst(after, steps));
				after = steps.isEmpty() ? after : steps.getLast();
			}
			return after;
		}).map(match -> new MatchOutcome(match, replay.get()));
	}

	public Optional<Match> current(String playerKey) {
		return store.findCurrent(playerKey);
	}

	private static List<Match> withFirst(Match first, List<Match> rest) {
		List<Match> all = new ArrayList<>(rest.size() + 1);
		all.add(first);
		all.addAll(rest);
		return all;
	}

}
