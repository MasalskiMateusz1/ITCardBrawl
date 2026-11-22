package com.example.itcardbrawl.game;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;
import java.util.random.RandomGenerator;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import tools.jackson.databind.json.JsonMapper;

class MatchFactoryTests {

	private static final CardCatalog CATALOG = CardCatalogLoader.load(JsonMapper.builder().build(),
			new ClassPathResource(CardCatalogLoader.CATALOG_LOCATION));

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

	private static Match create(String factionId, long seed, boolean playerStarts) {
		return new MatchFactory(CATALOG, random(seed, playerStarts)).create(factionId);
	}

	@Test
	void bothSidesStartWithFiveCardsFromDeckInHandAndTwentyFiveInDeck() {
		Match match = create("developers", 1, true);

		for (MatchSide side : List.of(match.player(), match.opponent())) {
			assertThat(side.deck()).hasSize(MatchRules.DECK_SIZE - MatchRules.STARTING_HAND);
		}
		assertThat(match.player().hand()).hasSize(MatchRules.STARTING_HAND);
		assertThat(match.opponent().hand()).hasSize(MatchRules.STARTING_HAND + 1);
		assertThat(match.turnNumber()).isEqualTo(1);
		assertThat(match.id()).isNotNull();
	}

	@Test
	void realCatalogConfiguresNeutralSecondPlayerBonus() {
		assertThat(CATALOG.secondPlayerBonus()).get().satisfies(card -> {
			assertThat(card.id()).isEqualTo("neu-rubber-duck");
			assertThat(CATALOG.isNeutral(card)).isTrue();
		});
	}

	@Test
	void nonStartingSideGetsSecondPlayerBonusOnTopOfStartingHand() {
		Card bonus = CATALOG.secondPlayerBonus().orElseThrow();
		for (boolean playerStarts : List.of(true, false)) {
			Match match = create("testers", 5, playerStarts);
			MatchSide starting = playerStarts ? match.player() : match.opponent();
			MatchSide second = playerStarts ? match.opponent() : match.player();

			assertThat(starting.hand()).as("strona zaczynająca").hasSize(MatchRules.STARTING_HAND);
			assertThat(second.hand()).as("strona niezaczynająca").hasSize(MatchRules.STARTING_HAND + 1);
			assertThat(second.hand().getLast().card()).isEqualTo(bonus);
			assertThat(second.hand().getLast().instanceId()).isEqualTo(2 * MatchRules.DECK_SIZE + 1);
			// The bonus comes from outside the deck: the deck keeps all its cards.
			assertThat(second.deck()).hasSize(MatchRules.DECK_SIZE - MatchRules.STARTING_HAND);
			assertThat(allCards(second).toList()).hasSize(MatchRules.DECK_SIZE + 1);
		}
	}

	@Test
	void catalogWithoutSecondPlayerBonusDealsFiveCardsToBothSides() {
		CardCatalog withoutBonus = new CardCatalog(
				List.of(CATALOG.faction("developers").orElseThrow(), CATALOG.faction("testers").orElseThrow(),
						CATALOG.faction("neutral").orElseThrow()),
				Stream.concat(CATALOG.deck("developers").stream(), CATALOG.deck("testers").stream()).distinct().toList(),
				Map.of("developers", entries(CATALOG.deck("developers")), "testers",
						entries(CATALOG.deck("testers"))));

		Match match = new MatchFactory(withoutBonus, random(1, true)).create("developers");

		assertThat(match.player().hand()).hasSize(MatchRules.STARTING_HAND);
		assertThat(match.opponent().hand()).hasSize(MatchRules.STARTING_HAND);
	}

	@Test
	void factoryStartsBothSidesAtMaxHp() {
		for (boolean playerStarts : List.of(true, false)) {
			Match match = create("developers", 1, playerStarts);

			assertThat(match.player().hp()).isEqualTo(MatchRules.MAX_HP).isEqualTo(30);
			assertThat(match.opponent().hp()).isEqualTo(MatchRules.MAX_HP).isEqualTo(30);
		}
	}

	@Test
	void matchStartsInProgressWithEmptyTablesAndTurnStartedLog() {
		Match match = create("developers", 1, false);

		assertThat(match.status()).isEqualTo(Match.Status.IN_PROGRESS);
		assertThat(match.result()).isNull();
		assertThat(match.log()).containsExactly(new MatchEvent.TurnStarted(Match.Side.OPPONENT, 1));
		for (MatchSide side : List.of(match.player(), match.opponent())) {
			assertThat(side.board()).isEmpty();
			assertThat(side.graveyard()).isEmpty();
			assertThat(side.activeEvents()).isEmpty();
		}
	}

