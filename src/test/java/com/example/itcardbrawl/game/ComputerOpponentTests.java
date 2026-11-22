package com.example.itcardbrawl.game;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.SplittableRandom;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.random.RandomGenerator;
import java.util.stream.IntStream;

import com.example.itcardbrawl.game.Match.Side;
import com.example.itcardbrawl.game.Match.Status;
import com.example.itcardbrawl.game.MatchEvent.CardPlayed;
import com.example.itcardbrawl.game.MatchEvent.EffectApplied;
import com.example.itcardbrawl.game.MatchEvent.MoveExplained;
import com.example.itcardbrawl.game.MatchEvent.TurnEnded;
import com.example.itcardbrawl.game.MatchEvent.TurnStarted;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import tools.jackson.databind.json.JsonMapper;

class ComputerOpponentTests {

	private static final CardCatalog CATALOG = CardCatalogLoader.load(JsonMapper.builder().build(),
			new ClassPathResource(CardCatalogLoader.CATALOG_LOCATION));

	private static final MatchEngine ENGINE = new MatchEngine();

	private static final PositionEvaluator EVALUATOR = new PositionEvaluator();

	private static final Faction FACTION = new Faction("test", text("Test"), text("Test"), true);

	// Turn limit for one match in the test: 30-card decks end the match around turn 60 at the latest.
	private static final int MAX_TURNS = 200;

	private static final int SIMULATED_MATCHES = 200;

	// Spare decks of hand-built states get unique instanceIds.
	private static final AtomicInteger NEXT_FILLER_ID = new AtomicInteger(1_000);

	private static final double REQUIRED_WIN_RATE = 0.8;

	// The "no dumb moves" guarantees must not depend on randomness.
	private static final int SEEDS = 20;

	private static ComputerOpponent computer(long seed) {
		return new ComputerOpponent(ENGINE, EVALUATOR, new SplittableRandom(seed));
	}

	@Test
	void beatsRandomPlayerInAtLeastEightyPercentOfSeededMatches() {
		int wins = 0;
		for (long seed = 0; seed < SIMULATED_MATCHES; seed++) {
			String faction = seed % 2 == 0 ? "developers" : "testers";
			boolean playerStarts = (seed / 2) % 2 == 0;
			Match match = new MatchFactory(CATALOG, factoryRandom(seed, playerStarts)).create(faction);
			assertThat(match.activeSide()).isEqualTo(playerStarts ? Side.PLAYER : Side.OPPONENT);
			ComputerOpponent opponent = computer(seed + 1_000);
			RandomPlayer player = new RandomPlayer(ENGINE, new SplittableRandom(seed + 2_000));

			while (match.status() == Status.IN_PROGRESS) {
				assertThat(match.turnNumber()).as("partia z ziarnem %d nie kończy się", seed).isLessThan(MAX_TURNS);
				if (match.activeSide() == Side.OPPONENT) {
					match = opponent.playTurn(match);
					assertThat(match.status() == Status.FINISHED || match.activeSide() == Side.PLAYER).isTrue();
				}
				else {
					match = player.playTurn(match);
				}
			}

			assertThat(match.result()).as("wynik partii z ziarnem %d", seed).isNotNull();
			if (match.result().winner() == Side.OPPONENT) {
				wins++;
			}
		}
		assertThat((double) wins / SIMULATED_MATCHES).as("odsetek wygranych komputera (%d/%d)", wins,
				SIMULATED_MATCHES).isGreaterThanOrEqualTo(REQUIRED_WIN_RATE);
	}

