package com.example.itcardbrawl.game;

import java.util.ArrayList;
import java.util.List;
import java.util.random.RandomGenerator;

import com.example.itcardbrawl.game.Match.Side;
import com.example.itcardbrawl.game.Match.Status;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * A computer player acting through {@link MatchEngine} with a 1-move lookahead: every legal move is simulated and
 * scored by {@link PositionEvaluator}. A move that wins the match is taken immediately; moves that do not improve the
 * score are dropped; among the rest it picks at random with weight {@code exp((score - best) / TEMPERATURE)}. When
 * nothing remains, it ends the turn. Before the events of each move (including end of turn) the computer writes its
 * reason to the log ({@link MatchEvent.MoveExplained}), derived from the move and the score components before/after.
 *
 * <p>In the application the computer plays side {@link Side#OPPONENT}; the balance simulation also runs a second
 * instance for side {@link Side#PLAYER}, so that the same algorithm plays both sides.
 */
@Component
public class ComputerOpponent {

	/** Safety valve: after this many moves in one turn the computer ends it regardless of the available moves. */
	static final int MAX_MOVES_PER_TURN = 100;

	/** Sampling temperature: the lower it is, the more often the best-scoring move is chosen. */
	static final double TEMPERATURE = 1.0;

	/** Minimum score improvement for a move not to count as pointless (guards against floating-point noise). */
	private static final double MIN_GAIN = 1e-9;

	private final MatchEngine engine;

	private final PositionEvaluator evaluator;

	private final RandomGenerator random;

	/** The side this computer plays. */
	private final Side side;

	/** The computer of the application: plays side {@link Side#OPPONENT}. */
	@Autowired
	public ComputerOpponent(MatchEngine engine, PositionEvaluator evaluator, RandomGenerator random) {
		this(engine, evaluator, random, Side.OPPONENT);
	}

	public ComputerOpponent(MatchEngine engine, PositionEvaluator evaluator, RandomGenerator random, Side side) {
		this.engine = engine;
		this.evaluator = evaluator;
		this.random = random;
		this.side = side;
	}

	/**
	 * Plays the computer's turn. Returns the match with the other side active, or finished; when it is not the computer's
	 * turn, returns the match unchanged.
	 */
	public Match playTurn(Match match) {
		List<Match> steps = playTurnSteps(match);
		return steps.isEmpty() ? match : steps.getLast();
	}

	/**
	 * Plays the computer's turn and returns the state after each of its moves (play, attack, end of turn); the last
	 * element is the final state. When it is not the computer's turn, returns an empty list.
	 */
	public List<Match> playTurnSteps(Match match) {
		List<Match> steps = new ArrayList<>();
		Match current = match;
		int moves = 0;
		while (current.status() == Status.IN_PROGRESS && current.activeSide() == side) {
			// The safety valve is explained as having no beneficial move (END_NO_GAIN): in practice it never fires,
			// and a separate reason kind for an unreachable state would only clutter the log.
			Decision decision = moves >= MAX_MOVES_PER_TURN
					? endTurn(current, MoveReason.END_NO_GAIN) : decide(current);
			// The reason sits in the log before the events of its move, so the replay step covers both.
			current = decision.after().withEventAt(current.log().size(), decision.reason());
			steps.add(current);
			moves++;
		}
		return List.copyOf(steps);
	}

	/** Picks the computer's next move in the current state; {@link Move.EndTurn} when no move helps. */
	Move chooseMove(Match current) {
		return decide(current).move();
	}

	/** The chosen move, the state after it (without the reason in the log) and the reason for the move. */
	private record Decision(Move move, Match after, MatchEvent.MoveExplained reason) {
	}

	private Decision decide(Match current) {
		List<Move> legal = engine.legalMoves(current, side);
		if (legal.isEmpty()) {
			return endTurn(current, MoveReason.END_NO_MOVES);
		}
		PositionEvaluator.Score before = evaluator.score(current, side);
		double baseline = before.total();
		List<Move> candidates = new ArrayList<>();
		List<Match> afters = new ArrayList<>();
		List<PositionEvaluator.Score> scores = new ArrayList<>();
		double best = Double.NEGATIVE_INFINITY;
		for (Move move : legal) {
			Match after = engine.apply(current, side, move);
			if (after.status() == Status.FINISHED && after.result() != null && after.result().winner() == side) {
				return new Decision(move, after, explained(MoveReason.LETHAL, actingCard(current, move), null,
						playerHpLoss(current, after)));
			}
			PositionEvaluator.Score score = evaluator.score(after, side);
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
		return new Decision(move, engine.apply(current, side, move), explained(reason, null, null, 0));
	}

	/**
	 * The reason for a move that improves the score (a win and end of turn have their reason set earlier). An attack
	 * follows from its target and outcome; playing a character is board development, unless its on-play ability gives
	 * more; an event — the {@link PositionEvaluator.Score} component with the largest gain.
	 */
	private MatchEvent.MoveExplained reason(Match before, Move move, Match after,
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
					// With low own HP, strengthening the board (e.g. Guard) is defense, not development.
					yield before.side(side).hp() < PositionEvaluator.OWN_HP_LOW
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
	 * {@code boardSwing} is the combined change in the scores of both boards: a trade is favorable when the player's
	 * board loses more than the computer's own board. An attack on a character without such an advantage only
	 * weakens the target.
	 */
	private MatchEvent.MoveExplained attackReason(Match before, Match after, Move.Attack attack,
			CardInstance attacker, double boardSwing) {
		if (attack.targetId() == null) {
			return explained(MoveReason.PRESSURE, attacker, null, playerHpLoss(before, after));
		}
		CardInstance target = boardCharacter(before, attack.targetId());
		int damage = CharacterStats.of(board(before, side, attack.attackerId()), side,
				before.player().activeEvents(), before.opponent().activeEvents()).attack();
		if (boardCharacter(after, attack.targetId()) == null) {
			return explained(MoveReason.REMOVE_THREAT, attacker, target, damage);
		}
		boolean guard = CharacterStats.of(board(before, side.opponent(), attack.targetId()), side.opponent(),
				before.player().activeEvents(), before.opponent().activeEvents()).has(AbilityKeyword.GUARD);
		if (guard) {
			return explained(MoveReason.OPEN_GUARD, attacker, target, damage);
		}
		return boardSwing > MIN_GAIN ? explained(MoveReason.FAVORABLE_TRADE, attacker, target, damage)
				: explained(MoveReason.WEAKEN, attacker, target, 0);
	}

	private MatchEvent.MoveExplained explained(MoveReason reason, CardInstance card, CardInstance target,
			int amount) {
		return new MatchEvent.MoveExplained(side, reason, card, target, amount);
	}

	/** The card being played from the hand or the attacking character; {@code null} for end of turn. */
	private CardInstance actingCard(Match match, Move move) {
		return switch (move) {
			case Move.PlayCard play -> match.side(side).hand().stream()
				.filter(card -> card.instanceId() == play.instanceId()).findFirst().orElseThrow();
			case Move.Attack attack -> board(match, side, attack.attackerId()).instance();
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

	/** The first player character defeated by the move (events appended to the log by this move). */
	private CardInstance firstDefeated(Match before, Match after) {
		return after.log().subList(before.log().size(), after.log().size()).stream()
			.filter(MatchEvent.CharacterDefeated.class::isInstance).map(MatchEvent.CharacterDefeated.class::cast)
			.filter(event -> event.side() == side.opponent()).map(MatchEvent.CharacterDefeated::card).findFirst()
			.orElse(null);
	}

	private int playerHpLoss(Match before, Match after) {
		return Math.max(0, before.side(side.opponent()).hp() - after.side(side.opponent()).hp());
	}

	private int ownHpGain(Match before, Match after) {
		return Math.max(0, after.side(side).hp() - before.side(side).hp());
	}

	// The number of cards drawn (including those burned with a full hand) — without their contents.
	private int drawnCards(Match before, Match after) {
		return (int) after.log().subList(before.log().size(), after.log().size()).stream()
			.filter(event -> event.side() == side
					&& (event instanceof MatchEvent.CardDrawn || event instanceof MatchEvent.CardBurned))
			.count();
	}

	private int budgetGain(Match before, Match after, CardInstance card) {
		MatchSide own = before.side(side);
		MatchSide next = after.side(side);
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
