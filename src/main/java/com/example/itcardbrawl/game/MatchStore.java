package com.example.itcardbrawl.game;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.UnaryOperator;

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

	/**
	 * Atomowo zastępuje bieżącą partię gracza wynikiem {@code change}; równoległe aktualizacje tego samego
	 * gracza wykonują się po kolei. Wyjątek z {@code change} zostawia poprzednią partię bez zmian i trafia do
	 * wywołującego.
	 *
	 * @return nowy stan albo {@link Optional#empty()}, gdy gracz nie ma bieżącej partii
	 */
	public Optional<Match> update(String playerKey, UnaryOperator<Match> change) {
		return Optional.ofNullable(matches.computeIfPresent(playerKey,
				(key, match) -> Objects.requireNonNull(change.apply(match), "Zmiana partii zwróciła null")));
	}

}