	@Test
	void playsThePlayerSideWhenConstructedForIt() {
		for (long seed = 0; seed < SEEDS; seed++) {
			Match match = new MatchFactory(CATALOG, factoryRandom(seed, true)).create("developers");
			assertThat(match.activeSide()).isEqualTo(Side.PLAYER);
			ComputerOpponent player = new ComputerOpponent(ENGINE, EVALUATOR, new SplittableRandom(seed), Side.PLAYER);

			List<Match> steps = player.playTurnSteps(match);

			assertThat(steps).as("ziarno %d", seed).isNotEmpty();
			Match last = steps.getLast();
			assertThat(last.status() == Status.FINISHED || last.activeSide() == Side.OPPONENT).isTrue();
			assertThat(last.log()).contains(new TurnEnded(Side.PLAYER));
			assertThat(last.log()).doesNotContain(new TurnEnded(Side.OPPONENT));
			List<MoveExplained> reasons = reasons(match, steps);
			assertThat(reasons).as("ziarno %d", seed).isNotEmpty();
			assertThat(reasons).allSatisfy(reason -> assertThat(reason.side()).isEqualTo(Side.PLAYER));
		}
	}

	@Test
	void takesWinningAttackEvenWhenOtherGoodMovesExist() {
		BoardCharacter attacker = ready(character(1, 1, 3, 3));
		CardInstance playable = character(2, 1, 2, 2);
		BoardCharacter defender = ready(character(3, 1, 2, 2));
		Match match = opponentTurn(side(3, List.of(), List.of(defender), 0),
				side(MatchRules.MAX_HP, List.of(playable), List.of(attacker), 3));

		for (long seed = 0; seed < SEEDS; seed++) {
			List<Match> steps = computer(seed).playTurnSteps(match);

			assertThat(steps).as("ziarno %d", seed).hasSize(1);
			assertThat(steps.getFirst().result().winner()).isEqualTo(Side.OPPONENT);
			assertThat(reasons(match, steps)).containsExactly(
					new MoveExplained(Side.OPPONENT, MoveReason.LETHAL, attacker.instance(), null, 3));
		}
	}

	@Test
	void takesWinningEventIntoPlayerHp() {
		CardInstance damage = event(1, 1, new Effect(EffectKind.DAMAGE, EffectScope.TARGET, 5));
		CardInstance playable = character(2, 1, 2, 2);
		Match match = opponentTurn(side(4, List.of(), List.of(ready(character(3, 1, 2, 2))), 0),
				side(MatchRules.MAX_HP, List.of(damage, playable), List.of(), 3));

		for (long seed = 0; seed < SEEDS; seed++) {
			List<Match> steps = computer(seed).playTurnSteps(match);

			assertThat(steps).as("ziarno %d", seed).hasSize(1);
			assertThat(steps.getFirst().result().winner()).isEqualTo(Side.OPPONENT);
			assertThat(steps.getFirst().log()).contains(new CardPlayed(Side.OPPONENT, damage));
			assertThat(reasons(match, steps)).containsExactly(
					new MoveExplained(Side.OPPONENT, MoveReason.LETHAL, damage, null, 5));
		}
	}

	@Test
	void healsItselfButNeverThePlayer() {
		CardInstance heal = event(1, 1, new Effect(EffectKind.HEAL, EffectScope.TARGET, 3));
		Match match = opponentTurn(side(20, List.of(), List.of(), 0), side(20, List.of(heal), List.of(), 1));

		for (long seed = 0; seed < SEEDS; seed++) {
			Match after = computer(seed).playTurn(match);

			assertThat(effects(after)).as("ziarno %d", seed)
				.noneMatch(e -> e.targetPlayer() == Side.PLAYER)
				.anyMatch(e -> e.targetPlayer() == Side.OPPONENT);
			assertThat(after.player().hp()).isEqualTo(20);
			assertThat(after.opponent().hp()).isEqualTo(23);
		}
	}

