package com.example.itcardbrawl.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;
import java.util.UUID;
import java.util.stream.IntStream;

import com.example.itcardbrawl.game.AbilityKeyword;
import com.example.itcardbrawl.game.ActiveEvent;
import com.example.itcardbrawl.game.BoardCharacter;
import com.example.itcardbrawl.game.CardCatalog;
import com.example.itcardbrawl.game.CardCatalogLoader;
import com.example.itcardbrawl.game.CardInstance;
import com.example.itcardbrawl.game.EffectScope;
import com.example.itcardbrawl.game.Faction;
import com.example.itcardbrawl.game.Language;
import com.example.itcardbrawl.game.LocalizedText;
import com.example.itcardbrawl.game.Match;
import com.example.itcardbrawl.game.Match.Side;
import com.example.itcardbrawl.game.Match.Status;
import com.example.itcardbrawl.game.MatchEvent;
import com.example.itcardbrawl.game.MatchResult;
import com.example.itcardbrawl.game.MatchRules;
import com.example.itcardbrawl.game.MatchSide;
import com.example.itcardbrawl.game.MatchEngine;
import com.example.itcardbrawl.game.Move;
import com.example.itcardbrawl.game.ComputerOpponent;
import com.example.itcardbrawl.game.MatchFactory;
import com.example.itcardbrawl.game.MoveReason;
import com.example.itcardbrawl.game.PositionEvaluator;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import tools.jackson.databind.json.JsonMapper;

class MatchViewTests {

	private static final CardCatalog CATALOG = CardCatalogLoader.load(JsonMapper.builder().build(),
			new ClassPathResource(CardCatalogLoader.CATALOG_LOCATION));

	private static final Faction FACTION = new Faction("test", new LocalizedText("Test", "Test"),
			new LocalizedText("Test", "Test"), true);

	private static final UUID ID = UUID.randomUUID();

	private final MatchLogFormatter formatter = new MatchLogFormatter();

	@Test
	void shortLogIsSentWholeWithUnchangedLogSize() {
		Match step = match(5);
		Match last = match(8);

		MatchView view = MatchView.of(last, List.of(step, last), CATALOG, formatter, Language.PL, List.of());

		assertThat(view.log()).hasSize(8);
		assertThat(view.replay()).extracting(MatchView.ReplayStepView::logSize).containsExactly(5, 8);
	}

	@Test
	void longLogIsCappedToLastEntriesAndStepLogSizeIsShifted() {
		int total = MatchView.LOG_LIMIT + 30;
		Match step = match(total - 5);
		Match last = match(total);

		MatchView view = MatchView.of(last, List.of(step, last), CATALOG, formatter, Language.EN, List.of());

		assertThat(view.log()).hasSize(MatchView.LOG_LIMIT);
		// The first entry sent is TurnStarted for turn 31 — the 30 oldest entries were skipped.
		assertThat(view.log().getFirst().text()).contains("31");
		assertThat(view.replay()).extracting(MatchView.ReplayStepView::logSize)
			.containsExactly(MatchView.LOG_LIMIT - 5, MatchView.LOG_LIMIT);
	}

	@Test
	void stepOlderThanSentLogHasLogSizeZero() {
		Match old = match(10);
		Match last = match(MatchView.LOG_LIMIT + 50);

		MatchView view = MatchView.of(last, List.of(old, last), CATALOG, formatter, Language.PL, List.of());

		assertThat(view.replay().getFirst().logSize()).isZero();
	}

	@Test
	void everyComputerReplayStepLogSizeCoversItsReason() {
		MatchEngine engine = new MatchEngine();
		for (long seed = 0; seed < 10; seed++) {
			Match start = new MatchFactory(CATALOG, new SplittableRandom(seed)).create("developers");
			if (start.activeSide() == Side.PLAYER) {
				start = engine.apply(start, Side.PLAYER, new Move.EndTurn());
			}
			List<Match> replay = new ArrayList<>();
			replay.add(start);
			replay.addAll(new ComputerOpponent(engine, new PositionEvaluator(), new SplittableRandom(seed)).playTurnSteps(start));

			MatchView view = MatchView.of(replay.getLast(), replay, CATALOG, formatter, Language.PL, List.of());

			List<Integer> logSizes = view.replay().stream().map(MatchView.ReplayStepView::logSize).toList();
			for (int i = 1; i < logSizes.size(); i++) {
				List<MatchView.LogEntryView> stepEntries = view.log().subList(logSizes.get(i - 1), logSizes.get(i));
				assertThat(stepEntries).as("ziarno %d, krok %d", seed, i).isNotEmpty();
				assertThat(stepEntries.getFirst().reason()).isTrue();
				assertThat(stepEntries.getFirst().text()).startsWith("Przeciwnik: ");
				assertThat(stepEntries.subList(1, stepEntries.size())).noneMatch(MatchView.LogEntryView::reason);
				assertThat(view.replay().get(i).reason()).as("ziarno %d, krok %d", seed, i)
					.isEqualTo(stepEntries.getFirst().text());
			}
		}
	}

