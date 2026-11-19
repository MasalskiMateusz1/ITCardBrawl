package com.example.itcardbrawl.api;

import java.util.List;
import java.util.UUID;

import com.example.itcardbrawl.game.CardCatalog;
import com.example.itcardbrawl.game.CardInstance;
import com.example.itcardbrawl.game.CardType;
import com.example.itcardbrawl.game.Faction;
import com.example.itcardbrawl.game.Language;
import com.example.itcardbrawl.game.Match;
import com.example.itcardbrawl.game.MatchRules;
import com.example.itcardbrawl.game.MatchSide;

/**
 * Widok stołu z perspektywy gracza. Przeciwnik jest widoczny tylko przez liczniki —
 * jego ręka i kolejność talii nie opuszczają serwera. Teksty (nazwy frakcji, nazwy i treść kart) są w języku
 * żądania; identyfikatory partii i kart od języka nie zależą.
 */
public record MatchView(UUID matchId, TurnView turn, PlayerView player, OpponentView opponent) {

	public static MatchView of(Match match, CardCatalog catalog, Language language) {
		MatchSide player = match.player();
		MatchSide opponent = match.opponent();
		return new MatchView(match.id(),
				new TurnView(match.turnNumber(), match.activeSide()),
				new PlayerView(FactionRef.of(player.faction(), language), player.stress(), MatchRules.STRESS_LIMIT,
						ResourceView.of(player), player.deck().size(),
						player.hand().stream().map(card -> HandCardView.of(card, catalog, language)).toList()),
				new OpponentView(FactionRef.of(opponent.faction(), language), opponent.stress(), MatchRules.STRESS_LIMIT,
						ResourceView.of(opponent), opponent.deck().size(), opponent.hand().size()));
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
			List<HandCardView> hand) {
	}

	public record OpponentView(FactionRef faction, int stress, int stressLimit, ResourceView resource, int deckCount,
			int handCount) {
	}

	public record HandCardView(int instanceId, String cardId, String name, CardType type, int cost, String text,
			boolean neutral) {

		static HandCardView of(CardInstance instance, CardCatalog catalog, Language language) {
			var card = instance.card();
			return new HandCardView(instance.instanceId(), card.id(), card.name().in(language), card.type(),
					card.cost(), card.text().in(language), catalog.isNeutral(card));
		}

	}

}