	@Test
	void neverDamagesOwnCharacterOrItself() {
		CardInstance damage = event(1, 1, new Effect(EffectKind.DAMAGE, EffectScope.TARGET, 2));
		CardInstance own = character(2, 1, 3, 3);
		Match match = opponentTurn(side(MatchRules.MAX_HP, List.of(), List.of(ready(character(3, 1, 2, 2))), 0),
				side(MatchRules.MAX_HP, List.of(damage), List.of(fresh(own)), 1));

		for (long seed = 0; seed < SEEDS; seed++) {
			Match after = computer(seed).playTurn(match);

			assertThat(effects(after)).as("ziarno %d", seed)
				.noneMatch(e -> own.equals(e.targetCharacter()))
				.noneMatch(e -> e.targetPlayer() == Side.OPPONENT);
			assertThat(after.opponent().hp()).isEqualTo(MatchRules.MAX_HP);
			assertThat(after.opponent().board()).extracting(BoardCharacter::instance).contains(own);
		}
	}

	@Test
	void neverTrapsOwnCharacter() {
		CardInstance trap = event(1, 1, 3, new Effect(EffectKind.TRAP, EffectScope.TARGET, 0));
		CardInstance own = character(2, 1, 3, 3);
		CardInstance enemy = character(3, 1, 3, 3);
		Match match = opponentTurn(side(MatchRules.MAX_HP, List.of(), List.of(ready(enemy)), 0),
				side(MatchRules.MAX_HP, List.of(trap), List.of(fresh(own)), 1));

		for (long seed = 0; seed < SEEDS; seed++) {
			Match after = computer(seed).playTurn(match);

			assertThat(effects(after)).as("ziarno %d", seed)
				.noneMatch(e -> own.equals(e.targetCharacter()))
				.anyMatch(e -> enemy.equals(e.targetCharacter()));
		}
	}

	@Test
	void skipsBoardWipeThatMostlyDestroysOwnBoard() {
		CardInstance wipe = event(1, 1, new Effect(EffectKind.DAMAGE, EffectScope.ALL_BOARDS, 4));
		List<BoardCharacter> ownBoard = List.of(fresh(character(2, 1, 3, 3)), fresh(character(3, 1, 3, 3)),
				fresh(character(4, 1, 3, 3)));
		Match match = opponentTurn(side(MatchRules.MAX_HP, List.of(), List.of(ready(character(5, 1, 1, 1))), 0),
				side(MatchRules.MAX_HP, List.of(wipe), ownBoard, 1));

		for (long seed = 0; seed < SEEDS; seed++) {
			Match after = computer(seed).playTurn(match);

			assertThat(after.log()).as("ziarno %d", seed).doesNotContain(new CardPlayed(Side.OPPONENT, wipe));
			assertThat(after.opponent().board()).hasSize(3);
		}
	}

	@Test
	void endsTurnWhenOnlyLegalMovesAreHarmful() {
		CardInstance heal = event(1, 1, new Effect(EffectKind.HEAL, EffectScope.TARGET, 3));
		Match match = opponentTurn(side(20, List.of(), List.of(), 0),
				side(MatchRules.MAX_HP, List.of(heal), List.of(), 1));
		assertThat(ENGINE.legalMoves(match, Side.OPPONENT)).isNotEmpty();

		for (long seed = 0; seed < SEEDS; seed++) {
			List<Match> steps = computer(seed).playTurnSteps(match);

			assertThat(steps).as("ziarno %d", seed).hasSize(1);
			Match after = steps.getFirst();
			assertThat(after.activeSide()).isEqualTo(Side.PLAYER);
			assertThat(after.log()).noneMatch(CardPlayed.class::isInstance);
			assertThat(after.opponent().hand()).containsExactly(heal);
			assertThat(reasons(match, steps)).extracting(MoveExplained::reason).containsExactly(MoveReason.END_NO_GAIN);
		}
	}

