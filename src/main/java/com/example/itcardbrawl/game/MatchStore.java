package com.example.itcardbrawl.game;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.UnaryOperator;

import org.springframework.stereotype.Component;

/**
 * Each player's current match in process memory (one per player, until the server restarts).
 * Player key: {@code "<registrationId>:<principalName>"}.
 */
@Component
public class MatchStore {

	private final Map<String, Match> matches = new ConcurrentHashMap<>();

	/** Saves the player's match, replacing the previous one. */
	public void save(String playerKey, Match match) {
		matches.put(playerKey, match);
	}

	public Optional<Match> findCurrent(String playerKey) {
		return Optional.ofNullable(matches.get(playerKey));
	}

	/**
	 * Atomically replaces the player's current match with the result of {@code change}. {@code change} is computed
	 * outside the map lock (a computer turn does not block other players); the write succeeds only if the match has
	 * not changed in the meantime — otherwise {@code change} is invoked again on the new state. {@code change} must
	 * therefore have no lasting side effects beyond its result. An exception from {@code change} leaves the previous
	 * match unchanged and propagates to the caller.
	 *
	 * @return the new state, or {@link Optional#empty()} when the player has no current match
	 */
	public Optional<Match> update(String playerKey, UnaryOperator<Match> change) {
		while (true) {
			Match current = matches.get(playerKey);
			if (current == null) {
				return Optional.empty();
			}
			Match next = Objects.requireNonNull(change.apply(current), "Zmiana partii zwróciła null");
			if (matches.replace(playerKey, current, next)) {
				return Optional.of(next);
			}
		}
	}

}
