package com.example.itcardbrawl.game;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.SplittableRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.UUID;
import java.util.random.RandomGenerator;
import java.util.stream.IntStream;

import com.example.itcardbrawl.game.Match.Side;
import com.example.itcardbrawl.game.Match.Status;
import com.example.itcardbrawl.game.MatchEvent.CardPlayed;
import com.example.itcardbrawl.game.MatchEvent.TurnEnded;
import com.example.itcardbrawl.game.MatchEvent.TurnStarted;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import tools.jackson.databind.json.JsonMapper;

class RandomOpponentTests {

	private static final CardCatalog CATALOG = CardCatalogLoader.load(JsonMapper.builder().build(),
			new ClassPathResource(CardCatalogLoader.CATALOG_LOCATION));

	private static final MatchEngine ENGINE = new MatchEngine();

	private static final Faction FACTION = new Faction("test", text("Test"), text("Test"), true);

	// Limit tur jednej partii w teście: talie po 25 kart kończą partię najpóźniej w okolicach tury 50.
	private static final int MAX_TURNS = 200;

	@Test
	void fiftyComputerVersusComputerMatchesFinishWithoutErrors() {
		for (long seed = 0; seed < 50; seed++) {
			String faction = seed % 2 == 0 ? "developers" : "testers";
			Match match = new MatchFactory(CATALOG, new SplittableRandom(seed)).create(faction);
			RandomOpponent opponent = new RandomOpponent(ENGINE, new SplittableRandom(seed + 1_000));
			RandomGenerator playerRandom = new SplittableRandom(seed + 2_000);

			int turns = 0;
			while (match.status() == Status.IN_PROGRESS) {
				assertThat(turns++).as("partia z ziarnem %d nie kończy się", seed).isLessThan(MAX_TURNS);
				if (match.activeSide() == Side.OPPONENT) {
					match = opponent.playTurn(match);
					assertThat(match.status() == Status.FINISHED || match.activeSide() == Side.PLAYER).isTrue();
				}
				else {
					match = playRandomPlayerTurn(match, playerRandom);
				}
			}

			assertThat(match.result()).as("wynik partii z ziarnem %d", seed).isNotNull();
		}
	}

	@Test
	void playsAffordableCardsBeforeEndingTurn() {
		CardInstance first = character(1, 1);
		CardInstance second = character(2, 2);
		Match match = opponentTurn(side(List.of(), List.of(filler(100)), 0),
				side(List.of(first, second), List.of(filler(200)), 3));

		Match after = new RandomOpponent(ENGINE, new SplittableRandom(7)).playTurn(match);

		assertThat(after.activeSide()).isEqualTo(Side.PLAYER);
		assertThat(after.turnNumber()).isEqualTo(2);
		assertThat(after.opponent().hand()).isEmpty();
		assertThat(after.opponent().board()).extracting(BoardCharacter::instance).containsExactlyInAnyOrder(first, second);
		assertThat(after.log()).contains(new CardPlayed(Side.OPPONENT, first), new CardPlayed(Side.OPPONENT, second),
				new TurnEnded(Side.OPPONENT), new TurnStarted(Side.PLAYER, 2));
	}

	@Test
	void endsTurnWhenNoLegalMovesRemain() {
		Match match = opponentTurn(side(List.of(), List.of(filler(100)), 0), side(List.of(character(1, 5)), List.of(), 1));

		Match after = new RandomOpponent(ENGINE, new SplittableRandom(7)).playTurn(match);

		assertThat(after.activeSide()).isEqualTo(Side.PLAYER);
		assertThat(after.opponent().hand()).hasSize(1);
		assertThat(after.log()).noneMatch(CardPlayed.class::isInstance);
	}

	@Test
	void choosesActionBeforeChoosingOneOfItsTargets() {
		CardInstance targeted = new CardInstance(1, new Card("event", FACTION.id(), text("Wydarzenie"),
				CardType.EVENT, 1, text("Opis"), null, null, null, 0,
				List.of(new Effect(EffectKind.DAMAGE, EffectScope.TARGET, 1))));
		CardInstance playerCharacter = character(2, 1);
		CardInstance opponentCharacter = character(3, 1);
		MatchSide opponent = new MatchSide(FACTION, List.of(), List.of(targeted), MatchRules.MAX_HP, 1, 1,
				List.of(new BoardCharacter(opponentCharacter, 0, false, false)), List.of(), List.of());
		Match match = opponentTurn(side(List.of(), List.of(playerCharacter), 0), opponent);

		List<Match> steps = new RandomOpponent(ENGINE, new SequenceRandom(1, 0)).playTurnSteps(match);

		assertThat(steps).isNotEmpty();
		assertThat(steps.getFirst().log()).anyMatch(com.example.itcardbrawl.game.MatchEvent.Attacked.class::isInstance)
				.noneMatch(com.example.itcardbrawl.game.MatchEvent.EffectApplied.class::isInstance);
	}