	@Test
	void defeatingPlayerCharacterIsExplainedAsRemovingThreat() {
		BoardCharacter attacker = ready(character(1, 1, 3, 5));
		CardInstance guard = guard(2, 2, 2);
		Match match = opponentTurn(side(MatchRules.MAX_HP, List.of(), List.of(ready(guard)), 0),
				side(MatchRules.MAX_HP, List.of(), List.of(attacker), 0));

		for (long seed = 0; seed < SEEDS; seed++) {
			List<Match> steps = computer(seed).playTurnSteps(match);

			assertThat(reasons(match, steps)).as("ziarno %d", seed).containsExactly(
					new MoveExplained(Side.OPPONENT, MoveReason.REMOVE_THREAT, attacker.instance(), guard, 3),
					new MoveExplained(Side.OPPONENT, MoveReason.END_NO_MOVES, null, null, 0));
		}
	}

	@Test
	void hitsLethalThreatThatTwoAttackersFinishTogetherInsteadOfPlayer() {
		// Own HP 9: next turn the 9/4 Overtime character alone is lethal. Neither 2/10 attacker defeats it alone, but
		// together they do - the first hit is a favorable trade that beats 2 points of player HP.
		BoardCharacter first = ready(character(1, 1, 2, 10));
		BoardCharacter second = ready(character(2, 1, 2, 10));
		CardInstance threat = overtime(3, 9, 4);
		Match match = opponentTurn(side(MatchRules.MAX_HP, List.of(), List.of(ready(threat)), 0),
				side(9, List.of(), List.of(first, second), 0));

		for (long seed = 0; seed < SEEDS; seed++) {
			List<Match> steps = computer(seed).playTurnSteps(match);
			List<MoveExplained> reasons = reasons(match, steps);

			assertThat(reasons).as("ziarno %d", seed).hasSize(3);
			assertThat(reasons.get(0).reason()).as("ziarno %d", seed).isEqualTo(MoveReason.FAVORABLE_TRADE);
			assertThat(reasons.get(0).target()).isEqualTo(threat);
			assertThat(reasons.get(1).reason()).as("ziarno %d", seed).isEqualTo(MoveReason.REMOVE_THREAT);
			assertThat(reasons.get(1).target()).isEqualTo(threat);
			assertThat(List.of(reasons.get(0).card(), reasons.get(1).card()))
				.containsExactlyInAnyOrder(first.instance(), second.instance());
			assertThat(steps.getLast().player().hp()).isEqualTo(MatchRules.MAX_HP);
		}
	}

	@Test
	void guardAtLowOwnHpIsExplainedAsDefence() {
		CardInstance guardAll = event(1, 1, 2, new Effect(EffectKind.GUARD, EffectScope.OWN_BOARD, 0));
		CardInstance own = character(2, 1, 1, 6);
		Match match = opponentTurn(side(MatchRules.MAX_HP, List.of(), List.of(ready(character(3, 1, 4, 4))), 0),
				side(PositionEvaluator.OWN_HP_LOW - 6, List.of(guardAll), List.of(fresh(own)), 1));

		for (long seed = 0; seed < SEEDS; seed++) {
			List<Match> steps = computer(seed).playTurnSteps(match);

			assertThat(reasons(match, steps)).as("ziarno %d", seed).first()
				.isEqualTo(new MoveExplained(Side.OPPONENT, MoveReason.DEFEND, guardAll, null, 0));
		}
	}

