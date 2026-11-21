package com.example.itcardbrawl.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
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
import com.example.itcardbrawl.game.MatchSide;
import com.example.itcardbrawl.game.MatchEngine;
import com.example.itcardbrawl.game.Move;

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
		// Pierwszy wysłany wpis to TurnStarted dla tury 31 — 30 najstarszych wpisów pominięto.
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

		assertThat(ownView.attack()).isEqualTo(8);
		assertThat(ownView.baseAttack()).isEqualTo(8);
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

		// Atak −2 od wydarzenia przeciwnika, HP +2 od własnego.
		assertThat(ownView.baseAttack()).isEqualTo(8);
		assertThat(ownView.attack()).isEqualTo(6);
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
		assertThat(english.opponent().lastEvent().effects().getFirst().text()).contains("resource");
	}

	// Partia z logiem złożonym z kolejnych TurnStarted (tura 1..logSize), strony bez kart.
	private static Match match(int logSize) {
		MatchSide side = new MatchSide(FACTION, List.of(), List.of(), 30, 0, 0, List.of(), List.of(), List.of());
		List<MatchEvent> log = IntStream.rangeClosed(1, logSize)
			.<MatchEvent>mapToObj(turn -> new MatchEvent.TurnStarted(Side.PLAYER, turn))
			.toList();
		return new Match(ID, logSize, Side.PLAYER, side, side, Status.IN_PROGRESS, null, log);
	}

	private static MatchSide side(List<CardInstance> hand, List<BoardCharacter> board, List<ActiveEvent> activeEvents) {
		return new MatchSide(FACTION, List.of(), hand, 30, 10, 10, board, List.of(), activeEvents);
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
