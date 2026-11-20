package com.example.itcardbrawl.game;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.example.itcardbrawl.game.Match.Side;
import com.example.itcardbrawl.game.Match.Status;
import com.example.itcardbrawl.game.MatchResult.EndReason;

import org.springframework.stereotype.Component;

/**
 * Silnik zasad partii: jedyne miejsce, które zmienia stan {@link Match}. Gracz i komputer przechodzą przez
 * {@link #apply}; każdy krok trafia do logu jako {@link MatchEvent}. Silnik jest bezstanowy i bez zależności.
 */
@Component
public class MatchEngine {

	/**
	 * Wykonuje ruch strony i zwraca nowy stan partii.
	 *
	 * @throws IllegalMoveException gdy ruch łamie zasady (partia zakończona, nie ta tura, zła karta lub cel)
	 */
	public Match apply(Match match, Side side, Move move) {
		if (match.status() != Status.IN_PROGRESS) {
			throw new IllegalMoveException("Partia jest zakończona");
		}
		if (side != match.activeSide()) {
			throw new IllegalMoveException("To nie jest tura tej strony");
		}
		State state = new State(match);
		switch (move) {
			case Move.PlayCard play -> playCard(state, play.instanceId());
			case Move.Attack attack -> attack(state, attack.attackerId(), attack.targetId());
			case Move.EndTurn endTurn -> endTurn(state);
			case null -> throw new IllegalMoveException("Brak ruchu");
		}
		return state.toMatch();
	}

	/**
	 * Poprawne zagrania i ataki strony: najpierw zagrania kart z ręki, potem ataki. Lista nie zawiera
	 * {@link Move.EndTurn}, choć jest on zawsze poprawny dla aktywnej strony w trwającej partii.
	 */
	public List<Move> legalMoves(Match match, Side side) {
		if (match.status() != Status.IN_PROGRESS || side != match.activeSide()) {
			return List.of();
		}
		MatchSide own = match.side(side);
		MatchSide enemy = match.side(side.opponent());
		List<Move> moves = new ArrayList<>();
		for (CardInstance card : own.hand()) {
			if (canPlay(own, card)) {
				moves.add(new Move.PlayCard(card.instanceId()));
			}
		}
		List<BoardCharacter> targets = allowedTargets(enemy.board());
		boolean stressAllowed = !isGuarded(enemy.board());
		for (BoardCharacter attacker : own.board()) {
			if (!attacker.canAttack()) {
				continue;
			}
			for (BoardCharacter target : targets) {
				moves.add(new Move.Attack(attacker.instance().instanceId(), target.instance().instanceId()));
			}
			if (stressAllowed) {
				moves.add(new Move.Attack(attacker.instance().instanceId(), null));
			}
		}
		return List.copyOf(moves);
	}

	private static boolean canPlay(MatchSide side, CardInstance card) {
		return card.card().cost() <= side.resourceCurrent()
				&& (card.card().type() != CardType.CHARACTER || side.board().size() < MatchRules.BOARD_LIMIT);
	}

	private static boolean isGuarded(List<BoardCharacter> enemyBoard) {
		return enemyBoard.stream().anyMatch(c -> c.has(AbilityKeyword.GUARD));
	}

	// Jeśli przeciwnik ma postacie z Ochroną, tylko one są dozwolonym celem (stres też odpada).
	private static List<BoardCharacter> allowedTargets(List<BoardCharacter> enemyBoard) {
		return isGuarded(enemyBoard) ? enemyBoard.stream().filter(c -> c.has(AbilityKeyword.GUARD)).toList()
				: enemyBoard;
	}