	@Test
	void everyMoveHasExactlyOneReasonDirectlyBeforeItsEvents() {
		for (long seed = 0; seed < SEEDS; seed++) {
			Match previous = midgame(seed);
			List<Match> steps = computer(seed + 1_000).playTurnSteps(previous);

			for (Match step : steps) {
				List<MatchEvent> added = step.log().subList(previous.log().size(), step.log().size());
				assertThat(added).as("ziarno %d", seed).hasSizeGreaterThanOrEqualTo(2);
				assertThat(added.getFirst()).isInstanceOf(MoveExplained.class);
				assertThat(added).filteredOn(MoveExplained.class::isInstance).hasSize(1);
				assertThat(added.get(1)).isNotInstanceOf(MoveExplained.class);
				MoveExplained reason = (MoveExplained) added.getFirst();
				assertThat(reason.side()).isEqualTo(Side.OPPONENT);
				if (reason.reason() == MoveReason.END_NO_GAIN || reason.reason() == MoveReason.END_NO_MOVES) {
					// End of turn: the reason comes before Overtime and TurnEnded.
					assertThat(added.get(1)).isInstanceOfAny(TurnEnded.class, MatchEvent.AbilityTriggered.class);
					assertThat(reason.card()).isNull();
				}
				else {
					assertThat(added.get(1)).isInstanceOfAny(CardPlayed.class, MatchEvent.Attacked.class);
					assertThat(reason.card()).isNotNull();
				}
				previous = step;
			}
			assertThat(reasons(midgame(seed), steps)).hasSameSizeAs(steps);
		}
	}

	@Test
	void sameMoveAndScoreRegardlessOfHiddenCards() {
		for (long seed = 0; seed < SEEDS; seed++) {
			Match match = midgame(seed);
			Match hidden = withDifferentHiddenCards(match);
			assertThat(hidden.player().hand()).isNotEqualTo(match.player().hand());
			assertThat(hidden.player().deck()).isNotEqualTo(match.player().deck());
			assertThat(hidden.opponent().deck()).isNotEqualTo(match.opponent().deck());

			assertThat(EVALUATOR.score(hidden, Side.OPPONENT)).as("ziarno %d", seed)
				.isEqualTo(EVALUATOR.score(match, Side.OPPONENT));
			assertThat(computer(seed + 5_000).chooseMove(hidden)).as("ziarno %d", seed)
				.isEqualTo(computer(seed + 5_000).chooseMove(match));
		}
	}

	@Test
	void playsAffordableCardsBeforeEndingTurn() {
		CardInstance first = character(1, 1, 1, 1);
		CardInstance second = character(2, 2, 1, 1);
		Match match = opponentTurn(side(List.of(), List.of(filler(100)), 0),
				side(List.of(first, second), List.of(filler(200)), 3));

		Match after = computer(7).playTurn(match);

		assertThat(after.activeSide()).isEqualTo(Side.PLAYER);
		assertThat(after.turnNumber()).isEqualTo(2);
		assertThat(after.opponent().hand()).isEmpty();
		assertThat(after.opponent().board()).extracting(BoardCharacter::instance).containsExactlyInAnyOrder(first, second);
		assertThat(after.log()).contains(new CardPlayed(Side.OPPONENT, first), new CardPlayed(Side.OPPONENT, second),
				new TurnEnded(Side.OPPONENT), new TurnStarted(Side.PLAYER, 2));
	}

	@Test
	void endsTurnWhenNoLegalMovesRemain() {
		Match match = opponentTurn(side(List.of(), List.of(filler(100)), 0),
				side(List.of(character(1, 5, 1, 1)), List.of(), 1));

		Match after = computer(7).playTurn(match);

		assertThat(after.activeSide()).isEqualTo(Side.PLAYER);
		assertThat(after.opponent().hand()).hasSize(1);
		assertThat(after.log()).noneMatch(CardPlayed.class::isInstance);
		assertThat(after.log()).filteredOn(MoveExplained.class::isInstance)
			.containsExactly(new MoveExplained(Side.OPPONENT, MoveReason.END_NO_MOVES, null, null, 0));
	}

	@Test
	void leavesMatchUntouchedOutsideOwnTurn() {
		Match playerTurn = playerTurn();

		assertThat(computer(7).playTurn(playerTurn)).isSameAs(playerTurn);
	}

	@Test
	void playTurnStepsIsEmptyOutsideOwnTurn() {
		assertThat(computer(7).playTurnSteps(playerTurn())).isEmpty();
	}

