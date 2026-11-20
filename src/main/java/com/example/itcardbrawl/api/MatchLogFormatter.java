package com.example.itcardbrawl.api;

import com.example.itcardbrawl.game.CardInstance;
import com.example.itcardbrawl.game.Language;
import com.example.itcardbrawl.game.Match.Side;
import com.example.itcardbrawl.game.MatchEvent;
import com.example.itcardbrawl.game.MatchEvent.AbilityTriggered;
import com.example.itcardbrawl.game.MatchEvent.Attacked;
import com.example.itcardbrawl.game.MatchEvent.CardBurned;
import com.example.itcardbrawl.game.MatchEvent.CardDrawn;
import com.example.itcardbrawl.game.MatchEvent.CardPlayed;
import com.example.itcardbrawl.game.MatchEvent.CharacterDefeated;
import com.example.itcardbrawl.game.MatchEvent.EventExpired;
import com.example.itcardbrawl.game.MatchEvent.EffectApplied;
import com.example.itcardbrawl.game.MatchEvent.MatchEnded;
import com.example.itcardbrawl.game.MatchEvent.StressChanged;
import com.example.itcardbrawl.game.MatchEvent.TurnEnded;
import com.example.itcardbrawl.game.MatchEvent.TurnStarted;
import com.example.itcardbrawl.game.MatchResult;
import com.example.itcardbrawl.game.MatchRules;

import org.springframework.stereotype.Component;

/**
 * Składa zdania logu partii z perspektywy gracza: jego strona to „Ty” / „You”, strona komputera to
 * „Przeciwnik” / „Opponent”. Nazwy kart pochodzą z katalogu w języku żądania. Karty dobierane przez
 * przeciwnika nie są ujawniane.
 */
@Component
public class MatchLogFormatter {

	public String format(MatchEvent event, Language language) {
		return switch (event) {
			case TurnStarted e -> mine(e.side())
					? text(language, "Twoja tura %d".formatted(e.turn()), "Your turn %d".formatted(e.turn()))
					: text(language, "Tura przeciwnika %d".formatted(e.turn()), "Opponent's turn %d".formatted(e.turn()));
			case CardDrawn e -> mine(e.side())
					? text(language, "Dobierasz: " + name(e.card(), language), "You draw: " + name(e.card(), language))
					: text(language, "Przeciwnik dobiera kartę", "Opponent draws a card");
			case CardBurned e -> mine(e.side())
					? text(language, "Pełna ręka, karta na cmentarz: " + name(e.card(), language),
							"Hand full, card burned: " + name(e.card(), language))
					: text(language, "Pełna ręka przeciwnika, karta na cmentarz: " + name(e.card(), language),
							"Opponent's hand full, card burned: " + name(e.card(), language));
			case CardPlayed e -> mine(e.side())
					? text(language, "Zagrywasz: " + name(e.card(), language), "You play: " + name(e.card(), language))
					: text(language, "Przeciwnik zagrywa: " + name(e.card(), language),
							"Opponent plays: " + name(e.card(), language));
			case EffectApplied e -> effectApplied(e, language);
			case AbilityTriggered e -> abilityTriggered(e, language);
			case Attacked e -> attacked(e, language);
			case StressChanged e -> mine(e.affected())
					? text(language, "Twój stres: %d → %d/%d".formatted(e.from(), e.to(), MatchRules.STRESS_LIMIT),
							"Your stress: %d → %d/%d".formatted(e.from(), e.to(), MatchRules.STRESS_LIMIT))
					: text(language, "Stres przeciwnika: %d → %d/%d".formatted(e.from(), e.to(), MatchRules.STRESS_LIMIT),
							"Opponent stress: %d → %d/%d".formatted(e.from(), e.to(), MatchRules.STRESS_LIMIT));
			case CharacterDefeated e -> mine(e.side())
					? text(language, "Twoja postać pokonana: " + name(e.card(), language),
							"Your character is defeated: " + name(e.card(), language))
					: text(language, "Postać przeciwnika pokonana: " + name(e.card(), language),
							"Opponent's character is defeated: " + name(e.card(), language));
			case EventExpired e -> mine(e.side())
					? text(language, "Twoje wydarzenie wygasa: " + name(e.card(), language),
							"Your event expires: " + name(e.card(), language))
					: text(language, "Wydarzenie przeciwnika wygasa: " + name(e.card(), language),
							"Opponent's event expires: " + name(e.card(), language));
			case TurnEnded e -> mine(e.side())
					? text(language, "Kończysz turę", "You end your turn")
					: text(language, "Przeciwnik kończy turę", "Opponent ends their turn");
			case MatchEnded e -> matchEnded(e.result(), language);
		};
	}