	private static void playCard(State state, int instanceId) {
		Side actor = state.activeSide;
		SideState own = state.active();
		CardInstance card = own.hand.stream()
			.filter(c -> c.instanceId() == instanceId)
			.findFirst()
			.orElseThrow(() -> new IllegalMoveException("Karty nie ma na ręce"));
		Card definition = card.card();
		if (definition.cost() > own.resourceCurrent) {
			throw new IllegalMoveException("Za mało zasobu na zagranie karty");
		}
		boolean character = definition.type() == CardType.CHARACTER;
		if (character && own.board.size() >= MatchRules.BOARD_LIMIT) {
			throw new IllegalMoveException("Stół jest pełny");
		}

		own.hand.remove(card);
		own.resourceCurrent -= definition.cost();
		state.log(new MatchEvent.CardPlayed(actor, card));

		if (!character) {
			// Wydarzenia nie mają jeszcze efektu (S-04): płacą koszt i zostają w grze przez duration tur.
			if (definition.duration() == 0) {
				own.graveyard.add(card);
			}
			else {
				own.activeEvents.add(new ActiveEvent(card, definition.duration()));
			}
			return;
		}

		BoardCharacter played = new BoardCharacter(card, 0, false);
		own.board.add(played.withCanAttack(played.has(AbilityKeyword.RUSH)));
		Ability ability = definition.ability();
		if (ability == null) {
			return;
		}
		switch (ability.keyword()) {
			case ON_PLAY_STRESS -> {
				state.log(new MatchEvent.AbilityTriggered(actor, card, ability.keyword(), ability.amount()));
				state.addStress(actor.opponent(), ability.amount());
			}
			case HEAL -> {
				state.log(new MatchEvent.AbilityTriggered(actor, card, ability.keyword(), ability.amount()));
				state.addStress(actor, -Math.min(ability.amount(), own.stress));
			}
			case GUARD, RUSH, OVERTIME -> {
				// Zdolności stałe: działają przy ataku albo na koniec tury, nie przy zagraniu.
			}
		}
	}

	private static void attack(State state, int attackerId, Integer targetId) {
		Side actor = state.activeSide;
		SideState own = state.active();
		SideState enemy = state.side(actor.opponent());
		int attackerIndex = indexOf(own.board, attackerId)
			.orElseThrow(() -> new IllegalMoveException("Atakującej postaci nie ma na stole"));
		BoardCharacter attacker = own.board.get(attackerIndex);
		if (!attacker.canAttack()) {
			throw new IllegalMoveException("Postać nie może teraz atakować");
		}

		if (targetId == null) {
			if (isGuarded(enemy.board)) {
				throw new IllegalMoveException("Najpierw trzeba zaatakować postać z Ochroną");
			}
			own.board.set(attackerIndex, attacker.withCanAttack(false));
			state.log(new MatchEvent.Attacked(actor, attacker.instance(), null, attacker.attack(), 0));
			state.addStress(actor.opponent(), attacker.attack());
			return;
		}

		int targetIndex = indexOf(enemy.board, targetId)
			.orElseThrow(() -> new IllegalMoveException("Celu nie ma na stole przeciwnika"));
		BoardCharacter target = enemy.board.get(targetIndex);
		if (isGuarded(enemy.board) && !target.has(AbilityKeyword.GUARD)) {
			throw new IllegalMoveException("Najpierw trzeba zaatakować postać z Ochroną");
		}

		// Obie postacie zadają sobie obrażenia jednocześnie.
		BoardCharacter hitAttacker = attacker.withDamage(attacker.damage() + target.attack()).withCanAttack(false);
		BoardCharacter hitTarget = target.withDamage(target.damage() + attacker.attack());
		own.board.set(attackerIndex, hitAttacker);
		enemy.board.set(targetIndex, hitTarget);
		state.log(new MatchEvent.Attacked(actor, attacker.instance(), target.instance(), attacker.attack(),
				target.attack()));

		if (hitTarget.isDefeated()) {
			enemy.board.remove(targetIndex);
			enemy.graveyard.add(target.instance());
			state.log(new MatchEvent.CharacterDefeated(actor.opponent(), target.instance()));
		}
		if (hitAttacker.isDefeated()) {
			own.board.remove(attackerIndex);
			own.graveyard.add(attacker.instance());
			state.log(new MatchEvent.CharacterDefeated(actor, attacker.instance()));
		}
	}

	private static void endTurn(State state) {
		Side ending = state.activeSide;
		SideState own = state.active();

		// (1) Nadgodziny: każda postać z OVERTIME dodaje 1 stresu przeciwnikowi; koniec partii przerywa resztę.
		for (BoardCharacter character : List.copyOf(own.board)) {
			if (character.has(AbilityKeyword.OVERTIME)) {
				state.log(new MatchEvent.AbilityTriggered(ending, character.instance(), AbilityKeyword.OVERTIME, 1));
				state.addStress(ending.opponent(), 1);
				if (state.finished()) {
					return;
				}
			}
		}

		// (2) Obrażenia znikają na koniec tury u obu stron.
		state.player.board.replaceAll(c -> c.withDamage(0));
		state.opponent.board.replaceAll(c -> c.withDamage(0));
		state.log(new MatchEvent.TurnEnded(ending));

		// (3) Zmiana aktywnej strony, (4) początek jej tury.
		state.activeSide = ending.opponent();
		state.turnNumber++;
		startTurn(state);
	}

