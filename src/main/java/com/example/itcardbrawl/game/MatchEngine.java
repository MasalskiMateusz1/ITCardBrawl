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
			case Move.PlayCard play -> playCard(state, play.instanceId(), play.target());
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
				if (!hasTargetEffect(card.card())) {
					moves.add(new Move.PlayCard(card.instanceId(), null));
				}
				else {
					List<Target> targets = eventTargets(match, side, card.card());
					for (Target target : targets) {
						moves.add(new Move.PlayCard(card.instanceId(), target));
					}
				}
			}
		}
		List<BoardCharacter> targets = allowedTargets(match, side.opponent());
		boolean stressAllowed = !isGuarded(match, side.opponent());
		for (BoardCharacter attacker : own.board()) {
			if (!canAttack(match, side, attacker)) {
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

	private static boolean hasTargetEffect(Card card) {
		return card.type() == CardType.EVENT && card.effects().stream().anyMatch(e -> e.scope() == EffectScope.TARGET);
	}

	private static List<Target> eventTargets(Match match, Side actor, Card card) {
		List<Effect> targeted = card.effects().stream().filter(e -> e.scope() == EffectScope.TARGET).toList();
		if (targeted.isEmpty()) {
			return List.of();
		}
		List<Target> result = new ArrayList<>();
		for (Side side : Side.values()) {
			for (BoardCharacter character : match.side(side).board()) {
				if (!stats(match, side, character).trapped()
						&& targeted.stream().allMatch(effect -> supportsCharacter(effect.kind()))) {
					result.add(new Target.Character(character.instance().instanceId()));
				}
			}
		}
		for (Side side : Side.values()) {
			if (targeted.stream().allMatch(effect -> supportsPlayer(effect.kind()))) {
				result.add(new Target.Player(side));
			}
		}
		return List.copyOf(result);
	}

	private static List<Target> eventTargets(State state, Side actor, Card card) {
		List<Effect> targeted = card.effects().stream().filter(e -> e.scope() == EffectScope.TARGET).toList();
		if (targeted.isEmpty()) {
			return List.of();
		}
		List<Target> result = new ArrayList<>();
		for (Side side : Side.values()) {
			for (BoardCharacter character : state.side(side).board) {
				if (!stats(state, side, character).trapped()
						&& targeted.stream().allMatch(effect -> supportsCharacter(effect.kind()))) {
					result.add(new Target.Character(character.instance().instanceId()));
				}
			}
		}
		for (Side side : Side.values()) {
			if (targeted.stream().allMatch(effect -> supportsPlayer(effect.kind()))) {
				result.add(new Target.Player(side));
			}
		}
		return List.copyOf(result);
	}

	private static boolean supportsCharacter(EffectKind kind) {
		return kind != EffectKind.RESOURCE && kind != EffectKind.RESOURCE_MAX && kind != EffectKind.DRAW
				&& kind != EffectKind.STRESS;
	}

	private static boolean supportsPlayer(EffectKind kind) {
		return kind == EffectKind.DAMAGE || kind == EffectKind.HEAL;
	}

	private static void applyEffect(State state, CardInstance card, Effect effect, Target target) {
		Side actor = state.activeSide;
		CardInstance targetCharacter = target instanceof Target.Character character
				? findCharacter(state, character.instanceId()) : null;
		Side targetPlayer = target instanceof Target.Player player ? player.side() : null;
		state.log(new MatchEvent.EffectApplied(actor, card, effect, targetCharacter, targetPlayer));
		int amount = effect.amount();
		switch (effect.kind()) {
			case STRESS -> state.addStress(actor.opponent(), amount);
			case DAMAGE -> applyDamage(state, actor, effect.scope(), amount, target);
			case HEAL -> applyHeal(state, actor, effect.scope(), amount, target);
			case DRAW -> {
				for (int i = 0; i < amount && !state.finished(); i++) {
					drawOne(state, actor);
				}
			}
			case RESOURCE -> {
				SideState own = state.side(actor);
				own.resourceCurrent = Math.min(own.resourceCurrent + amount, MatchRules.RESOURCE_CAP);
			}
			case RESOURCE_MAX -> {
				SideState own = state.side(actor);
				own.resourceMax = Math.min(own.resourceMax + amount, MatchRules.RESOURCE_CAP);
			}
			case ATTACK, HEALTH, GUARD, RUSH, TRAP -> {
				// Modyfikatory zaczynają działać w fazie efektów trwających.
			}
		}
	}

	private static CardInstance findCharacter(State state, int instanceId) {
		for (Side side : Side.values()) {
			Optional<Integer> index = indexOf(state.side(side).board, instanceId);
			if (index.isPresent()) {
				return state.side(side).board.get(index.get()).instance();
			}
		}
		return null;
	}

	private static void applyDamage(State state, Side actor, EffectScope scope, int amount, Target target) {
		if (scope == EffectScope.TARGET) {
			if (target instanceof Target.Player player) {
				state.addStress(player.side(), amount);
			}
			else if (target instanceof Target.Character character) {
				addCharacterDamage(state, character.instanceId(), amount);
			}
			return;
		}
		if (scope == EffectScope.ENEMY_ALL) {
			state.addStress(actor.opponent(), amount);
		}
		List<Side> affected = switch (scope) {
			case ENEMY_BOARD, ENEMY_ALL -> List.of(actor.opponent());
			case ALL_BOARDS -> List.of(actor.opponent(), actor);
			default -> List.of();
		};
		for (Side side : affected) {
			for (BoardCharacter character : List.copyOf(state.side(side).board)) {
				addCharacterDamage(state, character.instance().instanceId(), amount);
			}
		}
	}

	private static void addCharacterDamage(State state, int instanceId, int amount) {
		for (Side side : Side.values()) {
			SideState player = state.side(side);
			Optional<Integer> index = indexOf(player.board, instanceId);
			if (index.isPresent()) {
				BoardCharacter character = player.board.get(index.get());
				player.board.set(index.get(), character.withDamage(character.damage() + amount));
				return;
			}
		}
	}

	private static void applyHeal(State state, Side actor, EffectScope scope, int amount, Target target) {
		if (scope == EffectScope.TARGET) {
			if (target instanceof Target.Player player) {
				SideState affected = state.side(player.side());
				state.addStress(player.side(), -Math.min(amount, affected.stress));
			}
			else if (target instanceof Target.Character character) {
				healCharacter(state, character.instanceId(), amount);
			}
			return;
		}
		if (scope == EffectScope.OWN_ALL) {
			SideState own = state.side(actor);
			state.addStress(actor, -Math.min(amount, own.stress));
		}
		List<Side> affected = switch (scope) {
			case OWN_BOARD, OWN_ALL -> List.of(actor);
			case ALL_BOARDS -> List.of(actor.opponent(), actor);
			default -> List.of();
		};
		for (Side side : affected) {
			for (BoardCharacter character : List.copyOf(state.side(side).board)) {
				healCharacter(state, character.instance().instanceId(), amount);
			}
		}
	}

	private static void healCharacter(State state, int instanceId, int amount) {
		for (Side side : Side.values()) {
			SideState player = state.side(side);
			Optional<Integer> index = indexOf(player.board, instanceId);
			if (index.isPresent()) {
				BoardCharacter character = player.board.get(index.get());
				player.board.set(index.get(), character.withDamage(Math.max(0, character.damage() - amount)));
				return;
			}
		}
	}

	private static void defeatCharacters(State state, Side actor) {
		for (Side side : List.of(actor.opponent(), actor)) {
			SideState owner = state.side(side);
			for (BoardCharacter character : List.copyOf(owner.board)) {
				if (stats(state, side, character).remainingHealth(character.damage()) <= 0) {
					owner.board.remove(character);
					owner.graveyard.add(character.instance());
					state.log(new MatchEvent.CharacterDefeated(side, character.instance()));
				}
			}
		}
	}

	private static CharacterStats stats(Match match, Side owner, BoardCharacter character) {
		return CharacterStats.of(character, owner, match.player().activeEvents(), match.opponent().activeEvents());
	}

	private static CharacterStats stats(State state, Side owner, BoardCharacter character) {
		return CharacterStats.of(character, owner, state.player.activeEvents, state.opponent.activeEvents);
	}

	private static boolean canAttack(Match match, Side owner, BoardCharacter character) {
		CharacterStats stats = stats(match, owner, character);
		return !character.attacked() && (!character.enteredThisTurn() || stats.has(AbilityKeyword.RUSH)) && !stats.trapped();
	}

	private static boolean canAttack(State state, Side owner, BoardCharacter character) {
		CharacterStats stats = stats(state, owner, character);
		return !character.attacked() && (!character.enteredThisTurn() || stats.has(AbilityKeyword.RUSH)) && !stats.trapped();
	}

	private static boolean isGuarded(Match match, Side owner) {
		return match.side(owner).board().stream().anyMatch(c -> !stats(match, owner, c).trapped()
				&& stats(match, owner, c).has(AbilityKeyword.GUARD));
	}

	private static boolean isGuarded(State state, Side owner) {
		return state.side(owner).board.stream().anyMatch(c -> !stats(state, owner, c).trapped()
				&& stats(state, owner, c).has(AbilityKeyword.GUARD));
	}

	// Jeśli przeciwnik ma postacie z Ochroną, tylko one są dozwolonym celem (stres też odpada).
	private static List<BoardCharacter> allowedTargets(Match match, Side owner) {
		List<BoardCharacter> board = match.side(owner).board();
		return isGuarded(match, owner) ? board.stream().filter(c -> !stats(match, owner, c).trapped()
				&& stats(match, owner, c).has(AbilityKeyword.GUARD)).toList()
				: board.stream().filter(c -> !stats(match, owner, c).trapped()).toList();
	}

	private static void playCard(State state, int instanceId, Target target) {
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
		if (character && target != null) {
			throw new IllegalMoveException("Postać nie przyjmuje celu");
		}
		if (character && own.board.size() >= MatchRules.BOARD_LIMIT) {
			throw new IllegalMoveException("Stół jest pełny");
		}
		if (!character) {
			List<Target> allowed = eventTargets(state, actor, definition);
			if (hasTargetEffect(definition) != (target != null) || target != null && !allowed.contains(target)) {
				throw new IllegalMoveException("Brak lub nieprawidłowy cel wydarzenia");
			}
		}

		own.hand.remove(card);
		own.resourceCurrent -= definition.cost();
		state.log(new MatchEvent.CardPlayed(actor, card));

		if (!character) {
			for (Effect effect : definition.effects()) {
				if (effect.scope() == EffectScope.TARGET && target instanceof Target.Character targeted
						&& findCharacter(state, targeted.instanceId()) == null) {
					continue;
				}
				applyEffect(state, card, effect, effect.scope() == EffectScope.TARGET ? target : null);
				defeatCharacters(state, actor);
				if (state.finished()) {
					break;
				}
			}
			if (definition.duration() == 0) {
				own.graveyard.add(card);
			}
			else {
				Integer targetId = target instanceof Target.Character characterTarget ? characterTarget.instanceId() : null;
				own.activeEvents.add(new ActiveEvent(card, definition.duration(), targetId));
				defeatCharacters(state, actor);
			}
			return;
		}

		BoardCharacter played = new BoardCharacter(card, 0, true, false);
		own.board.add(played);
		Ability ability = definition.ability();
		if (ability != null) switch (ability.keyword()) {
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
		defeatCharacters(state, actor);
	}

	private static void attack(State state, int attackerId, Integer targetId) {
		Side actor = state.activeSide;
		SideState own = state.active();
		SideState enemy = state.side(actor.opponent());
		int attackerIndex = indexOf(own.board, attackerId)
			.orElseThrow(() -> new IllegalMoveException("Atakującej postaci nie ma na stole"));
		BoardCharacter attacker = own.board.get(attackerIndex);
		CharacterStats attackerStats = stats(state, actor, attacker);
		if (attackerStats.trapped() || !canAttack(state, actor, attacker)) {
			throw new IllegalMoveException("Postać nie może teraz atakować");
		}

		if (targetId == null) {
			if (isGuarded(state, actor.opponent())) {
				throw new IllegalMoveException("Najpierw trzeba zaatakować postać z Ochroną");
			}
			own.board.set(attackerIndex, attacker.markAttacked());
			state.log(new MatchEvent.Attacked(actor, attacker.instance(), null, attackerStats.attack(), 0));
			state.addStress(actor.opponent(), attackerStats.attack());
			return;
		}

		int targetIndex = indexOf(enemy.board, targetId)
			.orElseThrow(() -> new IllegalMoveException("Celu nie ma na stole przeciwnika"));
		BoardCharacter target = enemy.board.get(targetIndex);
		CharacterStats targetStats = stats(state, actor.opponent(), target);
		if (targetStats.trapped()) {
			throw new IllegalMoveException("Uwięziona postać nie może być celem ataku");
		}
		if (isGuarded(state, actor.opponent()) && !targetStats.has(AbilityKeyword.GUARD)) {
			throw new IllegalMoveException("Najpierw trzeba zaatakować postać z Ochroną");
		}

		// Obie postacie zadają sobie obrażenia jednocześnie.
		BoardCharacter hitAttacker = attacker.withDamage(attacker.damage() + targetStats.attack()).markAttacked();
		BoardCharacter hitTarget = target.withDamage(target.damage() + attackerStats.attack());
		own.board.set(attackerIndex, hitAttacker);
		enemy.board.set(targetIndex, hitTarget);
		state.log(new MatchEvent.Attacked(actor, attacker.instance(), target.instance(), attackerStats.attack(),
				targetStats.attack()));

		defeatCharacters(state, actor);
	}

	private static void endTurn(State state) {
		Side ending = state.activeSide;
		SideState own = state.active();

		// (1) Nadgodziny: każda postać z OVERTIME dodaje 1 stresu przeciwnikowi; koniec partii przerywa resztę.
		for (BoardCharacter character : List.copyOf(own.board)) {
			if (stats(state, ending, character).has(AbilityKeyword.OVERTIME)) {
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
				remaining.add(new ActiveEvent(event.instance(), turnsLeft, event.targetId()));
			}
		}
		own.activeEvents.clear();
		own.activeEvents.addAll(remaining);
		defeatCharacters(state, starting);

		// Dobranie: pusta talia przegrywa partię, pełna ręka pali kartę.
		drawOne(state, starting);
		if (state.finished()) {
			return;
		}

		own.resourceMax = Math.min(own.resourceMax + 1, MatchRules.RESOURCE_CAP);
		own.resourceCurrent = own.resourceMax;
		own.board.replaceAll(c -> c.withAttackReady(true));
	}

	private static void drawOne(State state, Side drawing) {
		SideState own = state.side(drawing);
		if (own.deck.isEmpty()) {
			state.finish(drawing.opponent(), EndReason.EMPTY_DECK, drawing);
			return;
		}
		CardInstance drawn = own.deck.removeFirst();
		if (own.hand.size() >= MatchRules.HAND_LIMIT) {
			own.graveyard.add(drawn);
			state.log(new MatchEvent.CardBurned(drawing, drawn));
		}
		else {
			own.hand.add(drawn);
			state.log(new MatchEvent.CardDrawn(drawing, drawn));
		}
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
