package com.example.itcardbrawl.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import com.example.itcardbrawl.game.CardCatalog;
import com.example.itcardbrawl.game.CardCatalogLoader;
import com.example.itcardbrawl.game.Faction;
import com.example.itcardbrawl.game.Language;
import com.example.itcardbrawl.game.LocalizedText;
import com.example.itcardbrawl.game.Match;
import com.example.itcardbrawl.game.Match.Side;
import com.example.itcardbrawl.game.Match.Status;
import com.example.itcardbrawl.game.MatchEvent;
import com.example.itcardbrawl.game.MatchSide;

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

		MatchView view = MatchView.of(last, List.of(step, last), CATALOG, formatter, Language.PL);

		assertThat(view.log()).hasSize(8);
		assertThat(view.replay()).extracting(MatchView.ReplayStepView::logSize).containsExactly(5, 8);
	}

	@Test
	void longLogIsCappedToLastEntriesAndStepLogSizeIsShifted() {
		int total = MatchView.LOG_LIMIT + 30;
		Match step = match(total - 5);
		Match last = match(total);

		MatchView view = MatchView.of(last, List.of(step, last), CATALOG, formatter, Language.EN);

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

		MatchView view = MatchView.of(last, List.of(old, last), CATALOG, formatter, Language.PL);

		assertThat(view.replay().getFirst().logSize()).isZero();
	}

	// Partia z logiem złożonym z kolejnych TurnStarted (tura 1..logSize), strony bez kart.
	private static Match match(int logSize) {
		MatchSide side = new MatchSide(FACTION, List.of(), List.of(), 0, 0, 0, List.of(), List.of(), List.of());
		List<MatchEvent> log = IntStream.rangeClosed(1, logSize)
			.<MatchEvent>mapToObj(turn -> new MatchEvent.TurnStarted(Side.PLAYER, turn))
			.toList();
		return new Match(ID, logSize, Side.PLAYER, side, side, Status.IN_PROGRESS, null, log);
	}

}
