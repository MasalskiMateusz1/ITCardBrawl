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
	 * Atomowo zastępuje bieżącą partię gracza wynikiem {@code change}. {@code change} liczy się poza blokadą
	 * mapy (tura komputera nie blokuje innych graczy); zapis udaje się tylko wtedy, gdy partia w międzyczasie
	 * się nie zmieniła — inaczej {@code change} jest wywoływane ponownie na nowym stanie. {@code change} musi
	 * więc być bez trwałych skutków ubocznych poza wynikiem. Wyjątek z {@code change} zostawia poprzednią
	 * partię bez zmian i trafia do wywołującego.
	 *
	 * @return nowy stan albo {@link Optional#empty()}, gdy gracz nie ma bieżącej partii
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
