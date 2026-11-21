package com.example.itcardbrawl.api;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.example.itcardbrawl.game.Ability;
import com.example.itcardbrawl.game.AbilityKeyword;
import com.example.itcardbrawl.game.ActiveEvent;
import com.example.itcardbrawl.game.BoardCharacter;
import com.example.itcardbrawl.game.Card;
import com.example.itcardbrawl.game.CardCatalog;
import com.example.itcardbrawl.game.CardInstance;
import com.example.itcardbrawl.game.CardType;
import com.example.itcardbrawl.game.CharacterStats;
import com.example.itcardbrawl.game.Effect;
import com.example.itcardbrawl.game.EffectScope;
import com.example.itcardbrawl.game.Faction;
import com.example.itcardbrawl.game.Language;
import com.example.itcardbrawl.game.Match;
import com.example.itcardbrawl.game.MatchEvent;
import com.example.itcardbrawl.game.MatchResult;
import com.example.itcardbrawl.game.MatchRules;
import com.example.itcardbrawl.game.MatchSide;
import com.example.itcardbrawl.game.Move;
import com.example.itcardbrawl.game.Target;

/**
 * Widok stołu z perspektywy gracza. Przeciwnik jest widoczny przez stół, aktywne wydarzenia i liczniki —
 * jego ręka i kolejność talii nie opuszczają serwera. Teksty (nazwy frakcji, nazwy i treść kart, log) są
 * w języku żądania; identyfikatory partii i kart od języka nie zależą.
 */
