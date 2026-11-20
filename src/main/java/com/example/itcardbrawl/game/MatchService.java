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
	 * Tworzy nową partię gracza, zastępując poprzednią. Gdy zaczyna komputer, od razu rozgrywa jego turę;
	 * {@code replay} to wtedy stan startowy i stan po każdym ruchu komputera, w przeciwnym razie jest pusty.
	 *
	 * @throws IllegalArgumentException gdy frakcja nie istnieje albo nie jest grywalna
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
	 * Wykonuje ruch gracza; po zakończeniu tury w trwającej partii rozgrywa też turę komputera. {@code replay}
	 * to wtedy stan po końcu tury gracza i stan po każdym ruchu komputera; dla pozostałych ruchów jest pusty.
	 *
	 * @return nowy stan partii albo {@link Optional#empty()}, gdy gracz nie ma bieżącej partii
	 * @throws StaleMoveException gdy {@code matchId} albo {@code turn} nie zgadzają się z bieżącą partią
	 * @throws IllegalMoveException gdy ruch łamie zasady
	 */
	public Optional<MatchOutcome> move(String playerKey, UUID matchId, int turn, Move move) {
		// Kroki powstają w tej samej atomowej aktualizacji, a magazyn przyjmuje tylko stan końcowy —
		// wynosimy je z lambdy przez lokalny holder.
		AtomicReference<List<Match>> replay = new AtomicReference<>(List.of());
		return store.update(playerKey, current -> {
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
