package com.example.itcardbrawl.game;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.SplittableRandom;
import java.util.UUID;
import java.util.random.RandomGenerator;

import com.example.itcardbrawl.game.Match.Side;
import com.example.itcardbrawl.game.Match.Status;
import com.example.itcardbrawl.game.MatchEvent.TurnEnded;
import com.example.itcardbrawl.game.MatchEvent.TurnStarted;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import tools.jackson.databind.json.JsonMapper;

class MatchServiceTests {

	private static final CardCatalog CATALOG = CardCatalogLoader.load(JsonMapper.builder().build(),
			new ClassPathResource(CardCatalogLoader.CATALOG_LOCATION));

	private static final String KEY = "github:gracz";

	private static final Instant NOW = Instant.parse("2026-01-01T12:00:00Z");

	private final MatchStore store = new MatchStore();

	private final MutableClock clock = new MutableClock(NOW);

	// Shuffling from the seed, while the choice of who starts (nextBoolean) is fixed by the test.
	private static RandomGenerator random(long seed, boolean playerStarts) {
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

	private MatchService service(boolean playerStarts) {
		MatchEngine engine = new MatchEngine();
		return new MatchService(new MatchFactory(CATALOG, random(11, playerStarts)), engine,
				new ComputerOpponent(engine, new PositionEvaluator(), new SplittableRandom(13)), store, clock);
	}

	@Test
	void startWithComputerFirstPlaysItsTurn() {
		Match match = service(false).start(KEY, "developers").match();

		assertThat(match.status()).isEqualTo(Status.IN_PROGRESS);
		assertThat(match.activeSide()).isEqualTo(Side.PLAYER);
		assertThat(match.turnNumber()).isEqualTo(2);
		assertThat(match.log()).contains(new TurnEnded(Side.OPPONENT), new TurnStarted(Side.PLAYER, 2));
		assertThat(store.findCurrent(KEY)).containsSame(match);
	}

	@Test
	void startWithPlayerFirstLeavesTurnOne() {
		Match match = service(true).start(KEY, "developers").match();

		assertThat(match.activeSide()).isEqualTo(Side.PLAYER);
		assertThat(match.turnNumber()).isEqualTo(1);
		assertThat(store.findCurrent(KEY)).containsSame(match);
	}

	@Test
	void staleTurnIsRejectedWithoutChangingState() {
		MatchService service = service(true);
		Match match = service.start(KEY, "developers").match();

		assertThatThrownBy(() -> service.move(KEY, match.id(), match.turnNumber() + 1, new Move.EndTurn()))
			.isInstanceOf(StaleMoveException.class);

		assertThat(service.current(KEY).map(MatchOutcome::match)).containsSame(match);
	}

	@Test
	void foreignMatchIdIsRejectedWithoutChangingState() {
		MatchService service = service(true);
		Match match = service.start(KEY, "developers").match();

		assertThatThrownBy(() -> service.move(KEY, UUID.randomUUID(), match.turnNumber(), new Move.EndTurn()))
			.isInstanceOf(StaleMoveException.class);

		assertThat(service.current(KEY).map(MatchOutcome::match)).containsSame(match);
	}

	@Test
	void illegalMoveIsRejectedWithoutChangingState() {
		MatchService service = service(true);
		Match match = service.start(KEY, "developers").match();

		assertThatThrownBy(() -> service.move(KEY, match.id(), match.turnNumber(), new Move.PlayCard(-1)))
			.isInstanceOf(IllegalMoveException.class);

		assertThat(service.current(KEY).map(MatchOutcome::match)).containsSame(match);
	}

	@Test
	void endTurnRunsComputerTurn() {
		MatchService service = service(true);
		Match match = service.start(KEY, "developers").match();

		Match after = service.move(KEY, match.id(), 1, new Move.EndTurn()).orElseThrow().match();

		assertThat(after.status()).isEqualTo(Status.IN_PROGRESS);
		assertThat(after.activeSide()).isEqualTo(Side.PLAYER);
		assertThat(after.turnNumber()).isEqualTo(3);
		assertThat(after.log()).contains(new TurnEnded(Side.PLAYER), new TurnStarted(Side.OPPONENT, 2),
				new TurnEnded(Side.OPPONENT), new TurnStarted(Side.PLAYER, 3));
		assertThat(service.current(KEY).map(MatchOutcome::match)).containsSame(after);
	}

	@Test
	void doubleEndTurnWithSameTurnIsStaleTheSecondTime() {
		MatchService service = service(true);
		Match match = service.start(KEY, "developers").match();
		Match afterFirst = service.move(KEY, match.id(), 1, new Move.EndTurn()).orElseThrow().match();

		assertThatThrownBy(() -> service.move(KEY, match.id(), 1, new Move.EndTurn()))
			.isInstanceOf(StaleMoveException.class);

		assertThat(service.current(KEY).map(MatchOutcome::match)).containsSame(afterFirst);
	}

	@Test
	void startWithComputerFirstReturnsReplayFromStartingState() {
		MatchOutcome outcome = service(false).start(KEY, "developers");

		assertThat(outcome.replay()).hasSizeGreaterThanOrEqualTo(2);
		Match first = outcome.replay().getFirst();
		assertThat(first.turnNumber()).isEqualTo(1);
		assertThat(first.activeSide()).isEqualTo(Side.OPPONENT);
		assertThat(outcome.replay().getLast()).isSameAs(outcome.match());
	}

	@Test
	void startWithPlayerFirstHasEmptyReplay() {
		assertThat(service(true).start(KEY, "developers").replay()).isEmpty();
	}

	@Test
	void endTurnReplayStartsAfterPlayerEndsTurnAndEndsWithFinalState() {
		MatchService service = service(true);
		Match match = service.start(KEY, "developers").match();

		MatchOutcome outcome = service.move(KEY, match.id(), 1, new Move.EndTurn()).orElseThrow();

		assertThat(outcome.replay()).hasSizeGreaterThanOrEqualTo(2);
		Match first = outcome.replay().getFirst();
		assertThat(first.activeSide()).isEqualTo(Side.OPPONENT);
		assertThat(first.turnNumber()).isEqualTo(2);
		assertThat(first.log()).contains(new TurnEnded(Side.PLAYER), new TurnStarted(Side.OPPONENT, 2))
			.doesNotContain(new TurnEnded(Side.OPPONENT));
		assertThat(outcome.replay().getLast()).isSameAs(outcome.match());
		assertThat(service.current(KEY).map(MatchOutcome::match)).containsSame(outcome.match());
	}

	@Test
	void playCardHasEmptyReplay() {
		MatchService service = service(true);
		Match match = service.start(KEY, "developers").match();
		// The budget grows every turn - we end turns until the player can play a card.
		while (playCard(match) == null) {
			assertThat(match.status()).isEqualTo(Status.IN_PROGRESS);
			match = service.move(KEY, match.id(), match.turnNumber(), new Move.EndTurn()).orElseThrow().match();
		}
		Move.PlayCard play = playCard(match);

		MatchOutcome outcome = service.move(KEY, match.id(), match.turnNumber(), play).orElseThrow();

		assertThat(outcome.replay()).isEmpty();
		assertThat(outcome.match().player().hand()).extracting(CardInstance::instanceId)
			.doesNotContain(play.instanceId());
	}

	private static Move.PlayCard playCard(Match match) {
		return new MatchEngine().legalMoves(match, Side.PLAYER).stream()
			.filter(Move.PlayCard.class::isInstance)
			.map(Move.PlayCard.class::cast)
			.findFirst()
			.orElse(null);
	}

	@Test
	void moveWithoutMatchIsEmpty() {
		assertThat(service(true).move(KEY, UUID.randomUUID(), 1, new Move.EndTurn())).isEmpty();
	}

	// --- Turn time limit ---

	private static Instant deadlineAfter(int replaySteps) {
		return NOW.plus(MatchRules.TURN_LIMIT)
			.plus(MatchRules.REPLAY_STEP.multipliedBy(replaySteps))
			.plus(MatchRules.TURN_GRACE);
	}

	@Test
	void startWithPlayerFirstSetsDeadlineWithoutReplay() {
		MatchOutcome outcome = service(true).start(KEY, "developers");

		assertThat(outcome.playerTurnDeadline()).isEqualTo(deadlineAfter(0));
		assertThat(store.find(KEY).orElseThrow().playerTurnDeadline()).isEqualTo(deadlineAfter(0));
	}

	@Test
	void deadlineAccountsForReplaySteps() {
		MatchOutcome started = service(false).start(KEY, "developers");

		assertThat(started.replay()).hasSizeGreaterThanOrEqualTo(2);
		assertThat(started.playerTurnDeadline()).isEqualTo(deadlineAfter(started.replay().size()));

		MatchService service = service(true);
		Match match = service.start(KEY, "developers").match();
		MatchOutcome ended = service.move(KEY, match.id(), 1, new Move.EndTurn()).orElseThrow();
		assertThat(ended.playerTurnDeadline()).isEqualTo(deadlineAfter(ended.replay().size()));
	}

	@Test
	void moveWithinTheTurnKeepsTheDeadline() {
		MatchService service = service(true);
		Match match = service.start(KEY, "developers").match();
		while (playCard(match) == null) {
			match = service.move(KEY, match.id(), match.turnNumber(), new Move.EndTurn()).orElseThrow().match();
		}
		Instant deadline = store.find(KEY).orElseThrow().playerTurnDeadline();

		MatchOutcome outcome = service.move(KEY, match.id(), match.turnNumber(), playCard(match)).orElseThrow();

		assertThat(outcome.playerTurnDeadline()).isEqualTo(deadline);
	}

	@Test
	void currentBeforeDeadlineChangesNothing() {
		MatchService service = service(true);
		Match match = service.start(KEY, "developers").match();
		clock.advance(MatchRules.TURN_LIMIT.plus(MatchRules.TURN_GRACE));

		MatchOutcome outcome = service.current(KEY).orElseThrow();

		assertThat(outcome.match()).isSameAs(match);
		assertThat(outcome.replay()).isEmpty();
		assertThat(outcome.playerTurnDeadline()).isEqualTo(deadlineAfter(0));
	}

	@Test
	void currentAfterDeadlineEndsThePlayerTurnWithComputerTurn() {
		MatchService service = service(true);
		service.start(KEY, "developers");
		clock.advance(MatchRules.TURN_LIMIT.plus(MatchRules.TURN_GRACE).plusMillis(1));

		MatchOutcome outcome = service.current(KEY).orElseThrow();

		Match after = outcome.match();
		assertThat(after.turnNumber()).isEqualTo(3);
		assertThat(after.activeSide()).isEqualTo(Side.PLAYER);
		assertThat(after.log()).contains(new TurnEnded(Side.PLAYER), new TurnStarted(Side.OPPONENT, 2),
				new TurnEnded(Side.OPPONENT), new TurnStarted(Side.PLAYER, 3));
		assertThat(outcome.replay()).hasSizeGreaterThanOrEqualTo(2);
		assertThat(outcome.replay().getLast()).isSameAs(after);
		assertThat(outcome.playerTurnDeadline())
			.isEqualTo(clock.instant().plus(MatchRules.TURN_LIMIT)
				.plus(MatchRules.REPLAY_STEP.multipliedBy(outcome.replay().size()))
				.plus(MatchRules.TURN_GRACE));
		assertThat(store.findCurrent(KEY)).containsSame(after);

		// A second read within the new deadline has nothing more to replay.
		assertThat(service.current(KEY).orElseThrow().replay()).isEmpty();
	}

	@Test
	void moveAfterDeadlineEndsTheTurnAndIsStale() {
		MatchService service = service(true);
		Match match = service.start(KEY, "developers").match();
		clock.advance(Duration.ofMinutes(5));

		assertThatThrownBy(() -> service.move(KEY, match.id(), 1, new Move.EndTurn()))
			.isInstanceOf(StaleMoveException.class);

		Match stored = store.findCurrent(KEY).orElseThrow();
		assertThat(stored.turnNumber()).isEqualTo(3);
		assertThat(stored.activeSide()).isEqualTo(Side.PLAYER);
		assertThat(stored.log()).containsOnlyOnce(new TurnEnded(Side.PLAYER));
	}

	@Test
	void finishedMatchHasNoDeadline() {
		MatchService service = service(true);
		Match match = service.start(KEY, "developers").match();
		store.update(KEY, entry -> entry.withMatch(new Match(match.id(), match.turnNumber(), match.activeSide(),
				match.player(), match.opponent(), Status.FINISHED, new MatchResult(Side.PLAYER,
						MatchResult.EndReason.HP_DEPLETED), match.log())));
		clock.advance(Duration.ofMinutes(5));

		Match current = service.current(KEY).orElseThrow().match();

		assertThat(current.status()).isEqualTo(Status.FINISHED);
		assertThat(current.turnNumber()).isEqualTo(1);
	}

}