	@Test
	void firstReplayStepHasNoReasonEvenWhenItsLogEndsWithAnEarlierOne() {
		MatchEngine engine = new MatchEngine();
		Match start = new MatchFactory(CATALOG, new SplittableRandom(3)).create("developers");
		ComputerOpponent opponent = new ComputerOpponent(engine, new PositionEvaluator(), new SplittableRandom(3));
		if (start.activeSide() == Side.PLAYER) {
			start = engine.apply(start, Side.PLAYER, new Move.EndTurn());
		}
		// A full computer turn, then the end of the player's turn: the starting step's log contains the previous
		// turn's reasons.
		Match playerTurn = opponent.playTurn(start);
		Match secondTurn = engine.apply(playerTurn, Side.PLAYER, new Move.EndTurn());
		assertThat(secondTurn.log()).anyMatch(MatchEvent.MoveExplained.class::isInstance);
		List<Match> replay = new ArrayList<>();
		replay.add(secondTurn);
		replay.addAll(opponent.playTurnSteps(secondTurn));

		MatchView view = MatchView.of(replay.getLast(), replay, CATALOG, formatter, Language.PL, List.of());

		assertThat(view.replay().getFirst().reason()).isNull();
		assertThat(view.replay().subList(1, view.replay().size()))
			.allSatisfy(step -> assertThat(step.reason()).startsWith("Przeciwnik: "));
	}

	@Test
	void onlyMoveReasonsAreFlaggedAndSerializedAsReason() {
		CardInstance attacker = card(70, "dev-tech-lead");
		Match match = withLog(matchWithSides(List.of(), List.of(), List.of(), List.of(), List.of(), List.of()),
				List.of(new MatchEvent.TurnStarted(Side.OPPONENT, 2),
						new MatchEvent.MoveExplained(Side.OPPONENT, MoveReason.PRESSURE, attacker, null, 8),
						new MatchEvent.Attacked(Side.OPPONENT, attacker, null, 8, 0)));

		MatchView view = MatchView.of(match, List.of(), CATALOG, formatter, Language.EN, List.of());
		DocumentContext json = JsonPath.parse(JsonMapper.builder().build().writeValueAsString(view));

		assertThat(view.log()).extracting(MatchView.LogEntryView::reason).containsExactly(false, true, false);
		List<Boolean> reasons = json.read("$.log[*].reason");
		assertThat(reasons).containsExactly(false, true, false);
		Map<String, Object> entry = json.read("$.log[1]");
		assertThat(entry).containsOnlyKeys("side", "text", "reason");
		assertThat((String) json.read("$.log[1].text")).isEqualTo("Opponent: pressures your HP — Tech Lead deals you 8 damage");
	}

	@Test
	void handTargetsAndPlayableMatchEngineLegalMoves() {
		CardInstance event = card(20, "tst-edge-case");
		BoardCharacter ownCharacter = character(30, "tst-manual");
		BoardCharacter enemyCharacter = character(31, "dev-tech-lead");
		Match match = matchWithSides(List.of(event), List.of(ownCharacter), List.of(enemyCharacter), List.of(),
				List.of(), List.of());
		List<Move> legalMoves = new MatchEngine().legalMoves(match, Side.PLAYER);

		MatchView view = MatchView.of(match, List.of(), CATALOG, formatter, Language.PL, legalMoves);
		MatchView.HandCardView handCard = view.player().hand().getFirst();

		assertThat(handCard.requiresTarget()).isTrue();
		assertThat(handCard.playable()).isTrue();
		assertThat(handCard.effects()).singleElement().satisfies(effect -> {
			assertThat(effect.kind()).isEqualTo(com.example.itcardbrawl.game.EffectKind.DAMAGE);
			assertThat(effect.scope()).isEqualTo(EffectScope.TARGET);
			assertThat(effect.text()).contains("Zadaj");
		});
		assertThat(handCard.targets()).containsExactlyInAnyOrder(
				new MatchView.TargetView("CHARACTER", 30, null), new MatchView.TargetView("CHARACTER", 31, null),
				new MatchView.TargetView("PLAYER", null, Side.PLAYER),
				new MatchView.TargetView("PLAYER", null, Side.OPPONENT));
		assertThat(view.player().board().getFirst().canAttack()).isTrue();
		assertThat(view.player().board().getFirst().attackTargets()).containsExactlyInAnyOrder(
				new MatchView.TargetView("CHARACTER", 31, null),
				new MatchView.TargetView("PLAYER", null, Side.OPPONENT));
	}