	@Test
	void leavesMatchUntouchedOutsideOwnTurn() {
		Match opponentTurn = opponentTurn(side(List.of(), List.of(), 0), side(List.of(character(1, 1)), List.of(), 1));
		Match playerTurn = new Match(opponentTurn.id(), 1, Side.PLAYER, opponentTurn.player(), opponentTurn.opponent(),
				Status.IN_PROGRESS, null, List.of(new TurnStarted(Side.PLAYER, 1)));

		assertThat(new RandomOpponent(ENGINE, new SplittableRandom(7)).playTurn(playerTurn)).isSameAs(playerTurn);
	}

	@Test
	void lastStepEqualsPlayTurnForSameSeed() {
		for (long seed = 0; seed < 20; seed++) {
			Match match = computerStarts(seed);

			List<Match> steps = new RandomOpponent(ENGINE, new SplittableRandom(seed + 1_000)).playTurnSteps(match);
			Match played = new RandomOpponent(ENGINE, new SplittableRandom(seed + 1_000)).playTurn(match);

			assertThat(steps).as("kroki z ziarnem %d", seed).isNotEmpty();
			assertThat(steps.getLast()).isEqualTo(played);
		}
	}

	@Test
	void everyStepButTheLastHasOpponentActive() {
		for (long seed = 0; seed < 20; seed++) {
			List<Match> steps = new RandomOpponent(ENGINE, new SplittableRandom(seed + 1_000))
				.playTurnSteps(computerStarts(seed));

			assertThat(steps.subList(0, steps.size() - 1)).as("kroki z ziarnem %d", seed)
				.allSatisfy(step -> {
					assertThat(step.status()).isEqualTo(Status.IN_PROGRESS);
					assertThat(step.activeSide()).isEqualTo(Side.OPPONENT);
				});
			Match last = steps.getLast();
			assertThat(last.status() == Status.FINISHED || last.activeSide() == Side.PLAYER).isTrue();
		}
	}

	@Test
	void playTurnStepsIsEmptyOutsideOwnTurn() {
		Match opponentTurn = opponentTurn(side(List.of(), List.of(), 0), side(List.of(character(1, 1)), List.of(), 1));
		Match playerTurn = new Match(opponentTurn.id(), 1, Side.PLAYER, opponentTurn.player(), opponentTurn.opponent(),
				Status.IN_PROGRESS, null, List.of(new TurnStarted(Side.PLAYER, 1)));

		assertThat(new RandomOpponent(ENGINE, new SplittableRandom(7)).playTurnSteps(playerTurn)).isEmpty();
	}

	// Partia z katalogu, w której zaczyna komputer (gdy fabryka wylosuje gracza, gracz kończy turę 1).
	private static Match computerStarts(long seed) {
		Match match = new MatchFactory(CATALOG, new SplittableRandom(seed)).create("developers");
		return match.activeSide() == Side.OPPONENT ? match : ENGINE.apply(match, Side.PLAYER, new Move.EndTurn());
	}

	// Gracz w teście gra tak samo jak komputer: losowe poprawne ruchy, potem koniec tury.
	private static Match playRandomPlayerTurn(Match match, RandomGenerator random) {
		Match current = match;
		while (current.status() == Status.IN_PROGRESS && current.activeSide() == Side.PLAYER) {
			List<Move> legal = ENGINE.legalMoves(current, Side.PLAYER);
			Move move = legal.isEmpty() ? new Move.EndTurn() : legal.get(random.nextInt(legal.size()));
			current = ENGINE.apply(current, Side.PLAYER, move);
		}
		return current;
	}

	// Partia w turze 1 komputera.
	private static Match opponentTurn(MatchSide player, MatchSide opponent) {
		return new Match(UUID.randomUUID(), 1, Side.OPPONENT, player, opponent, Status.IN_PROGRESS, null,
				List.of(new TurnStarted(Side.OPPONENT, 1)));
	}

	private static MatchSide side(List<CardInstance> hand, List<CardInstance> deck, int resource) {
		return new MatchSide(FACTION, deck, hand, MatchRules.MAX_HP, resource, resource, List.of(), List.of(), List.of());
	}

	private static final class SequenceRandom implements RandomGenerator {

		private final int[] values;

		private final AtomicInteger index = new AtomicInteger();

		SequenceRandom(int... values) {
			this.values = values;
		}

		@Override
		public long nextLong() {
			return 0;
		}

		@Override
		public int nextInt(int bound) {
			return Math.floorMod(values[Math.min(index.getAndIncrement(), values.length - 1)], bound);
		}
	}

	private static CardInstance character(int instanceId, int cost) {
		return new CardInstance(instanceId, new Card("char-" + instanceId, FACTION.id(), text("Postać"),
				CardType.CHARACTER, cost, text("Opis"), 1, 1, null, null, null));
	}

	private static CardInstance[] filler(int firstId) {
		return IntStream.range(0, 3).mapToObj(i -> character(firstId + i, 1)).toArray(CardInstance[]::new);
	}

	private static LocalizedText text(String value) {
		return new LocalizedText(value, value);
	}

}