	private static String effectApplied(EffectApplied e, Language language) {
		String description = e.effect().describe().in(language);
		String target = e.targetCharacter() != null ? " — " + name(e.targetCharacter(), language)
				: e.targetPlayer() != null ? (mine(e.targetPlayer())
						? text(language, " — Twój stres", " — your stress")
						: text(language, " — stres przeciwnika", " — opponent stress")) : "";
		return name(e.card(), language) + ": " + description + target;
	}

	private static String abilityTriggered(AbilityTriggered e, Language language) {
		String card = name(e.card(), language);
		int amount = e.amount();
		return switch (e.keyword()) {
			case ON_PLAY_STRESS, OVERTIME -> mine(e.side())
					? text(language, "%s dodaje przeciwnikowi stresu: +%d".formatted(card, amount),
							"%s adds opponent stress: +%d".formatted(card, amount))
					: text(language, "%s dodaje Ci stresu: +%d".formatted(card, amount),
							"%s adds to your stress: +%d".formatted(card, amount));
			case HEAL -> mine(e.side())
					? text(language, "%s zmniejsza Twój stres: −%d".formatted(card, amount),
							"%s reduces your stress: −%d".formatted(card, amount))
					: text(language, "%s zmniejsza stres przeciwnika: −%d".formatted(card, amount),
							"%s reduces opponent stress: −%d".formatted(card, amount));
			// Zdolności stałe nie wyzwalają się jako zdarzenie, ale przełącznik musi być kompletny.
			case GUARD, RUSH -> card + ": " + e.keyword().describe(amount).in(language);
		};
	}

	private static String attacked(Attacked e, Language language) {
		String attacker = name(e.attacker(), language);
		if (e.target() == null) {
			return mine(e.side())
					? text(language, "%s atakuje stres przeciwnika: +%d".formatted(attacker, e.damageDealt()),
							"%s attacks opponent stress: +%d".formatted(attacker, e.damageDealt()))
					: text(language, "%s atakuje Twój stres: +%d".formatted(attacker, e.damageDealt()),
							"%s attacks your stress: +%d".formatted(attacker, e.damageDealt()));
		}
		String target = name(e.target(), language);
		return mine(e.side())
				? text(language,
						"Twoja postać %s atakuje %s: zadaje %d, otrzymuje %d".formatted(attacker, target,
								e.damageDealt(), e.damageTaken()),
						"Your %s attacks %s: deals %d, takes %d".formatted(attacker, target, e.damageDealt(),
								e.damageTaken()))
				: text(language,
						"Przeciwnik: %s atakuje Twoją postać %s: zadaje %d, otrzymuje %d".formatted(attacker, target,
								e.damageDealt(), e.damageTaken()),
						"Opponent's %s attacks your %s: deals %d, takes %d".formatted(attacker, target,
								e.damageDealt(), e.damageTaken()));
	}

	private static String matchEnded(MatchResult result, Language language) {
		boolean won = mine(result.winner());
		return switch (result.reason()) {
			case STRESS_LIMIT -> won
					? text(language, "Wygrywasz: stres przeciwnika osiągnął limit",
							"You win: opponent's stress reached the limit")
					: text(language, "Przegrywasz: Twój stres osiągnął limit", "You lose: your stress reached the limit");
			case EMPTY_DECK -> won
					? text(language, "Wygrywasz: przeciwnikowi skończyła się talia", "You win: opponent ran out of cards")
					: text(language, "Przegrywasz: skończyła się Twoja talia", "You lose: your deck ran out");
		};
	}

	private static boolean mine(Side side) {
		return side == Side.PLAYER;
	}

	private static String name(CardInstance card, Language language) {
		return card.card().name().in(language);
	}

	private static String text(Language language, String pl, String en) {
		return switch (language) {
			case PL -> pl;
			case EN -> en;
		};
	}

}