	@Test
	void lastStepEqualsPlayTurnForSameSeed() {
		for (long seed = 0; seed < SEEDS; seed++) {
			Match match = computerStarts(seed);

			List<Match> steps = computer(seed + 1_000).playTurnSteps(match);
			Match played = computer(seed + 1_000).playTurn(match);

			assertThat(steps).as("kroki z ziarnem %d", seed).isNotEmpty();
			assertThat(steps.getLast()).isEqualTo(played);
		}
	}

	@Test
	void everyStepButTheLastHasOpponentActive() {
		for (long seed = 0; seed < SEEDS; seed++) {
			List<Match> steps = computer(seed + 1_000).playTurnSteps(midgame(seed));

			assertThat(steps.subList(0, steps.size() - 1)).as("kroki z ziarnem %d", seed)
				.allSatisfy(step -> {
					assertThat(step.status()).isEqualTo(Status.IN_PROGRESS);
					assertThat(step.activeSide()).isEqualTo(Side.OPPONENT);
				});
			Match last = steps.getLast();
			assertThat(last.status() == Status.FINISHED || last.activeSide() == Side.PLAYER).isTrue();
		}
	}

	// Shuffling from the seed, while the choice of who starts (nextBoolean) is fixed by the test.
	private static RandomGenerator factoryRandom(long seed, boolean playerStarts) {
		SplittableRandom delegate = new SplittableRandom(seed);
		return new RandomGenerator() {
			@Override
			public long nextLong() {
				return delegate.nextLong();
			}

			@Override
			public boolean nextBoolean() {
				return playerStarts;
			}
		};
	}

	// A match from the catalog in which the computer starts (if the factory draws the player, the player ends turn 1).
	private static Match computerStarts(long seed) {
		Match match = new MatchFactory(CATALOG, new SplittableRandom(seed)).create("developers");
		return match.activeSide() == Side.OPPONENT ? match : ENGINE.apply(match, Side.PLAYER, new Move.EndTurn());
	}

	// A match from the catalog played up to the start of a computer turn from turn 7 on (boards and hands are
	// not empty).
	private static Match midgame(long seed) {
		Match match = new MatchFactory(CATALOG, new SplittableRandom(seed))
			.create(seed % 2 == 0 ? "developers" : "testers");
		ComputerOpponent opponent = computer(seed + 3_000);
		RandomPlayer player = new RandomPlayer(ENGINE, new SplittableRandom(seed + 4_000));
		while (match.activeSide() == Side.PLAYER || match.turnNumber() < 7) {
			match = match.activeSide() == Side.OPPONENT ? opponent.playTurn(match) : player.playTurn(match);
			assertThat(match.status()).as("partia z ziarnem %d skończyła się przed turą 7", seed)
				.isEqualTo(Status.IN_PROGRESS);
		}
		return match;
	}

	// The same match with different player hand contents and both decks in reverse order (card counts unchanged).
	private static Match withDifferentHiddenCards(Match match) {
		MatchSide player = match.player();
		int handSize = player.hand().size();
		List<CardInstance> newHand = new ArrayList<>(player.deck().subList(0, handSize));
		List<CardInstance> newDeck = new ArrayList<>(player.deck().subList(handSize, player.deck().size()));
		newDeck.addAll(player.hand());
		Collections.reverse(newDeck);
		MatchSide opponent = match.opponent();
		List<CardInstance> opponentDeck = new ArrayList<>(opponent.deck());
		Collections.reverse(opponentDeck);
		return new Match(match.id(), match.turnNumber(), match.activeSide(),
				new MatchSide(player.faction(), newDeck, newHand, player.hp(), player.budgetCurrent(),
						player.budgetMax(), player.board(), player.graveyard(), player.activeEvents()),
				new MatchSide(opponent.faction(), opponentDeck, opponent.hand(), opponent.hp(),
						opponent.budgetCurrent(), opponent.budgetMax(), opponent.board(), opponent.graveyard(),
						opponent.activeEvents()),
				match.status(), match.result(), match.log());
	}

