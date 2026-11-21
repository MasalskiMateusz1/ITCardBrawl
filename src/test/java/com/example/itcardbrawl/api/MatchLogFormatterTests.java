package com.example.itcardbrawl.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import com.example.itcardbrawl.game.AbilityKeyword;
import com.example.itcardbrawl.game.CardCatalog;
import com.example.itcardbrawl.game.CardCatalogLoader;
import com.example.itcardbrawl.game.CardInstance;
import com.example.itcardbrawl.game.Language;
import com.example.itcardbrawl.game.Match.Side;
import com.example.itcardbrawl.game.MatchEvent;
import com.example.itcardbrawl.game.MatchResult;
import com.example.itcardbrawl.game.MatchResult.EndReason;
import com.example.itcardbrawl.game.MoveReason;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import tools.jackson.databind.json.JsonMapper;

class MatchLogFormatterTests {

	private static final CardCatalog CATALOG = CardCatalogLoader.load(JsonMapper.builder().build(),
			new ClassPathResource(CardCatalogLoader.CATALOG_LOCATION));

	private static final CardInstance TECH_LEAD = new CardInstance(1, CATALOG.card("dev-tech-lead").orElseThrow());

	private static final CardInstance MANUAL_TESTER = new CardInstance(2, CATALOG.card("tst-manual").orElseThrow());

	private final MatchLogFormatter formatter = new MatchLogFormatter();

