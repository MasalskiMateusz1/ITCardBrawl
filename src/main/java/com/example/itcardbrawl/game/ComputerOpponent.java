package com.example.itcardbrawl.game;

import java.util.ArrayList;
import java.util.List;
import java.util.random.RandomGenerator;

import com.example.itcardbrawl.game.Match.Side;
import com.example.itcardbrawl.game.Match.Status;

import org.springframework.stereotype.Component;

/**
 * Komputer grający przez {@link MatchEngine} z oceną 1 ruchu wprzód: każdy poprawny ruch jest symulowany
 * i oceniany przez {@link PositionEvaluator}. Ruch wygrywający partię jest brany od razu; ruchy, które nie
 * poprawiają oceny, odpadają; spośród pozostałych losuje z wagą {@code exp((score - best) / TEMPERATURE)}.
 * Gdy nic nie zostaje, kończy turę. Przed zdarzeniami każdego ruchu (także końca tury) komputer wpisuje do
 * logu jego powód ({@link MatchEvent.MoveExplained}), wyprowadzony z ruchu i składników oceny przed/po.
 */
@Component
public class ComputerOpponent {

	/** Bezpiecznik: po tylu ruchach w jednej turze komputer kończy ją bez względu na dostępne ruchy. */
	static final int MAX_MOVES_PER_TURN = 100;

	/** Temperatura losowania: im mniejsza, tym częściej wybierany jest ruch o najlepszej ocenie. */
	static final double TEMPERATURE = 1.0;

	/** Minimalna poprawa oceny, by ruch nie był uznany za bezcelowy (chroni przed szumem zmiennoprzecinkowym). */
	private static final double MIN_GAIN = 1e-9;

	private static final Side SIDE = Side.OPPONENT;

	private final MatchEngine engine;

	private final PositionEvaluator evaluator;

	private final RandomGenerator random;

	public ComputerOpponent(MatchEngine engine, PositionEvaluator evaluator, RandomGenerator random) {
		this.engine = engine;
		this.evaluator = evaluator;
		this.random = random;
	}

	/**
	 * Rozgrywa turę komputera. Zwraca partię z aktywnym graczem albo zakończoną; gdy nie jest tura komputera,
	 * zwraca partię bez zmian.
	 */
	public Match playTurn(Match match) {
		List<Match> steps = playTurnSteps(match);
		return steps.isEmpty() ? match : steps.getLast();
	}

	/**
	 * Rozgrywa turę komputera i zwraca stan po każdym jego ruchu (zagranie, atak, koniec tury); ostatni element
	 * to stan końcowy. Gdy nie jest tura komputera, zwraca pustą listę.
	 */
	public List<Match> playTurnSteps(Match match) {
		List<Match> steps = new ArrayList<>();
		Match current = match;
		int moves = 0;
		while (current.status() == Status.IN_PROGRESS && current.activeSide() == SIDE) {
			// Bezpiecznik opisujemy jak brak korzystnego ruchu (END_NO_GAIN): w praktyce nie zadziała, a osobny
			// rodzaj powodu dla stanu nieosiągalnego tylko by zaciemnił log.
			Decision decision = moves >= MAX_MOVES_PER_TURN
					? endTurn(current, MoveReason.END_NO_GAIN) : decide(current);
			// Powód stoi w logu przed zdarzeniami swojego ruchu, więc krok odtwarzania obejmuje i jedno, i drugie.
			current = decision.after().withEventAt(current.log().size(), decision.reason());
			steps.add(current);
			moves++;
		}
		return List.copyOf(steps);
	}

	/** Wybiera następny ruch komputera w bieżącym stanie; {@link Move.EndTurn}, gdy żaden ruch nie pomaga. */
	Move chooseMove(Match current) {
		return decide(current).move();
	}

	/** Wybrany ruch, stan po nim (bez powodu w logu) i powód ruchu. */
	private record Decision(Move move, Match after, MatchEvent.MoveExplained reason) {
	}