	private static void startTurn(State state) {
		Side starting = state.activeSide;
		SideState own = state.active();
		state.log(new MatchEvent.TurnStarted(starting, state.turnNumber));

		// Tick aktywnych wydarzeń właściciela; po wygaśnięciu trafiają na cmentarz.
		List<ActiveEvent> remaining = new ArrayList<>();
		for (ActiveEvent event : own.activeEvents) {
			int turnsLeft = event.turnsLeft() - 1;
			if (turnsLeft <= 0) {
				own.graveyard.add(event.instance());
				state.log(new MatchEvent.EventExpired(starting, event.instance()));
			}
			else {
				remaining.add(new ActiveEvent(event.instance(), turnsLeft));
			}
		}
		own.activeEvents.clear();
		own.activeEvents.addAll(remaining);

		// Dobranie: pusta talia przegrywa partię, pełna ręka pali kartę.
		if (own.deck.isEmpty()) {
			state.finish(starting.opponent(), EndReason.EMPTY_DECK, starting);
			return;
		}
		CardInstance drawn = own.deck.removeFirst();
		if (own.hand.size() >= MatchRules.HAND_LIMIT) {
			own.graveyard.add(drawn);
			state.log(new MatchEvent.CardBurned(starting, drawn));
		}
		else {
			own.hand.add(drawn);
			state.log(new MatchEvent.CardDrawn(starting, drawn));
		}

		own.resourceMax = Math.min(own.resourceMax + 1, MatchRules.RESOURCE_CAP);
		own.resourceCurrent = own.resourceMax;
		own.board.replaceAll(c -> c.withCanAttack(true));
	}

	private static Optional<Integer> indexOf(List<BoardCharacter> board, int instanceId) {
		for (int i = 0; i < board.size(); i++) {
			if (board.get(i).instance().instanceId() == instanceId) {
				return Optional.of(i);
			}
		}
		return Optional.empty();
	}

	/** Robocza, mutowalna kopia partii na czas jednego ruchu. */
	private static final class State {

		private final Match original;

		private final SideState player;

		private final SideState opponent;

		private final List<MatchEvent> log;

		private Side activeSide;

		private int turnNumber;

		private Status status;

		private MatchResult result;

		State(Match match) {
			this.original = match;
			this.player = new SideState(match.player());
			this.opponent = new SideState(match.opponent());
			this.log = new ArrayList<>(match.log());
			this.activeSide = match.activeSide();
			this.turnNumber = match.turnNumber();
			this.status = match.status();
			this.result = match.result();
		}

		SideState side(Side side) {
			return side == Side.PLAYER ? player : opponent;
		}

		SideState active() {
			return side(activeSide);
		}

		void log(MatchEvent event) {
			log.add(event);
		}

		boolean finished() {
			return status == Status.FINISHED;
		}

		// Każda zmiana stresu od razu sprawdza limit; strona, która go osiągnęła, przegrywa.
		void addStress(Side affected, int delta) {
			SideState side = side(affected);
			int from = side.stress;
			side.stress = from + delta;
			if (side.stress != from) {
				log(new MatchEvent.StressChanged(activeSide, affected, from, side.stress));
			}
			if (side.stress >= MatchRules.STRESS_LIMIT) {
				finish(affected.opponent(), EndReason.STRESS_LIMIT, activeSide);
			}
		}

		void finish(Side winner, EndReason reason, Side actor) {
			status = Status.FINISHED;
			result = new MatchResult(winner, reason);
			log(new MatchEvent.MatchEnded(actor, result));
		}

		Match toMatch() {
			return new Match(original.id(), turnNumber, activeSide, player.toMatchSide(), opponent.toMatchSide(),
					status, result, log);
		}

	}

	private static final class SideState {

		private final Faction faction;

		private final List<CardInstance> deck;

		private final List<CardInstance> hand;

		private final List<BoardCharacter> board;

		private final List<CardInstance> graveyard;

		private final List<ActiveEvent> activeEvents;

		private int stress;

		private int resourceCurrent;

		private int resourceMax;

		SideState(MatchSide side) {
			this.faction = side.faction();
			this.deck = new ArrayList<>(side.deck());
			this.hand = new ArrayList<>(side.hand());
			this.board = new ArrayList<>(side.board());
			this.graveyard = new ArrayList<>(side.graveyard());
			this.activeEvents = new ArrayList<>(side.activeEvents());
			this.stress = side.stress();
			this.resourceCurrent = side.resourceCurrent();
			this.resourceMax = side.resourceMax();
		}

		MatchSide toMatchSide() {
			return new MatchSide(faction, deck, hand, stress, resourceCurrent, resourceMax, board, graveyard,
					activeEvents);
		}

	}

}