	// Every event type (and every keyword and outcome variant) from the perspective of both sides.
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
			events.add(new MatchEvent.HpChanged(side, side.opponent(), 24, 16));
			events.add(new MatchEvent.CharacterDefeated(side, MANUAL_TESTER));
			events.add(new MatchEvent.EventExpired(side, TECH_LEAD));
			events.add(new MatchEvent.TurnEnded(side));
			for (EndReason reason : EndReason.values()) {
				events.add(new MatchEvent.MatchEnded(side, new MatchResult(side, reason)));
			}
		}
		events.addAll(everyReason());
		return events;
	}

	// Every kind of computer move reason, with and without a target (card is null only for end of turn).
	private static List<MatchEvent.MoveExplained> everyReason() {
		List<MatchEvent.MoveExplained> reasons = new ArrayList<>();
		for (MoveReason reason : MoveReason.values()) {
			boolean endTurn = reason == MoveReason.END_NO_GAIN || reason == MoveReason.END_NO_MOVES;
			CardInstance card = endTurn ? null : TECH_LEAD;
			reasons.add(new MatchEvent.MoveExplained(Side.OPPONENT, reason, card, endTurn ? null : MANUAL_TESTER, 3));
			reasons.add(new MatchEvent.MoveExplained(Side.OPPONENT, reason, card, null, endTurn ? 0 : 1));
		}
		return reasons;
	}

	@Test
	void everyMoveReasonHasOpponentSentenceNamingOnlyItsOwnCards() {
		List<String> otherNames = Stream.of("developers", "testers").flatMap(faction -> CATALOG.deck(faction).stream())
			.filter(card -> !card.id().equals(TECH_LEAD.card().id()) && !card.id().equals(MANUAL_TESTER.card().id()))
			.flatMap(card -> Stream.of(card.name().in(Language.PL), card.name().in(Language.EN)))
			.distinct().toList();
		for (MatchEvent.MoveExplained reason : everyReason()) {
			String polish = formatter.format(reason, Language.PL);
			String english = formatter.format(reason, Language.EN);
			assertThat(polish).as("pl: %s", reason).startsWith("Przeciwnik: ").hasSizeGreaterThan("Przeciwnik: ".length());
			assertThat(english).as("en: %s", reason).startsWith("Opponent: ").hasSizeGreaterThan("Opponent: ".length());
			for (String name : otherNames) {
				assertThat(polish).as("pl: %s", reason).doesNotContain(name);
				assertThat(english).as("en: %s", reason).doesNotContain(name);
			}
			if (reason.card() != null) {
				assertThat(polish).contains(TECH_LEAD.card().name().in(Language.PL));
				assertThat(english).contains(TECH_LEAD.card().name().in(Language.EN));
			}
		}
	}

	@Test
	void moveReasonSentencesMatchTemplates() {
		MatchEvent lethal = new MatchEvent.MoveExplained(Side.OPPONENT, MoveReason.LETHAL, TECH_LEAD, null, 5);
		MatchEvent lethalOne = new MatchEvent.MoveExplained(Side.OPPONENT, MoveReason.LETHAL, TECH_LEAD, null, 1);
		MatchEvent noGain = new MatchEvent.MoveExplained(Side.OPPONENT, MoveReason.END_NO_GAIN, null, null, 0);
		MatchEvent removes = new MatchEvent.MoveExplained(Side.OPPONENT, MoveReason.REMOVE_THREAT, TECH_LEAD,
				MANUAL_TESTER, 8);

		assertThat(formatter.format(lethal, Language.PL)).isEqualTo("Przeciwnik: kończy partię — Tech Lead zadaje Ci 5 obrażeń");
		assertThat(formatter.format(lethal, Language.EN))
			.isEqualTo("Opponent: finishes the match — Tech Lead deals you 5 damage");
		assertThat(formatter.format(lethalOne, Language.PL)).endsWith("zadaje Ci 1 obrażenie");
		assertThat(formatter.format(noGain, Language.PL))
			.isEqualTo("Przeciwnik: kończy turę — żaden ruch nie poprawia jego sytuacji");
		assertThat(formatter.format(noGain, Language.EN)).isEqualTo("Opponent: ends the turn — no move improves the position");
		assertThat(formatter.format(removes, Language.PL)).isEqualTo("Przeciwnik: Tech Lead usuwa Twoją postać Tester manualny");
		assertThat(formatter.format(removes, Language.EN)).isEqualTo("Opponent: Tech Lead removes your Manual Tester");
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
		MatchEvent hp = new MatchEvent.HpChanged(Side.OPPONENT, Side.PLAYER, 24, 16);
		MatchEvent opponentHp = new MatchEvent.HpChanged(Side.PLAYER, Side.OPPONENT, 2, -3);

		assertThat(formatter.format(played, Language.PL)).isEqualTo("Przeciwnik zagrywa: Tech Lead");
		assertThat(formatter.format(played, Language.EN)).isEqualTo("Opponent plays: Tech Lead");
		assertThat(formatter.format(attacked, Language.PL)).isEqualTo("Tech Lead atakuje Cię i zadaje 8 obrażeń");
		assertThat(formatter.format(attacked, Language.EN)).isEqualTo("Tech Lead attacks you for 8 damage");
		assertThat(formatter.format(hp, Language.PL)).isEqualTo("Twoje HP: 24 → 16/30");
		assertThat(formatter.format(hp, Language.EN)).isEqualTo("Your HP: 24 → 16/30");
		assertThat(formatter.format(opponentHp, Language.PL)).isEqualTo("HP przeciwnika: 2 → −3/30");
		assertThat(formatter.format(opponentHp, Language.EN)).isEqualTo("Opponent HP: 2 → −3/30");
	}

	@Test
	void abilitySentencesSpeakOfDamageAndHealing() {
		MatchEvent damageByPlayer = new MatchEvent.AbilityTriggered(Side.PLAYER, TECH_LEAD, AbilityKeyword.ON_PLAY_DAMAGE, 4);
		MatchEvent overtimeByOpponent = new MatchEvent.AbilityTriggered(Side.OPPONENT, TECH_LEAD, AbilityKeyword.OVERTIME,
				1);
		MatchEvent healByPlayer = new MatchEvent.AbilityTriggered(Side.PLAYER, TECH_LEAD, AbilityKeyword.HEAL, 3);
		MatchEvent healByOpponent = new MatchEvent.AbilityTriggered(Side.OPPONENT, TECH_LEAD, AbilityKeyword.HEAL, 3);
		MatchEvent attackByPlayer = new MatchEvent.Attacked(Side.PLAYER, TECH_LEAD, null, 8, 0);

		assertThat(formatter.format(damageByPlayer, Language.PL)).isEqualTo("Tech Lead zadaje przeciwnikowi 4 obrażenia");
		assertThat(formatter.format(damageByPlayer, Language.EN)).isEqualTo("Tech Lead deals 4 damage to the opponent");
		assertThat(formatter.format(overtimeByOpponent, Language.PL)).isEqualTo("Tech Lead zadaje Ci 1 obrażenie");
		assertThat(formatter.format(overtimeByOpponent, Language.EN)).isEqualTo("Tech Lead deals you 1 damage");
		assertThat(formatter.format(healByPlayer, Language.PL)).isEqualTo("Tech Lead leczy Twoje HP o 3");
		assertThat(formatter.format(healByPlayer, Language.EN)).isEqualTo("Tech Lead heals your HP by 3");
		assertThat(formatter.format(healByOpponent, Language.PL)).isEqualTo("Tech Lead leczy HP przeciwnika o 3");
		assertThat(formatter.format(healByOpponent, Language.EN)).isEqualTo("Tech Lead heals opponent HP by 3");
		assertThat(formatter.format(attackByPlayer, Language.PL)).isEqualTo("Tech Lead atakuje przeciwnika i zadaje 8 obrażeń");
		assertThat(formatter.format(attackByPlayer, Language.EN)).isEqualTo("Tech Lead attacks the opponent for 8 damage");
	}

	@Test
	void effectLineNamesWhoseEventItIs() {
		CardInstance deadline = new CardInstance(3, CATALOG.card("neu-deadline").orElseThrow());
		com.example.itcardbrawl.game.Effect damage = deadline.card().effects().getFirst();
		MatchEvent byOpponent = new MatchEvent.EffectApplied(Side.OPPONENT, deadline, damage, null, Side.PLAYER);
		MatchEvent byPlayer = new MatchEvent.EffectApplied(Side.PLAYER, deadline, damage, null, Side.OPPONENT);

		assertThat(formatter.format(byOpponent, Language.PL)).startsWith("Wydarzenie przeciwnika ")
			.endsWith(" — Twoje HP");
		assertThat(formatter.format(byOpponent, Language.EN)).startsWith("Opponent's event ")
			.endsWith(" — your HP");
		assertThat(formatter.format(byPlayer, Language.PL)).startsWith("Twoje wydarzenie ")
			.endsWith(" — HP przeciwnika");
		assertThat(formatter.format(byPlayer, Language.EN)).startsWith("Your event ")
			.endsWith(" — opponent HP");
	}

	@Test
	void effectWithoutTargetHasNoTargetSuffix() {
		CardInstance stackOverflow = new CardInstance(4, CATALOG.card("dev-stackoverflow").orElseThrow());
		com.example.itcardbrawl.game.Effect draw = stackOverflow.card().effects().getFirst();
		MatchEvent applied = new MatchEvent.EffectApplied(Side.PLAYER, stackOverflow, draw, null, null);

		assertThat(formatter.format(applied, Language.PL)).isEqualTo("Twoje wydarzenie Kopiuj-wklej ze Stack Overflow: "
				+ draw.describe().in(Language.PL)).doesNotContain(" — ");
		assertThat(formatter.format(applied, Language.EN)).isEqualTo("Your event Copy-paste from Stack Overflow: "
				+ draw.describe().in(Language.EN)).doesNotContain(" — ");
	}

	@Test
	void cardNamesComeFromCatalogInRequestedLanguage() {
		MatchEvent played = new MatchEvent.CardPlayed(Side.PLAYER, MANUAL_TESTER);

		assertThat(formatter.format(played, Language.PL)).isEqualTo("Zagrywasz: Tester manualny");
		assertThat(formatter.format(played, Language.EN)).isEqualTo("You play: Manual Tester");
	}

	@Test
	void matchEndTellsPlayerWhetherTheyWon() {
		MatchEvent won = new MatchEvent.MatchEnded(Side.PLAYER, new MatchResult(Side.PLAYER, EndReason.HP_DEPLETED));
		MatchEvent lostOnHp = new MatchEvent.MatchEnded(Side.OPPONENT,
				new MatchResult(Side.OPPONENT, EndReason.HP_DEPLETED));
		MatchEvent lost = new MatchEvent.MatchEnded(Side.OPPONENT,
				new MatchResult(Side.OPPONENT, EndReason.EMPTY_DECK));

		assertThat(formatter.format(won, Language.PL)).isEqualTo("Wygrywasz: HP przeciwnika spadło do 0 lub niżej");
		assertThat(formatter.format(won, Language.EN)).isEqualTo("You win: opponent's HP dropped to 0 or below");
		assertThat(formatter.format(lostOnHp, Language.PL)).isEqualTo("Przegrywasz: Twoje HP spadło do 0 lub niżej");
		assertThat(formatter.format(lostOnHp, Language.EN)).isEqualTo("You lose: your HP dropped to 0 or below");
		assertThat(formatter.format(lost, Language.PL)).startsWith("Przegrywasz");
		assertThat(formatter.format(lost, Language.EN)).startsWith("You lose");
	}

}
