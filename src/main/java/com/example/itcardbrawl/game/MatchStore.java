package com.example.itcardbrawl.game;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

/**
 * Bieżąca partia każdego gracza w pamięci procesu (jedna na gracza, do restartu serwera).
 * Klucz gracza: {@code "<registrationId>:<principalName>"}.
 */
@Component
public class MatchStore {

	private final Map<String, Match> matches = new ConcurrentHashMap<>();

	/** Zapisuje partię gracza, zastępując poprzednią. */
	public void save(String playerKey, Match match) {
		matches.put(playerKey, match);
	}

	public Optional<Match> findCurrent(String playerKey) {
		return Optional.ofNullable(matches.get(playerKey));
	}

}