	private Decision decide(Match current) {
		List<Move> legal = engine.legalMoves(current, SIDE);
		if (legal.isEmpty()) {
			return endTurn(current, MoveReason.END_NO_MOVES);
		}
		PositionEvaluator.Score before = evaluator.score(current, SIDE);
		double baseline = before.total();
		List<Move> candidates = new ArrayList<>();
		List<Match> afters = new ArrayList<>();
		List<PositionEvaluator.Score> scores = new ArrayList<>();
		double best = Double.NEGATIVE_INFINITY;
		for (Move move : legal) {
			Match after = engine.apply(current, SIDE, move);
			if (after.status() == Status.FINISHED && after.result() != null && after.result().winner() == SIDE) {
				return new Decision(move, after, explained(MoveReason.LETHAL, actingCard(current, move), null,
						playerHpLoss(current, after)));
			}
			PositionEvaluator.Score score = evaluator.score(after, SIDE);
			if (score.total() > baseline + MIN_GAIN) {
				candidates.add(move);
				afters.add(after);
				scores.add(score);
				best = Math.max(best, score.total());
			}
		}
		if (candidates.isEmpty()) {
			return endTurn(current, MoveReason.END_NO_GAIN);
		}
		double[] weights = new double[candidates.size()];
		double sum = 0;
		for (int i = 0; i < weights.length; i++) {
			weights[i] = Math.exp((scores.get(i).total() - best) / TEMPERATURE);
			sum += weights[i];
		}
		double roll = random.nextDouble() * sum;
		int chosen = candidates.size() - 1;
		for (int i = 0; i < weights.length; i++) {
			roll -= weights[i];
			if (roll < 0) {
				chosen = i;
				break;
			}
		}
		Move move = candidates.get(chosen);
		Match after = afters.get(chosen);
		return new Decision(move, after, reason(current, move, after, before, scores.get(chosen)));
	}

	private Decision endTurn(Match current, MoveReason reason) {
		Move move = new Move.EndTurn();
		return new Decision(move, engine.apply(current, SIDE, move), explained(reason, null, null, 0));
	}

	/**
	 * Powód ruchu, który poprawia ocenę (wygrana i koniec tury mają powód ustalony wcześniej). Atak wynika z
	 * celu i skutku; zagranie postaci to rozbudowa stołu, chyba że zdolność przy zagraniu daje więcej;
	 * wydarzenie — składnik {@link PositionEvaluator.Score} o największym przyroście.
	 */
	private static MatchEvent.MoveExplained reason(Match before, Move move, Match after,
			PositionEvaluator.Score scoreBefore, PositionEvaluator.Score scoreAfter) {
		CardInstance card = actingCard(before, move);
		double ownHp = scoreAfter.ownHp() - scoreBefore.ownHp();
		double enemyHp = scoreAfter.enemyHp() - scoreBefore.enemyHp();
		double ownBoard = scoreAfter.ownBoard() - scoreBefore.ownBoard();
		double enemyBoard = scoreAfter.enemyBoard() - scoreBefore.enemyBoard();
		double cards = scoreAfter.cards() - scoreBefore.cards();
		double resources = scoreAfter.resources() - scoreBefore.resources();
		return switch (move) {
			case Move.Attack attack -> attackReason(before, after, attack, card, enemyBoard + ownBoard);
			case Move.PlayCard play when card.card().type() == CardType.CHARACTER -> {
				if (enemyHp > ownBoard) {
					yield explained(MoveReason.PRESSURE, card, null, playerHpLoss(before, after));
				}
				if (ownHp > ownBoard) {
					yield explained(MoveReason.DEFEND, card, null, ownHpGain(before, after));
				}
				yield explained(MoveReason.DEVELOP_BOARD, card, null, 0);
			}
			case Move.PlayCard play -> {
				CardInstance target = play.target() instanceof Target.Character character
						? boardCharacter(before, character.instanceId()) : null;
				double top = max(ownHp, enemyHp, ownBoard, enemyBoard, cards, resources);
				if (top == enemyHp) {
					yield explained(MoveReason.PRESSURE, card, null, playerHpLoss(before, after));
				}
				if (top == enemyBoard) {
					CardInstance defeated = firstDefeated(before, after);
					yield defeated != null ? explained(MoveReason.REMOVE_THREAT, card, defeated, 0)
							: explained(MoveReason.WEAKEN, card, target, 0);
				}
				if (top == ownBoard) {
					// Przy niskim własnym HP wzmocnienie stołu (np. Ochrona) to obrona, nie rozbudowa.
					yield before.side(SIDE).hp() < PositionEvaluator.OWN_HP_LOW
							? explained(MoveReason.DEFEND, card, null, ownHpGain(before, after))
							: explained(MoveReason.STRENGTHEN, card, target, 0);
				}
				if (top == ownHp) {
					yield explained(MoveReason.DEFEND, card, null, ownHpGain(before, after));
				}
				if (top == cards) {
					yield explained(MoveReason.DRAW_CARDS, card, null, drawnCards(before, after));
				}
				yield explained(MoveReason.GAIN_BUDGET, card, null, budgetGain(before, after, card));
			}
			case Move.EndTurn endTurn -> explained(MoveReason.END_NO_GAIN, null, null, 0);
		};
	}

