package com.example.itcardbrawl.game;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

	private final MatchStore store = new MatchStore();

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
				new ComputerOpponent(engine, new PositionEvaluator(), new SplittableRandom(13)), store);
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

		assertThat(service.current(KEY)).containsSame(match);
	}

	@Test
	void foreignMatchIdIsRejectedWithoutChangingState() {
		MatchService service = service(true);
		Match match = service.start(KEY, "developers").match();

		assertThatThrownBy(() -> service.move(KEY, UUID.randomUUID(), match.turnNumber(), new Move.EndTurn()))
			.isInstanceOf(StaleMoveException.class);

		assertThat(service.current(KEY)).containsSame(match);
	}

	@Test
	void illegalMoveIsRejectedWithoutChangingState() {
		MatchService service = service(true);
		Match match = service.start(KEY, "developers").match();

		assertThatThrownBy(() -> service.move(KEY, match.id(), match.turnNumber(), new Move.PlayCard(-1)))
			.isInstanceOf(IllegalMoveException.class);

		assertThat(service.current(KEY)).containsSame(match);
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
		assertThat(service.current(KEY)).containsSame(after);
	}

	@Test
	void doubleEndTurnWithSameTurnIsStaleTheSecondTime() {
		MatchService service = service(true);
		Match match = service.start(KEY, "developers").match();
		Match afterFirst = service.move(KEY, match.id(), 1, new Move.EndTurn()).orElseThrow().match();

		assertThatThrownBy(() -> service.move(KEY, match.id(), 1, new Move.EndTurn()))
			.isInstanceOf(StaleMoveException.class);

		assertThat(service.current(KEY)).containsSame(afterFirst);
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
		assertThat(service.current(KEY)).containsSame(outcome.match());
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

}