	@Test
	void boardShowsEffectiveStatsTrapAndAttackTargets() {
		CardInstance ownAura = card(40, "neu-requirements-change");
		CardInstance enemyTrap = card(41, "tst-flaky-test");
		BoardCharacter ownCharacter = character(42, "dev-tech-lead");
		BoardCharacter enemyCharacter = character(43, "tst-manual");
		Match match = matchWithSides(List.of(), List.of(ownCharacter), List.of(enemyCharacter),
				List.of(new ActiveEvent(ownAura, 2)), List.of(new ActiveEvent(enemyTrap, 2, 42)), List.of());
		List<Move> legalMoves = new MatchEngine().legalMoves(match, Side.PLAYER);

		MatchView view = MatchView.of(match, List.of(), CATALOG, formatter, Language.PL, legalMoves);
		MatchView.BoardCharacterView ownView = view.player().board().getFirst();
		MatchView.BoardCharacterView enemyView = view.opponent().board().getFirst();

		assertThat(ownView.attack()).isEqualTo(7);
		assertThat(ownView.baseAttack()).isEqualTo(7);
		assertThat(ownView.health()).isEqualTo(8);
		assertThat(ownView.keywords()).contains(AbilityKeyword.GUARD);
		assertThat(ownView.trapped()).isTrue();
		assertThat(ownView.canAttack()).isFalse();
		assertThat(ownView.attackTargets()).isEmpty();
		assertThat(enemyView.attackTargets()).isEmpty();
		assertThat(view.player().activeEvents().getFirst().effects()).isNotEmpty();
		assertThat(view.opponent().activeEvents().getFirst().targetName()).isEqualTo("Tech Lead");
	}

	@Test
	void boardShowsAuraModifiedStatsDifferentFromBaseStats() {
		CardInstance ownHealthAura = card(60, "tst-regression");
		CardInstance enemyAttackAura = card(61, "dev-legacy-refactor");
		BoardCharacter ownCharacter = character(62, "dev-tech-lead");
		Match match = matchWithSides(List.of(), List.of(ownCharacter), List.of(), List.of(new ActiveEvent(ownHealthAura, 2)),
				List.of(new ActiveEvent(enemyAttackAura, 2)), List.of());

		MatchView view = MatchView.of(match, List.of(), CATALOG, formatter, Language.PL, List.of());
		MatchView.BoardCharacterView ownView = view.player().board().getFirst();

		// Attack −2 from the opponent's event, HP +2 from its own.
		assertThat(ownView.baseAttack()).isEqualTo(7);
		assertThat(ownView.attack()).isEqualTo(5);
		assertThat(ownView.baseHealth()).isEqualTo(8);
		assertThat(ownView.health()).isEqualTo(10);
	}

