package com.example.itcardbrawl.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import com.example.itcardbrawl.game.AbilityKeyword;
import com.example.itcardbrawl.game.CardCatalog;
import com.example.itcardbrawl.game.CardCatalogLoader;
import com.example.itcardbrawl.game.CardInstance;
import com.example.itcardbrawl.game.Language;
import com.example.itcardbrawl.game.Match.Side;
import com.example.itcardbrawl.game.MatchEvent;
import com.example.itcardbrawl.game.MatchResult;
import com.example.itcardbrawl.game.MatchResult.EndReason;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import tools.jackson.databind.json.JsonMapper;

class MatchLogFormatterTests {

	private static final CardCatalog CATALOG = CardCatalogLoader.load(JsonMapper.builder().build(),
			new ClassPathResource(CardCatalogLoader.CATALOG_LOCATION));

	private static final CardInstance TECH_LEAD = new CardInstance(1, CATALOG.card("dev-tech-lead").orElseThrow());

	private static final CardInstance MANUAL_TESTER = new CardInstance(2, CATALOG.card("tst-manual").orElseThrow());

	private final MatchLogFormatter formatter = new MatchLogFormatter();

	// Każdy typ zdarzenia (i każdy wariant słowa kluczowego oraz wyniku) z perspektywy obu stron.
	private static List<MatchEvent> everyEvent() {
		List<MatchEvent> events = new ArrayList<>();
		for (Side side : Side.values()) {
			events.add(new MatchEvent.TurnStarted(side, 3));
			events.add(new MatchEvent.CardDrawn(side, TECH_LEAD));
			events.add(new MatchEvent.CardBurned(side, TECH_LEAD));
			events.add(new MatchEvent.CardPlayed(side, TECH_LEAD));
			events.add(new MatchEvent.EffectApplied(side, TECH_LEAD,
					new com.example.itcardbrawl.game.Effect(com.example.itcardbrawl.game.EffectKind.DAMAGE,
							com.example.itcardbrawl.game.EffectScope.TARGET, 3), MANUAL_TESTER, null));
			for (AbilityKeyword keyword : AbilityKeyword.values()) {
				events.add(new MatchEvent.AbilityTriggered(side, TECH_LEAD, keyword, keyword.takesAmount() ? 4 : 0));
			}
			events.add(new MatchEvent.Attacked(side, TECH_LEAD, null, 8, 0));
			events.add(new MatchEvent.Attacked(side, TECH_LEAD, MANUAL_TESTER, 8, 2));
			events.add(new MatchEvent.StressChanged(side, side.opponent(), 16, 24));
			events.add(new MatchEvent.CharacterDefeated(side, MANUAL_TESTER));
			events.add(new MatchEvent.EventExpired(side, TECH_LEAD));
			events.add(new MatchEvent.TurnEnded(side));
			for (EndReason reason : EndReason.values()) {
				events.add(new MatchEvent.MatchEnded(side, new MatchResult(side, reason)));
			}
		}
		return events;
	}

	@Test
	void everyEventHasTextInBothLanguages() {
		for (MatchEvent event : everyEvent()) {
			String polish = formatter.format(event, Language.PL);
			String english = formatter.format(event, Language.EN);
			assertThat(polish).as("pl: %s", event).isNotBlank();
			assertThat(english).as("en: %s", event).isNotBlank();
			assertThat(english).as("pl/en: %s", event).isNotEqualTo(polish);
		}
	}

	@Test
	void opponentDrawDoesNotRevealCard() {
		MatchEvent draw = new MatchEvent.CardDrawn(Side.OPPONENT, TECH_LEAD);

		assertThat(formatter.format(draw, Language.PL)).isEqualTo("Przeciwnik dobiera kartę");
		assertThat(formatter.format(draw, Language.EN)).isEqualTo("Opponent draws a card");
	}

	@Test
	void playerDrawShowsCardName() {
		MatchEvent draw = new MatchEvent.CardDrawn(Side.PLAYER, MANUAL_TESTER);

		assertThat(formatter.format(draw, Language.PL)).isEqualTo("Dobierasz: Tester manualny");
		assertThat(formatter.format(draw, Language.EN)).isEqualTo("You draw: Manual Tester");
	}

	@Test
	void sentencesUsePlayerPerspective() {
		MatchEvent played = new MatchEvent.CardPlayed(Side.OPPONENT, TECH_LEAD);
		MatchEvent attacked = new MatchEvent.Attacked(Side.OPPONENT, TECH_LEAD, null, 8, 0);
		MatchEvent stress = new MatchEvent.StressChanged(Side.OPPONENT, Side.PLAYER, 16, 24);

		assertThat(formatter.format(played, Language.PL)).isEqualTo("Przeciwnik zagrywa: Tech Lead");
		assertThat(formatter.format(played, Language.EN)).isEqualTo("Opponent plays: Tech Lead");
		assertThat(formatter.format(attacked, Language.PL)).isEqualTo("Tech Lead atakuje Twój stres: +8");
		assertThat(formatter.format(attacked, Language.EN)).isEqualTo("Tech Lead attacks your stress: +8");
		assertThat(formatter.format(stress, Language.PL)).isEqualTo("Twój stres: 16 → 24/30");
		assertThat(formatter.format(stress, Language.EN)).isEqualTo("Your stress: 16 → 24/30");
	}

	@Test
	void cardNamesComeFromCatalogInRequestedLanguage() {
		MatchEvent played = new MatchEvent.CardPlayed(Side.PLAYER, MANUAL_TESTER);

		assertThat(formatter.format(played, Language.PL)).isEqualTo("Zagrywasz: Tester manualny");
		assertThat(formatter.format(played, Language.EN)).isEqualTo("You play: Manual Tester");
	}

	@Test
	void matchEndTellsPlayerWhetherTheyWon() {
		MatchEvent won = new MatchEvent.MatchEnded(Side.PLAYER, new MatchResult(Side.PLAYER, EndReason.STRESS_LIMIT));
		MatchEvent lost = new MatchEvent.MatchEnded(Side.OPPONENT,
				new MatchResult(Side.OPPONENT, EndReason.EMPTY_DECK));

		assertThat(formatter.format(won, Language.PL)).startsWith("Wygrywasz");
		assertThat(formatter.format(won, Language.EN)).startsWith("You win");
		assertThat(formatter.format(lost, Language.PL)).startsWith("Przegrywasz");
		assertThat(formatter.format(lost, Language.EN)).startsWith("You lose");
	}

}