	// The computer move reasons appended to the log from the initial state to the last step.
	private static List<MoveExplained> reasons(Match start, List<Match> steps) {
		List<MatchEvent> log = steps.getLast().log();
		return log.subList(start.log().size(), log.size()).stream().filter(MoveExplained.class::isInstance)
			.map(MoveExplained.class::cast).toList();
	}

	private static List<EffectApplied> effects(Match match) {
		return match.log().stream().filter(EffectApplied.class::isInstance).map(EffectApplied.class::cast).toList();
	}

	private static Match playerTurn() {
		Match opponentTurn = opponentTurn(side(List.of(), List.of(), 0),
				side(List.of(character(1, 1, 1, 1)), List.of(), 1));
		return new Match(opponentTurn.id(), 1, Side.PLAYER, opponentTurn.player(), opponentTurn.opponent(),
				Status.IN_PROGRESS, null, List.of(new TurnStarted(Side.PLAYER, 1)));
	}

	// A match on the computer's turn 1.
	private static Match opponentTurn(MatchSide player, MatchSide opponent) {
		return new Match(UUID.randomUUID(), 1, Side.OPPONENT, player, opponent, Status.IN_PROGRESS, null,
				List.of(new TurnStarted(Side.OPPONENT, 1)));
	}

	private static MatchSide side(List<CardInstance> hand, List<CardInstance> deck, int budget) {
		return new MatchSide(FACTION, deck, hand, MatchRules.MAX_HP, budget, budget, List.of(), List.of(), List.of());
	}

	// A side with a spare deck, so that ending the turn does not end the match on an empty deck.
	private static MatchSide side(int hp, List<CardInstance> hand, List<BoardCharacter> board, int budget) {
		return new MatchSide(FACTION, List.of(filler(NEXT_FILLER_ID.getAndAdd(10))), hand, hp, budget, budget, board,
				List.of(), List.of());
	}

	private static BoardCharacter ready(CardInstance instance) {
		return new BoardCharacter(instance, 0, false, false);
	}

	private static BoardCharacter fresh(CardInstance instance) {
		return new BoardCharacter(instance, 0, true, false);
	}

	private static CardInstance character(int instanceId, int cost, int attack, int health) {
		return new CardInstance(instanceId, new Card("char-" + instanceId, FACTION.id(), text("Postać"),
				CardType.CHARACTER, cost, text("Opis"), attack, health, null, null, null));
	}

	private static CardInstance guard(int instanceId, int attack, int health) {
		return new CardInstance(instanceId, new Card("guard-" + instanceId, FACTION.id(), text("Ochroniarz"),
				CardType.CHARACTER, 1, text("Opis"), attack, health, new Ability(AbilityKeyword.GUARD, 0), null, null));
	}

	private static CardInstance overtime(int instanceId, int attack, int health) {
		return new CardInstance(instanceId, new Card("overtime-" + instanceId, FACTION.id(), text("Nadgodziny"),
				CardType.CHARACTER, 1, text("Opis"), attack, health, new Ability(AbilityKeyword.OVERTIME, 0), null,
				null));
	}

	private static CardInstance event(int instanceId, int cost, Effect effect) {
		return event(instanceId, cost, 0, effect);
	}

	private static CardInstance event(int instanceId, int cost, int duration, Effect effect) {
		return new CardInstance(instanceId, new Card("event-" + instanceId, FACTION.id(), text("Wydarzenie"),
				CardType.EVENT, cost, text("Opis"), null, null, null, duration, List.of(effect)));
	}

	private static CardInstance[] filler(int firstId) {
		return IntStream.range(0, 3).mapToObj(i -> character(firstId + i, 1, 1, 1)).toArray(CardInstance[]::new);
	}

	private static LocalizedText text(String value) {
		return new LocalizedText(value, value);
	}

}