	@Test
	void opponentGetsTheOtherPlayableFaction() {
		Match asDevelopers = create("developers", 1, true);
		Match asTesters = create("testers", 1, true);

		assertThat(asDevelopers.player().faction().id()).isEqualTo("developers");
		assertThat(asDevelopers.opponent().faction().id()).isEqualTo("testers");
		assertThat(asTesters.player().faction().id()).isEqualTo("testers");
		assertThat(asTesters.opponent().faction().id()).isEqualTo("developers");
	}

	@Test
	void eachSideUsesItsOwnFactionDeck() {
		Match match = create("developers", 7, true);

		assertThat(allCards(match.player()).toList())
			.allMatch(c -> c.faction().equals("developers") || CATALOG.isNeutral(c));
		assertThat(allCards(match.opponent()).toList())
			.allMatch(c -> c.faction().equals("testers") || CATALOG.isNeutral(c));
	}

	@Test
	void playerStartsWithOneBudgetAndOpponentWithNone() {
		Match match = create("developers", 1, true);

		assertThat(match.activeSide()).isEqualTo(Match.Side.PLAYER);
		assertThat(match.player().budgetCurrent()).isEqualTo(1);
		assertThat(match.player().budgetMax()).isEqualTo(1);
		assertThat(match.opponent().budgetCurrent()).isZero();
		assertThat(match.opponent().budgetMax()).isZero();
	}

	@Test
	void opponentStartsWithOneBudgetAndPlayerWithNone() {
		Match match = create("developers", 1, false);

		assertThat(match.activeSide()).isEqualTo(Match.Side.OPPONENT);
		assertThat(match.opponent().budgetCurrent()).isEqualTo(1);
		assertThat(match.opponent().budgetMax()).isEqualTo(1);
		assertThat(match.player().budgetCurrent()).isZero();
		assertThat(match.player().budgetMax()).isZero();
	}

	@Test
	void instanceIdsAreUniqueWithinMatch() {
		Match match = create("testers", 3, true);

		List<Integer> ids = Stream.concat(instances(match.player()), instances(match.opponent()))
			.map(CardInstance::instanceId)
			.toList();
		assertThat(ids).hasSize(2 * MatchRules.DECK_SIZE + 1).doesNotHaveDuplicates();
	}

	@Test
	void neutralFactionIsRejected() {
		MatchFactory factory = new MatchFactory(CATALOG, random(1, true));

		assertThatIllegalArgumentException().isThrownBy(() -> factory.create("neutral"));
	}

	@Test
	void unknownOrMissingFactionIsRejected() {
		MatchFactory factory = new MatchFactory(CATALOG, random(1, true));

		assertThatIllegalArgumentException().isThrownBy(() -> factory.create("managers"));
		assertThatIllegalArgumentException().isThrownBy(() -> factory.create(null));
	}

	@Test
	void sameSeedDealsSameHands() {
		Match first = create("developers", 42, true);
		Match second = create("developers", 42, true);

		assertThat(first.player().hand()).isEqualTo(second.player().hand());
		assertThat(first.opponent().hand()).isEqualTo(second.opponent().hand());
		assertThat(first.player().deck()).isEqualTo(second.player().deck());
	}

	@Test
	void deckIsShuffled() {
		Match match = create("developers", 42, true);

		List<Card> dealtOrder = allCards(match.player()).toList();
		assertThat(dealtOrder).containsExactlyInAnyOrderElementsOf(CATALOG.deck("developers"));
		assertThat(dealtOrder).isNotEqualTo(CATALOG.deck("developers"));
	}

	@Test
	void instanceIdsFollowDealtOrderNotCatalogOrder() {
		Match match = create("developers", 42, true);

		List<Integer> playerIds = instances(match.player()).map(CardInstance::instanceId).toList();
		// The opponent does not start, so its hand also holds the second player bonus, numbered after both decks.
		List<Integer> opponentIds = instances(match.opponent()).map(CardInstance::instanceId).sorted().toList();
		assertThat(playerIds).isEqualTo(IntStream.rangeClosed(1, MatchRules.DECK_SIZE).boxed().toList());
		assertThat(opponentIds)
			.isEqualTo(IntStream.rangeClosed(MatchRules.DECK_SIZE + 1, 2 * MatchRules.DECK_SIZE + 1).boxed().toList());
	}

	// Collapses an expanded deck back into deck entries (copies per card id).
	private static List<DeckEntry> entries(List<Card> deck) {
		Map<String, Integer> copies = new LinkedHashMap<>();
		deck.forEach(card -> copies.merge(card.id(), 1, Integer::sum));
		return copies.entrySet().stream().map(e -> new DeckEntry(e.getKey(), e.getValue())).toList();
	}

	private static Stream<CardInstance> instances(MatchSide side) {
		return Stream.concat(side.hand().stream(), side.deck().stream());
	}

	private static Stream<Card> allCards(MatchSide side) {
		return instances(side).map(CardInstance::card);
	}

}
