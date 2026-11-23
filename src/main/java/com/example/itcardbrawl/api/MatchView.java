package com.example.itcardbrawl.api;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;

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
 * The board view from the player's perspective. The opponent is visible through the board, active events and
 * counters — their hand and deck order never leave the server. Texts (faction names, card names and text, log) are
 * in the request language; match and card identifiers do not depend on the language.
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
				IntStream.range(0, replay.size())
					.mapToObj(i -> ReplayStepView.of(replay.get(i), i == 0 ? null : replay.get(i - 1), dropped, catalog,
							formatter, language))
					.toList());
	}

	/**
	 * The board state after one computer move. {@code reason} is the reason for the move that led to this step
	 * (the {@code MoveExplained} entry from the log fragment added in this step), or {@code null} for the starting
	 * step — its log ends at the previous turn, so it contains no reason for the current move.
	 */
	public record ReplayStepView(TurnView turn, Match.Status status, ResultView result, PlayerView player,
			OpponentView opponent, int logSize, String reason) {

		static ReplayStepView of(Match step, Match previous, int droppedLog, CardCatalog catalog,
				MatchLogFormatter formatter, Language language) {
			return new ReplayStepView(TurnView.of(step), step.status(), ResultView.of(step.result()),
					PlayerView.of(step, catalog, language, List.of()), OpponentView.of(step, language, List.of()),
						Math.max(0, step.log().size() - droppedLog), reason(step, previous, formatter, language));
		}

		private static String reason(Match step, Match previous, MatchLogFormatter formatter, Language language) {
			if (previous == null) {
				return null;
			}
			List<MatchEvent> log = step.log();
			return log.subList(Math.min(previous.log().size(), log.size()), log.size()).stream()
				.filter(MatchEvent.MoveExplained.class::isInstance)
				.reduce((first, second) -> second)
				.map(event -> formatter.format(event, language))
				.orElse(null);
		}

	}

	/**
	 * {@code timeLimitSeconds} is the player's turn limit ({@link MatchRules#TURN_LIMIT}) during the player's turn in
	 * a match in progress, {@code null} otherwise. The SPA counts it down from the end of the replay; the server keeps
	 * its own deadline, so the countdown does not depend on the client's clock.
	 */
	public record TurnView(int number, Match.Side active, Integer timeLimitSeconds) {

		static TurnView of(Match match) {
			return new TurnView(match.turnNumber(), match.activeSide(),
					match.isPlayerTurn() ? (int) MatchRules.TURN_LIMIT.toSeconds() : null);
		}

	}

	public record FactionRef(String id, String name) {

		static FactionRef of(Faction faction, Language language) {
			return new FactionRef(faction.id(), faction.name().in(language));
		}

	}

	public record BudgetView(int current, int max) {

		static BudgetView of(MatchSide side) {
			return new BudgetView(side.budgetCurrent(), side.budgetMax());
		}

	}

	public record PlayerView(FactionRef faction, int hp, int maxHp, BudgetView budget, int deckCount,
			List<HandCardView> hand, List<BoardCharacterView> board, List<ActiveEventView> activeEvents,
			int graveyardCount, PlayedEventView lastEvent, List<GraveyardCardView> graveyard) {

		static PlayerView of(Match match, CardCatalog catalog, Language language, List<Move> legalMoves) {
			MatchSide player = match.player();
			return new PlayerView(FactionRef.of(player.faction(), language), player.hp(), MatchRules.MAX_HP,
					BudgetView.of(player), player.deck().size(),
					player.hand().stream().map(card -> HandCardView.of(card, catalog, language, legalMoves)).toList(),
					BoardCharacterView.listOf(match, Match.Side.PLAYER, language, legalMoves),
					ActiveEventView.listOf(match, Match.Side.PLAYER, language), player.graveyard().size(),
					PlayedEventView.last(match, Match.Side.PLAYER, language), GraveyardCardView.listOf(player, language));
		}

	}

	public record OpponentView(FactionRef faction, int hp, int maxHp, BudgetView budget, int deckCount,
			int handCount, List<BoardCharacterView> board, List<ActiveEventView> activeEvents, int graveyardCount,
			PlayedEventView lastEvent, List<GraveyardCardView> graveyard) {

		static OpponentView of(Match match, Language language, List<Move> legalMoves) {
			MatchSide opponent = match.opponent();
			return new OpponentView(FactionRef.of(opponent.faction(), language), opponent.hp(),
					MatchRules.MAX_HP, BudgetView.of(opponent), opponent.deck().size(), opponent.hand().size(),
					BoardCharacterView.listOf(match, Match.Side.OPPONENT, language, legalMoves),
					ActiveEventView.listOf(match, Match.Side.OPPONENT, language), opponent.graveyard().size(),
					PlayedEventView.last(match, Match.Side.OPPONENT, language), GraveyardCardView.listOf(opponent, language));
		}

	}

	public record HandCardView(int instanceId, String cardId, String name, CardType type, int cost, String text,
			boolean neutral, Integer attack, Integer health, AbilityView ability, Integer duration,
			List<EffectView> effects, boolean requiresTarget, boolean playable, List<TargetView> targets,
			String faction) {

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
					!plays.isEmpty(), targets, card.faction());
		}

	}

	public record BoardCharacterView(int instanceId, String cardId, String name, int attack, int health, int damage,
			boolean canAttack, AbilityView ability, int baseAttack, int baseHealth, Set<AbilityKeyword> keywords,
			boolean trapped, List<TargetView> attackTargets, int cost, String faction) {

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
					targets, card.cost(), card.faction());
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
			List<EffectView> effects, String targetName, int cost, String faction) {

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
					targetName, card.cost(), card.faction());
		}

	}

	/**
	 * A card in a side's graveyard with its catalog content; {@code faction} is the card's own faction id
	 * (neutral cards report it). {@code attack}/{@code health}/{@code ability} are set for characters only,
	 * {@code duration}/{@code effects} for events only (the others are {@code null}, effects empty).
	 */
	public record GraveyardCardView(int instanceId, String cardId, String name, CardType type, int cost,
			String faction, Integer attack, Integer health, AbilityView ability, Integer duration,
			List<EffectView> effects, String text) {

		static List<GraveyardCardView> listOf(MatchSide side, Language language) {
			return side.graveyard().stream().map(instance -> of(instance, language)).toList();
		}

		static GraveyardCardView of(CardInstance instance, Language language) {
			Card card = instance.card();
			return new GraveyardCardView(instance.instanceId(), card.id(), card.name().in(language), card.type(),
					card.cost(), card.faction(), card.attack(), card.health(), AbilityView.of(card.ability(), language),
					card.duration(), card.effects() == null ? List.of() : card.effects().stream()
						.map(effect -> EffectView.of(effect, language)).toList(),
					card.text().in(language));
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

	/** A log entry; {@code reason} distinguishes the reason for a computer move from the effects of moves. */
	public record LogEntryView(Match.Side side, String text, boolean reason) {

		static LogEntryView of(MatchEvent event, MatchLogFormatter formatter, Language language) {
			return new LogEntryView(event.side(), formatter.format(event, language),
					event instanceof MatchEvent.MoveExplained);
		}

	}

}
