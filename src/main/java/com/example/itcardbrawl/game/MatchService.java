package com.example.itcardbrawl.game;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import com.example.itcardbrawl.game.Match.Side;
import com.example.itcardbrawl.game.Match.Status;
import com.example.itcardbrawl.game.MatchStore.Entry;

import org.springframework.stereotype.Component;

/**
 * Orchestration of a player's match: start, player moves, the computer turn and the player's turn time limit. Every
 * move, together with any resulting computer turn, runs in a single atomic update {@link MatchStore#update}.
 *
 * <p>The player's turn deadline is set when their turn starts: now + {@link MatchRules#TURN_LIMIT} + the replay steps
 * of the computer turn shown first × {@link MatchRules#REPLAY_STEP} + {@link MatchRules#TURN_GRACE}. It is checked
 * lazily: every {@link #move} and {@link #current} first ends an expired player turn (with the computer turn), in the
 * same atomic update.
 */
@Component
public class MatchService {

	private final MatchFactory factory;

	private final MatchEngine engine;

	private final ComputerOpponent opponent;

	private final MatchStore store;

	private final Clock clock;

	public MatchService(MatchFactory factory, MatchEngine engine, ComputerOpponent opponent, MatchStore store,
			Clock clock) {
		this.factory = factory;
		this.engine = engine;
		this.opponent = opponent;
		this.store = store;
		this.clock = clock;
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
		Instant deadline = deadline(match, replay.size(), clock.instant());
		store.save(playerKey, new Entry(match, deadline));
		return new MatchOutcome(match, replay, deadline);
	}

	/**
	 * Applies a player move; after an end of turn in a match in progress it also plays the computer turn.
	 * {@code replay} is then the state after the player's end of turn and the state after each computer move; for
	 * any other move it is empty. When the player's turn deadline has passed, the turn is ended automatically first
	 * (and stored) and the move is rejected as stale, because the turn number has changed.
	 *
	 * @return the new match state, or {@link Optional#empty()} when the player has no current match
	 * @throws StaleMoveException when {@code matchId} or {@code turn} does not match the current match, or the turn
	 * has just ended automatically
	 * @throws IllegalMoveException when the move breaks the rules
	 */
	public Optional<MatchOutcome> move(String playerKey, UUID matchId, int turn, Move move) {
		// The steps are produced in the same atomic update, but the store accepts only the final state —
		// so we carry them out of the lambda through local holders. The store may invoke the lambda again (write
		// conflict), so every attempt starts with empty steps.
		AtomicReference<List<Match>> replay = new AtomicReference<>(List.of());
		AtomicBoolean timedOut = new AtomicBoolean();
		Optional<Entry> updated = store.update(playerKey, current -> {
			replay.set(List.of());
			timedOut.set(false);
			Instant now = clock.instant();
			if (expired(current, now)) {
				// The expired turn is ended and stored; the move itself is stale (thrown after the update).
				timedOut.set(true);
				return endPlayerTurn(current.match(), now, replay);
			}
			Match match = current.match();
			if (!match.id().equals(matchId)) {
				throw new StaleMoveException("Ruch dotyczy innej partii niż bieżąca");
			}
			if (match.turnNumber() != turn) {
				throw new StaleMoveException("Ruch dotyczy innej tury niż bieżąca");
			}
			if (move instanceof Move.EndTurn) {
				return endPlayerTurn(match, now, replay);
			}
			Match after = engine.apply(match, Side.PLAYER, move);
			return playerTurn(after) ? current.withMatch(after) : new Entry(after, null);
		});
		if (timedOut.get()) {
			throw new StaleMoveException("Tura zakończyła się automatycznie po upływie limitu czasu");
		}
		return updated.map(entry -> outcome(entry, replay.get()));
	}

	/**
	 * The player's current match. When the player's turn deadline has passed, the turn is ended automatically first;
	 * {@code replay} is then the computer turn as after a manual end of turn, otherwise it is empty.
	 */
	public Optional<MatchOutcome> current(String playerKey) {
		AtomicReference<List<Match>> replay = new AtomicReference<>(List.of());
		return store.update(playerKey, current -> {
			replay.set(List.of());
			Instant now = clock.instant();
			return expired(current, now) ? endPlayerTurn(current.match(), now, replay) : current;
		}).map(entry -> outcome(entry, replay.get()));
	}

	// Ends the player's turn and plays the computer turn; the steps go to replay, the new deadline into the entry.
	private Entry endPlayerTurn(Match match, Instant now, AtomicReference<List<Match>> replay) {
		Match after = engine.apply(match, Side.PLAYER, new Move.EndTurn());
		if (after.status() == Status.IN_PROGRESS) {
			List<Match> steps = opponent.playTurnSteps(after);
			replay.set(withFirst(after, steps));
			after = steps.isEmpty() ? after : steps.getLast();
		}
		return new Entry(after, deadline(after, replay.get().size(), now));
	}

	private static boolean expired(Entry entry, Instant now) {
		return playerTurn(entry.match()) && entry.playerTurnDeadline() != null
				&& now.isAfter(entry.playerTurnDeadline());
	}

	private static boolean playerTurn(Match match) {
		return match.status() == Status.IN_PROGRESS && match.activeSide() == Side.PLAYER;
	}

	// The SPA starts counting the turn limit after the replay, so the server allows for it and for a grace period.
	private static Instant deadline(Match match, int replaySteps, Instant now) {
		if (!playerTurn(match)) {
			return null;
		}
		return now.plus(MatchRules.TURN_LIMIT)
			.plus(MatchRules.REPLAY_STEP.multipliedBy(replaySteps))
			.plus(MatchRules.TURN_GRACE);
	}

	private static MatchOutcome outcome(Entry entry, List<Match> replay) {
		return new MatchOutcome(entry.match(), replay, entry.playerTurnDeadline());
	}

	private static List<Match> withFirst(Match first, List<Match> rest) {
		List<Match> all = new ArrayList<>(rest.size() + 1);
		all.add(first);
		all.addAll(rest);
		return all;
	}

}
