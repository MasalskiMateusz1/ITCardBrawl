package com.example.itcardbrawl.game;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.UnaryOperator;

import org.springframework.stereotype.Component;

/**
 * Each player's current match in process memory (one per player, until the server restarts), together with the
 * deadline of the player's current turn. Player key: {@code "<registrationId>:<principalName>"}.
 */
@Component
public class MatchStore {

	/**
	 * A stored match and the deadline of the player's current turn. The deadline lives here, not in {@link Match}, so
	 * the engine and the simulation stay free of time.
	 *
	 * @param playerTurnDeadline the moment after which the player's turn ends automatically; {@code null} when it is
	 * not the player's turn (or the match is finished)
	 * @param pendingReplay the computer turn of a turn ended automatically by a rejected (stale) move, kept until the
	 * next read shows it once; empty when there is nothing to show
	 */
	public record Entry(Match match, Instant playerTurnDeadline, List<Match> pendingReplay) {

		public Entry {
			Objects.requireNonNull(match, "match");
			pendingReplay = List.copyOf(Objects.requireNonNull(pendingReplay, "pendingReplay"));
		}

		/** An entry with nothing pending to replay. */
		public Entry(Match match, Instant playerTurnDeadline) {
			this(match, playerTurnDeadline, List.of());
		}

		/** The same deadline with a different match state (e.g. a move within the same turn); nothing pending. */
		public Entry withMatch(Match next) {
			return new Entry(next, playerTurnDeadline);
		}

		/** The same state with the given computer turn kept for the next read. */
		public Entry withPendingReplay(List<Match> replay) {
			return new Entry(match, playerTurnDeadline, replay);
		}

	}

	private final Map<String, Entry> matches = new ConcurrentHashMap<>();

	/** Saves the player's match, replacing the previous one. */
	public void save(String playerKey, Entry entry) {
		matches.put(playerKey, Objects.requireNonNull(entry, "entry"));
	}

	public Optional<Entry> find(String playerKey) {
		return Optional.ofNullable(matches.get(playerKey));
	}

	public Optional<Match> findCurrent(String playerKey) {
		return find(playerKey).map(Entry::match);
	}

	/**
	 * Atomically replaces the player's current entry with the result of {@code change}. {@code change} is computed
	 * outside the map lock (a computer turn does not block other players); the write succeeds only if the entry has
	 * not changed in the meantime — otherwise {@code change} is invoked again on the new state. {@code change} must
	 * therefore have no lasting side effects beyond its result. An exception from {@code change} leaves the previous
	 * entry unchanged and propagates to the caller.
	 *
	 * @return the new entry, or {@link Optional#empty()} when the player has no current match
	 */
	public Optional<Entry> update(String playerKey, UnaryOperator<Entry> change) {
		while (true) {
			Entry current = matches.get(playerKey);
			if (current == null) {
				return Optional.empty();
			}
			Entry next = Objects.requireNonNull(change.apply(current), "Zmiana partii zwróciła null");
			if (matches.replace(playerKey, current, next)) {
				return Optional.of(next);
			}
		}
	}

}
