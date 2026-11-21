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
import com.example.itcardbrawl.game.MatchEvent.HpChanged;
import com.example.itcardbrawl.game.MatchEvent.MatchEnded;
import com.example.itcardbrawl.game.MatchEvent.TurnEnded;
import com.example.itcardbrawl.game.MatchEvent.TurnStarted;
import com.example.itcardbrawl.game.MatchResult;
import com.example.itcardbrawl.game.MatchRules;
import com.example.itcardbrawl.game.PolishNumberForms;

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
			case HpChanged e -> mine(e.affected())
					? text(language, "Twoje HP: %s → %s/%d".formatted(hp(e.from()), hp(e.to()), MatchRules.MAX_HP),
							"Your HP: %s → %s/%d".formatted(hp(e.from()), hp(e.to()), MatchRules.MAX_HP))
					: text(language, "HP przeciwnika: %s → %s/%d".formatted(hp(e.from()), hp(e.to()), MatchRules.MAX_HP),
							"Opponent HP: %s → %s/%d".formatted(hp(e.from()), hp(e.to()), MatchRules.MAX_HP));
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
						? text(language, " — Twoje HP", " — your HP")
						: text(language, " — HP przeciwnika", " — opponent HP")) : "";
		String card = name(e.card(), language);
		String prefix = mine(e.side())
				? text(language, "Twoje wydarzenie " + card, "Your event " + card)
				: text(language, "Wydarzenie przeciwnika " + card, "Opponent's event " + card);
		return prefix + ": " + description + target;
	}

	private static String abilityTriggered(AbilityTriggered e, Language language) {
		String card = name(e.card(), language);
		int amount = e.amount();
		return switch (e.keyword()) {
			case ON_PLAY_DAMAGE, OVERTIME -> mine(e.side())
					? text(language, "%s zadaje przeciwnikowi %d %s".formatted(card, amount, PolishNumberForms.damage(amount)),
							"%s deals %d damage to the opponent".formatted(card, amount))
					: text(language, "%s zadaje Ci %d %s".formatted(card, amount, PolishNumberForms.damage(amount)),
							"%s deals you %d damage".formatted(card, amount));
			case HEAL -> mine(e.side())
					? text(language, "%s leczy Twoje HP o %d".formatted(card, amount),
							"%s heals your HP by %d".formatted(card, amount))
					: text(language, "%s leczy HP przeciwnika o %d".formatted(card, amount),
							"%s heals opponent HP by %d".formatted(card, amount));
			// Zdolności stałe nie wyzwalają się jako zdarzenie, ale przełącznik musi być kompletny.
			case GUARD, RUSH -> card + ": " + e.keyword().describe(amount).in(language);
		};
	}

	private static String attacked(Attacked e, Language language) {
		String attacker = name(e.attacker(), language);
		if (e.target() == null) {
			return mine(e.side())
					? text(language, "%s atakuje przeciwnika i zadaje %d %s".formatted(attacker, e.damageDealt(),
									PolishNumberForms.damage(e.damageDealt())),
							"%s attacks the opponent for %d damage".formatted(attacker, e.damageDealt()))
					: text(language, "%s atakuje Cię i zadaje %d %s".formatted(attacker, e.damageDealt(),
									PolishNumberForms.damage(e.damageDealt())),
							"%s attacks you for %d damage".formatted(attacker, e.damageDealt()));
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
			case HP_DEPLETED -> won
					? text(language, "Wygrywasz: HP przeciwnika spadło do 0 lub niżej", "You win: opponent's HP dropped to 0 or below")
					: text(language, "Przegrywasz: Twoje HP spadło do 0 lub niżej", "You lose: your HP dropped to 0 or below");
			case EMPTY_DECK -> won
					? text(language, "Wygrywasz: przeciwnikowi skończyła się talia", "You win: opponent ran out of cards")
					: text(language, "Przegrywasz: skończyła się Twoja talia", "You lose: your deck ran out");
		};
	}

	// Ujemne HP z typograficznym minusem (U+2212).
	private static String hp(int value) {
		return value < 0 ? "−" + -value : Integer.toString(value);
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