public record MatchView(UUID matchId, Match.Status status, TurnView turn, PlayerView player, OpponentView opponent,
		ResultView result, List<LogEntryView> log, List<ReplayStepView> replay) {

	static final int LOG_LIMIT = 100;

	public static MatchView of(Match match, List<Match> replay, CardCatalog catalog, MatchLogFormatter formatter,
			Language language, List<Move> legalMoves) {
		List<MatchEvent> fullLog = match.log();
		int dropped = Math.max(0, fullLog.size() - LOG_LIMIT);
		return new MatchView(match.id(), match.status(), TurnView.of(match),
				PlayerView.of(match, catalog, language, legalMoves), OpponentView.of(match, language, legalMoves),
				ResultView.of(match.result()), fullLog.subList(dropped, fullLog.size()).stream()
					.map(event -> LogEntryView.of(event, formatter, language)).toList(),
				replay.stream().map(step -> ReplayStepView.of(step, dropped, catalog, language)).toList());
	}

	public record ReplayStepView(TurnView turn, Match.Status status, ResultView result, PlayerView player,
			OpponentView opponent, int logSize) {

		static ReplayStepView of(Match step, int droppedLog, CardCatalog catalog, Language language) {
			return new ReplayStepView(TurnView.of(step), step.status(), ResultView.of(step.result()),
					PlayerView.of(step, catalog, language, List.of()), OpponentView.of(step, language, List.of()),
						Math.max(0, step.log().size() - droppedLog));
		}

	}

	public record TurnView(int number, Match.Side active) {

		static TurnView of(Match match) {
			return new TurnView(match.turnNumber(), match.activeSide());
		}

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

	public record PlayerView(FactionRef faction, int hp, int maxHp, ResourceView resource, int deckCount,
			List<HandCardView> hand, List<BoardCharacterView> board, List<ActiveEventView> activeEvents,
			int graveyardCount, PlayedEventView lastEvent) {

		static PlayerView of(Match match, CardCatalog catalog, Language language, List<Move> legalMoves) {
			MatchSide player = match.player();
			return new PlayerView(FactionRef.of(player.faction(), language), player.hp(), MatchRules.MAX_HP,
					ResourceView.of(player), player.deck().size(),
					player.hand().stream().map(card -> HandCardView.of(card, catalog, language, legalMoves)).toList(),
					BoardCharacterView.listOf(match, Match.Side.PLAYER, language, legalMoves),
					ActiveEventView.listOf(match, Match.Side.PLAYER, language), player.graveyard().size(),
					PlayedEventView.last(match, Match.Side.PLAYER, language));
		}

	}

	public record OpponentView(FactionRef faction, int hp, int maxHp, ResourceView resource, int deckCount,
			int handCount, List<BoardCharacterView> board, List<ActiveEventView> activeEvents, int graveyardCount,
			PlayedEventView lastEvent) {

		static OpponentView of(Match match, Language language, List<Move> legalMoves) {
			MatchSide opponent = match.opponent();
			return new OpponentView(FactionRef.of(opponent.faction(), language), opponent.hp(),
					MatchRules.MAX_HP, ResourceView.of(opponent), opponent.deck().size(), opponent.hand().size(),
					BoardCharacterView.listOf(match, Match.Side.OPPONENT, language, legalMoves),
					ActiveEventView.listOf(match, Match.Side.OPPONENT, language), opponent.graveyard().size(),
					PlayedEventView.last(match, Match.Side.OPPONENT, language));
		}

	}

	public record HandCardView(int instanceId, String cardId, String name, CardType type, int cost, String text,
			boolean neutral, Integer attack, Integer health, AbilityView ability, Integer duration,
			List<EffectView> effects, boolean requiresTarget, boolean playable, List<TargetView> targets) {

		static HandCardView of(CardInstance instance, CardCatalog catalog, Language language, List<Move> legalMoves) {
			Card card = instance.card();
			List<Move.PlayCard> plays = legalMoves.stream().filter(Move.PlayCard.class::isInstance)
					.map(Move.PlayCard.class::cast).filter(play -> play.instanceId() == instance.instanceId()).toList();
			List<TargetView> targets = plays.stream().map(Move.PlayCard::target).filter(target -> target != null)
					.map(TargetView::of).distinct().toList();
			return new HandCardView(instance.instanceId(), card.id(), card.name().in(language), card.type(), card.cost(),
					card.text().in(language), catalog.isNeutral(card), card.attack(), card.health(),
					AbilityView.of(card.ability(), language), card.duration(),
					card.effects() == null ? List.of() : card.effects().stream()
						.map(effect -> EffectView.of(effect, language)).toList(),
					card.effects() != null && card.effects().stream().anyMatch(effect -> effect.scope() == EffectScope.TARGET),
					!plays.isEmpty(), targets);
		}

	}

	public record BoardCharacterView(int instanceId, String cardId, String name, int attack, int health, int damage,
			boolean canAttack, AbilityView ability, int baseAttack, int baseHealth, Set<AbilityKeyword> keywords,
			boolean trapped, List<TargetView> attackTargets) {

		static List<BoardCharacterView> listOf(Match match, Match.Side owner, Language language, List<Move> legalMoves) {
			return match.side(owner).board().stream()
					.map(character -> of(match, owner, character, language, legalMoves)).toList();
		}

		static BoardCharacterView of(Match match, Match.Side owner, BoardCharacter character, Language language,
				List<Move> legalMoves) {
			Card card = character.instance().card();
			CharacterStats stats = CharacterStats.of(character, owner, match.player().activeEvents(),
					match.opponent().activeEvents());
			List<Move.Attack> attacks = legalMoves.stream().filter(Move.Attack.class::isInstance)
					.map(Move.Attack.class::cast)
					.filter(attack -> owner == Match.Side.PLAYER && attack.attackerId() == character.instance().instanceId())
					.toList();
			List<TargetView> targets = attacks.stream().map(attack -> attack.targetId() == null
					? new TargetView("PLAYER", null, Match.Side.OPPONENT)
					: new TargetView("CHARACTER", attack.targetId(), null)).distinct().toList();
			return new BoardCharacterView(character.instance().instanceId(), card.id(), card.name().in(language),
					stats.attack(), stats.health(), character.damage(), !attacks.isEmpty(),
					AbilityView.of(card.ability(), language), card.attack(), card.health(), stats.keywords(), stats.trapped(),
					targets);
		}

	}

	public record AbilityView(AbilityKeyword keyword, String text) {

		static AbilityView of(Ability ability, Language language) {
			return ability == null ? null
					: new AbilityView(ability.keyword(), ability.keyword().describe(ability.amount()).in(language));
		}

	}

	public record EffectView(com.example.itcardbrawl.game.EffectKind kind, EffectScope scope, int amount,
			String text) {

		static EffectView of(Effect effect, Language language) {
			return new EffectView(effect.kind(), effect.scope(), effect.amount(), effect.describe().in(language));
		}

	}

	public record TargetView(String kind, Integer instanceId, Match.Side side) {

		static TargetView of(Target target) {
			return switch (target) {
				case Target.Character character -> new TargetView("CHARACTER", character.instanceId(), null);
				case Target.Player player -> new TargetView("PLAYER", null, player.side());
			};
		}

	}

	public record ActiveEventView(int instanceId, String cardId, String name, int turnsLeft,
			List<EffectView> effects, String targetName) {

		static List<ActiveEventView> listOf(Match match, Match.Side side, Language language) {
			return match.side(side).activeEvents().stream().map(event -> of(match, event, language)).toList();
		}

		static ActiveEventView of(Match match, ActiveEvent event, Language language) {
			Card card = event.instance().card();
			String targetName = event.targetId() == null ? null
					: java.util.stream.Stream.of(match.player(), match.opponent()).flatMap(player -> player.board().stream())
						.filter(character -> character.instance().instanceId() == event.targetId())
						.map(character -> character.instance().card().name().in(language)).findFirst().orElse(null);
			return new ActiveEventView(event.instance().instanceId(), card.id(), card.name().in(language),
					event.turnsLeft(), card.effects().stream().map(effect -> EffectView.of(effect, language)).toList(),
					targetName);
		}

	}

	public record PlayedEventView(int instanceId, String name, List<EffectView> effects) {

		static PlayedEventView last(Match match, Match.Side side, Language language) {
			return match.log().reversed().stream().filter(MatchEvent.CardPlayed.class::isInstance)
					.map(MatchEvent.CardPlayed.class::cast).filter(event -> event.side() == side)
					.filter(event -> event.card().card().type() == CardType.EVENT).findFirst()
					.map(event -> new PlayedEventView(event.card().instanceId(), event.card().card().name().in(language),
							event.card().card().effects().stream().map(effect -> EffectView.of(effect, language)).toList()))
					.orElse(null);
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
