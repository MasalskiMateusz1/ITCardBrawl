package com.example.itcardbrawl.game;

import java.util.Optional;
import java.util.UUID;

import com.example.itcardbrawl.game.Match.Side;
import com.example.itcardbrawl.game.Match.Status;

import org.springframework.stereotype.Component;

/**
 * Orkiestracja partii gracza: start, ruchy gracza i tura komputera. Każdy ruch wraz z ewentualną turą
 * komputera wykonuje się w jednej atomowej aktualizacji {@link MatchStore#update}.
 */
@Component
public class MatchService {

	private final MatchFactory factory;

	private final MatchEngine engine;

	private final RandomOpponent opponent;

	private final MatchStore store;

	public MatchService(MatchFactory factory, MatchEngine engine, RandomOpponent opponent, MatchStore store) {
		this.factory = factory;
		this.engine = engine;
		this.opponent = opponent;
		this.store = store;
	}

	/**
	 * Tworzy nową partię gracza, zastępując poprzednią. Gdy zaczyna komputer, od razu rozgrywa jego turę.
	 *
	 * @throws IllegalArgumentException gdy frakcja nie istnieje albo nie jest grywalna
	 */
	public Match start(String playerKey, String factionId) {
		Match match = factory.create(factionId);
		if (match.activeSide() == Side.OPPONENT) {
			match = opponent.playTurn(match);
		}
		store.save(playerKey, match);
		return match;
	}

	/**
	 * Wykonuje ruch gracza; po zakończeniu tury w trwającej partii rozgrywa też turę komputera.
	 *
	 * @return nowy stan partii albo {@link Optional#empty()}, gdy gracz nie ma bieżącej partii
	 * @throws StaleMoveException gdy {@code matchId} albo {@code turn} nie zgadzają się z bieżącą partią
	 * @throws IllegalMoveException gdy ruch łamie zasady
	 */
	public Optional<Match> move(String playerKey, UUID matchId, int turn, Move move) {
		return store.update(playerKey, current -> {
			if (!current.id().equals(matchId)) {
				throw new StaleMoveException("Ruch dotyczy innej partii niż bieżąca");
			}
			if (current.turnNumber() != turn) {
				throw new StaleMoveException("Ruch dotyczy innej tury niż bieżąca");
			}
			Match after = engine.apply(current, Side.PLAYER, move);
			if (move instanceof Move.EndTurn && after.status() == Status.IN_PROGRESS) {
				after = opponent.playTurn(after);
			}
			return after;
		});
	}

	public Optional<Match> current(String playerKey) {
		return store.findCurrent(playerKey);
	}

}
