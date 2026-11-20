package com.example.itcardbrawl.api;

import java.util.List;
import java.util.UUID;

import com.example.itcardbrawl.game.Ability;
import com.example.itcardbrawl.game.AbilityKeyword;
import com.example.itcardbrawl.game.ActiveEvent;
import com.example.itcardbrawl.game.BoardCharacter;
import com.example.itcardbrawl.game.Card;
import com.example.itcardbrawl.game.CardCatalog;
import com.example.itcardbrawl.game.CardInstance;
import com.example.itcardbrawl.game.CardType;
import com.example.itcardbrawl.game.Faction;
import com.example.itcardbrawl.game.Language;
import com.example.itcardbrawl.game.Match;
import com.example.itcardbrawl.game.MatchEvent;
import com.example.itcardbrawl.game.MatchResult;
import com.example.itcardbrawl.game.MatchRules;
import com.example.itcardbrawl.game.MatchSide;

/**
 * Widok stołu z perspektywy gracza. Przeciwnik jest widoczny przez stół, aktywne wydarzenia i liczniki —
 * jego ręka i kolejność talii nie opuszczają serwera. Teksty (nazwy frakcji, nazwy i treść kart, log) są
 * w języku żądania; identyfikatory partii i kart od języka nie zależą. {@code result} jest {@code null},
 * dopóki partia trwa.
 */
public record MatchView(UUID matchId, Match.Status status, TurnView turn, PlayerView player, OpponentView opponent,
		ResultView result, List<LogEntryView> log) {

	public static MatchView of(Match match, CardCatalog catalog, MatchLogFormatter formatter, Language language) {
		MatchSide player = match.player();
		MatchSide opponent = match.opponent();
		return new MatchView(match.id(), match.status(),
				new TurnView(match.turnNumber(), match.activeSide()),
				new PlayerView(FactionRef.of(player.faction(), language), player.stress(), MatchRules.STRESS_LIMIT,
						ResourceView.of(player), player.deck().size(),
						player.hand().stream().map(card -> HandCardView.of(card, catalog, language)).toList(),
						BoardCharacterView.listOf(player, language), ActiveEventView.listOf(player, language),
						player.graveyard().size()),
				new OpponentView(FactionRef.of(opponent.faction(), language), opponent.stress(), MatchRules.STRESS_LIMIT,
						ResourceView.of(opponent), opponent.deck().size(), opponent.hand().size(),
						BoardCharacterView.listOf(opponent, language), ActiveEventView.listOf(opponent, language),
						opponent.graveyard().size()),
				ResultView.of(match.result()),
				match.log().stream().map(event -> LogEntryView.of(event, formatter, language)).toList());
	}

	public record TurnView(int number, Match.Side active) {
	}

	public record FactionRef(String id, String name) {

		static FactionRef of(Faction faction, Language language) {
			return new FactionRef(faction.id(), faction.name().in(language));
		}

	}

	public record ResourceView(int current, int max) {

		static ResourceView of(MatchSide side) {
			return new ResourceView(side.resourceCurrent(), side.resourceMax());
		}

	}

	public record PlayerView(FactionRef faction, int stress, int stressLimit, ResourceView resource, int deckCount,
			List<HandCardView> hand, List<BoardCharacterView> board, List<ActiveEventView> activeEvents,
			int graveyardCount) {
	}

	public record OpponentView(FactionRef faction, int stress, int stressLimit, ResourceView resource, int deckCount,
			int handCount, List<BoardCharacterView> board, List<ActiveEventView> activeEvents, int graveyardCount) {
	}

	/**
	 * Karta na ręce gracza; {@code attack}, {@code health} i {@code ability} tylko dla postaci,
	 * {@code duration} tylko dla wydarzeń (pozostałe pola są {@code null}).
	 */
	public record HandCardView(int instanceId, String cardId, String name, CardType type, int cost, String text,
			boolean neutral, Integer attack, Integer health, AbilityView ability, Integer duration) {

		static HandCardView of(CardInstance instance, CardCatalog catalog, Language language) {
			Card card = instance.card();
			return new HandCardView(instance.instanceId(), card.id(), card.name().in(language), card.type(),
					card.cost(), card.text().in(language), catalog.isNeutral(card), card.attack(), card.health(),
					AbilityView.of(card.ability(), language), card.duration());
		}

	}

	/** Postać na stole; pozostałe HP = {@code health - damage}. */
	public record BoardCharacterView(int instanceId, String cardId, String name, int attack, int health, int damage,
			boolean canAttack, AbilityView ability) {

		static List<BoardCharacterView> listOf(MatchSide side, Language language) {
			return side.board().stream().map(character -> of(character, language)).toList();
		}

		static BoardCharacterView of(BoardCharacter character, Language language) {
			Card card = character.instance().card();
			return new BoardCharacterView(character.instance().instanceId(), card.id(), card.name().in(language),
					card.attack(), card.health(), character.damage(), character.canAttack(),
					AbilityView.of(card.ability(), language));
		}

	}

	public record AbilityView(AbilityKeyword keyword, String text) {

		static AbilityView of(Ability ability, Language language) {
			return ability == null ? null
					: new AbilityView(ability.keyword(), ability.keyword().describe(ability.amount()).in(language));
		}

	}

	public record ActiveEventView(int instanceId, String cardId, String name, int turnsLeft) {

		static List<ActiveEventView> listOf(MatchSide side, Language language) {
			return side.activeEvents().stream().map(event -> of(event, language)).toList();
		}

		static ActiveEventView of(ActiveEvent event, Language language) {
			Card card = event.instance().card();
			return new ActiveEventView(event.instance().instanceId(), card.id(), card.name().in(language),
					event.turnsLeft());
		}

	}

	public record ResultView(Match.Side winner, MatchResult.EndReason reason) {

		static ResultView of(MatchResult result) {
			return result == null ? null : new ResultView(result.winner(), result.reason());
		}

	}

	public record LogEntryView(Match.Side side, String text) {

		static LogEntryView of(MatchEvent event, MatchLogFormatter formatter, Language language) {
			return new LogEntryView(event.side(), formatter.format(event, language));
		}

	}

}