	@Test
	void lastEventIsLocalizedForBothSidesAndReplaySteps() {
		CardInstance playerEvent = card(50, "neu-daily-standup");
		CardInstance opponentEvent = card(51, "neu-coffee");
		Match initial = matchWithSides(List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
		Match afterPlayerEvent = withLog(initial, List.of(new MatchEvent.CardPlayed(Side.PLAYER, playerEvent)));
		Match finalMatch = withLog(afterPlayerEvent,
				List.of(new MatchEvent.CardPlayed(Side.PLAYER, playerEvent),
						new MatchEvent.CardPlayed(Side.OPPONENT, opponentEvent)));

		MatchView polish = MatchView.of(finalMatch, List.of(afterPlayerEvent, finalMatch), CATALOG, formatter,
				Language.PL, List.of());
		MatchView english = MatchView.of(finalMatch, List.of(afterPlayerEvent, finalMatch), CATALOG, formatter,
				Language.EN, List.of());

		assertThat(polish.player().lastEvent().name()).isEqualTo("Daily standup");
		assertThat(polish.player().lastEvent().effects().getFirst().text()).contains("przeciwnikowi");
		assertThat(polish.opponent().lastEvent().name()).isEqualTo("Kawa z automatu");
		assertThat(polish.replay().getFirst().opponent().lastEvent()).isNull();
		assertThat(polish.replay().getLast().opponent().lastEvent().name()).isEqualTo("Kawa z automatu");
		assertThat(english.player().lastEvent().name()).isEqualTo("Daily standup");
		assertThat(english.player().lastEvent().effects().getFirst().text()).contains("opponent");
		assertThat(english.opponent().lastEvent().effects().getFirst().text()).contains("budget");
	}

	// The match-end JSON contract: HP below zero visible in current and replay, cause HP_DEPLETED.
	@Test
	void finishedMatchSerializesHpBelowZeroAndHpDepletedReason() {
		MatchSide winner = new MatchSide(FACTION, List.of(), List.of(), MatchRules.MAX_HP, 2, 4, List.of(), List.of(),
				List.of());
		MatchSide loser = new MatchSide(FACTION, List.of(), List.of(), -3, 0, 0, List.of(), List.of(), List.of());
		Match finished = new Match(ID, 3, Side.PLAYER, winner, loser, Status.FINISHED,
				new MatchResult(Side.PLAYER, MatchResult.EndReason.HP_DEPLETED), List.of());

		MatchView view = MatchView.of(finished, List.of(finished), CATALOG, formatter, Language.PL, List.of());
		DocumentContext json = JsonPath.parse(JsonMapper.builder().build().writeValueAsString(view));

		assertThat((String) json.read("$.result.reason")).isEqualTo("HP_DEPLETED");
		assertThat((Integer) json.read("$.player.hp")).isEqualTo(MatchRules.MAX_HP);
		assertThat((Integer) json.read("$.opponent.hp")).isEqualTo(-3);
		assertThat((Integer) json.read("$.opponent.maxHp")).isEqualTo(MatchRules.MAX_HP);
		assertThat((String) json.read("$.replay[0].result.reason")).isEqualTo("HP_DEPLETED");
		assertThat((Integer) json.read("$.replay[0].opponent.hp")).isEqualTo(-3);
		assertThat((Integer) json.read("$.replay[0].player.maxHp")).isEqualTo(MatchRules.MAX_HP);
		// The budget contract: a budget field with current/max; the full list of keys rules out the former name.
		assertThat((Integer) json.read("$.player.budget.current")).isEqualTo(2);
		assertThat((Integer) json.read("$.player.budget.max")).isEqualTo(4);
		Map<String, Object> player = json.read("$.player");
		assertThat(player).containsOnlyKeys("faction", "hp", "maxHp", "budget", "deckCount", "hand", "board",
				"activeEvents", "graveyardCount", "lastEvent");
	}

	// A match whose log consists of consecutive TurnStarted entries (turn 1..logSize), sides without cards.
	private static Match match(int logSize) {
		MatchSide side = new MatchSide(FACTION, List.of(), List.of(), MatchRules.MAX_HP, 0, 0, List.of(), List.of(), List.of());
		List<MatchEvent> log = IntStream.rangeClosed(1, logSize)
			.<MatchEvent>mapToObj(turn -> new MatchEvent.TurnStarted(Side.PLAYER, turn))
			.toList();
		return new Match(ID, logSize, Side.PLAYER, side, side, Status.IN_PROGRESS, null, log);
	}

	private static MatchSide side(List<CardInstance> hand, List<BoardCharacter> board, List<ActiveEvent> activeEvents) {
		return new MatchSide(FACTION, List.of(), hand, MatchRules.MAX_HP, MatchRules.BUDGET_CAP, MatchRules.BUDGET_CAP, board, List.of(), activeEvents);
	}

	private static Match matchWithSides(List<CardInstance> hand, List<BoardCharacter> playerBoard,
			List<BoardCharacter> opponentBoard, List<ActiveEvent> playerEvents, List<ActiveEvent> opponentEvents,
			List<MatchEvent> log) {
		return new Match(ID, 2, Side.PLAYER, side(hand, playerBoard, playerEvents),
				side(List.of(), opponentBoard, opponentEvents), Status.IN_PROGRESS, null, log);
	}

	private static Match withLog(Match match, List<MatchEvent> log) {
		return new Match(match.id(), match.turnNumber(), match.activeSide(), match.player(), match.opponent(),
				match.status(), match.result(), log);
	}

	private static CardInstance card(int instanceId, String cardId) {
		return new CardInstance(instanceId, CATALOG.card(cardId).orElseThrow());
	}

	private static BoardCharacter character(int instanceId, String cardId) {
		return new BoardCharacter(card(instanceId, cardId), 0, false, false);
	}

}