	/**
	 * {@code boardSwing} to łączna zmiana ocen obu stołów: wymiana jest korzystna, gdy stół gracza traci
	 * więcej niż własny. Atak w postać bez takiej przewagi tylko osłabia cel.
	 */
	private static MatchEvent.MoveExplained attackReason(Match before, Match after, Move.Attack attack,
			CardInstance attacker, double boardSwing) {
		if (attack.targetId() == null) {
			return explained(MoveReason.PRESSURE, attacker, null, playerHpLoss(before, after));
		}
		CardInstance target = boardCharacter(before, attack.targetId());
		int damage = CharacterStats.of(board(before, SIDE, attack.attackerId()), SIDE,
				before.player().activeEvents(), before.opponent().activeEvents()).attack();
		if (boardCharacter(after, attack.targetId()) == null) {
			return explained(MoveReason.REMOVE_THREAT, attacker, target, damage);
		}
		boolean guard = CharacterStats.of(board(before, SIDE.opponent(), attack.targetId()), SIDE.opponent(),
				before.player().activeEvents(), before.opponent().activeEvents()).has(AbilityKeyword.GUARD);
		if (guard) {
			return explained(MoveReason.OPEN_GUARD, attacker, target, damage);
		}
		return boardSwing > MIN_GAIN ? explained(MoveReason.FAVORABLE_TRADE, attacker, target, damage)
				: explained(MoveReason.WEAKEN, attacker, target, 0);
	}

	private static MatchEvent.MoveExplained explained(MoveReason reason, CardInstance card, CardInstance target,
			int amount) {
		return new MatchEvent.MoveExplained(SIDE, reason, card, target, amount);
	}

	/** Zagrywana karta z ręki albo atakująca postać; {@code null} dla końca tury. */
	private static CardInstance actingCard(Match match, Move move) {
		return switch (move) {
			case Move.PlayCard play -> match.side(SIDE).hand().stream()
				.filter(card -> card.instanceId() == play.instanceId()).findFirst().orElseThrow();
			case Move.Attack attack -> board(match, SIDE, attack.attackerId()).instance();
			case Move.EndTurn endTurn -> null;
		};
	}

	private static BoardCharacter board(Match match, Side owner, int instanceId) {
		return match.side(owner).board().stream().filter(c -> c.instance().instanceId() == instanceId).findFirst()
			.orElseThrow();
	}

	private static CardInstance boardCharacter(Match match, int instanceId) {
		for (Side side : Side.values()) {
			for (BoardCharacter character : match.side(side).board()) {
				if (character.instance().instanceId() == instanceId) {
					return character.instance();
				}
			}
		}
		return null;
	}

	/** Pierwsza postać gracza pokonana przez ruch (zdarzenia dopisane do logu przez ten ruch). */
	private static CardInstance firstDefeated(Match before, Match after) {
		return after.log().subList(before.log().size(), after.log().size()).stream()
			.filter(MatchEvent.CharacterDefeated.class::isInstance).map(MatchEvent.CharacterDefeated.class::cast)
			.filter(event -> event.side() == SIDE.opponent()).map(MatchEvent.CharacterDefeated::card).findFirst()
			.orElse(null);
	}

	private static int playerHpLoss(Match before, Match after) {
		return Math.max(0, before.side(SIDE.opponent()).hp() - after.side(SIDE.opponent()).hp());
	}

	private static int ownHpGain(Match before, Match after) {
		return Math.max(0, after.side(SIDE).hp() - before.side(SIDE).hp());
	}

	// Liczba dobranych kart (także spalonych przy pełnej ręce) — bez ich zawartości.
	private static int drawnCards(Match before, Match after) {
		return (int) after.log().subList(before.log().size(), after.log().size()).stream()
			.filter(event -> event.side() == SIDE
					&& (event instanceof MatchEvent.CardDrawn || event instanceof MatchEvent.CardBurned))
			.count();
	}

	private static int budgetGain(Match before, Match after, CardInstance card) {
		MatchSide own = before.side(SIDE);
		MatchSide next = after.side(SIDE);
		return Math.max(0, Math.max(next.budgetMax() - own.budgetMax(),
				next.budgetCurrent() + card.card().cost() - own.budgetCurrent()));
	}

	private static double max(double... values) {
		double result = Double.NEGATIVE_INFINITY;
		for (double value : values) {
			result = Math.max(result, value);
		}
		return result;
	}

}
