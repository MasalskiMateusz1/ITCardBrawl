package com.example.itcardbrawl.game;

import java.util.List;

import com.example.itcardbrawl.game.Match.Side;
import com.example.itcardbrawl.game.Match.Status;

import org.springframework.stereotype.Component;

/**
 * Liczbowa ocena stanu partii z perspektywy jednej strony, rozbita na nazwane składniki ({@link Score}).
 * Czyta wyłącznie dane jawne dla obu graczy: HP, budżety, stoły z aktywnymi wydarzeniami (przez
 * {@link CharacterStats}) oraz liczby kart na ręce i w talii — nigdy zawartość ręki ani kolejność talii.
 *
 * <p>Styl zrównoważony: punkt HP przeciwnika i punkt wartości stołu ważą podobnie. Gdy HP przeciwnika
 * spada poniżej {@link #ENEMY_HP_LOW}, rośnie waga obrażeń w HP; gdy własne HP spada poniżej
 * {@link #OWN_HP_LOW}, rośnie waga własnego HP, Ochrony i zagrożenia ze stołu przeciwnika.
 */
@Component
public class PositionEvaluator {

	/** Wartość wygranej; przegrana to {@code -WIN_SCORE}. W praktyce nieskończoność. */
	static final double WIN_SCORE = 1_000_000;

	/** Waga punktu HP przeciwnika (każdy zabrany punkt podnosi ocenę). */
	static final double ENEMY_HP_WEIGHT = 1.0;

	/** Próg „HP przeciwnika nisko”: poniżej każdy punkt waży dodatkowo {@link #ENEMY_HP_LOW_EXTRA}. */
	static final int ENEMY_HP_LOW = 10;

	static final double ENEMY_HP_LOW_EXTRA = 1.0;

	/** Waga punktu własnego HP. */
	static final double OWN_HP_WEIGHT = 0.7;

	/** Próg „własne HP nisko”: poniżej każdy punkt waży dodatkowo {@link #OWN_HP_LOW_EXTRA}. */
	static final int OWN_HP_LOW = 12;

	static final double OWN_HP_LOW_EXTRA = 1.0;

	/** Stała wartość obecności postaci na stole. */
	static final double CHARACTER_BASE = 1.0;

	static final double ATTACK_WEIGHT = 1.0;

	static final double HEALTH_WEIGHT = 0.7;

	/**
	 * Obrażenia postaci znikają na koniec tury, więc liczą się tylko częściowo: zdrowie postaci to
	 * {@code health - DAMAGE_DISCOUNT * damage}.
	 */
	static final double DAMAGE_DISCOUNT = 0.5;

	/** Uwięziona postać nie atakuje i nie blokuje — zachowuje tylko taki ułamek wartości. */
	static final double TRAPPED_FACTOR = 0.3;

	static final double GUARD_VALUE = 1.0;

	/** Dodatkowa wartość własnej Ochrony przy niskim własnym HP. */
	static final double GUARD_LOW_HP_EXTRA = 2.0;

	static final double OVERTIME_VALUE = 1.5;

	/** Mnożnik wartości stołu przeciwnika (zagrożenia) przy niskim własnym HP. */
	static final double ENEMY_BOARD_THREAT_FACTOR = 1.3;

	/** Wartość karty na ręce (liczy się tylko liczba kart). */
	static final double HAND_CARD_VALUE = 1.0;

	/** Kara za pustą własną talię: kolejne dobranie przegrywa partię. */
	static final double EMPTY_DECK_PENALTY = 15.0;

	/**
	 * Wartość punktu bieżącego budżetu. Budżet odnawia się na początku tury, więc waga jest mała: zagranie
	 * karty nie może przegrywać z niewydanym budżetem.
	 */
	static final double BUDGET_CURRENT_WEIGHT = 0.25;

	/** Wartość punktu maksymalnego budżetu (trwały zysk). */
	static final double BUDGET_MAX_WEIGHT = 1.0;

	/**
	 * Ocena stanu z perspektywy strony. {@link #total()} to suma składników; partia zakończona ma składnik
	 * {@code outcome} równy {@code ±WIN_SCORE}.
	 */
	public record Score(double outcome, double ownHp, double enemyHp, double ownBoard, double enemyBoard,
			double cards, double resources) {

		public double total() {
			return outcome + ownHp + enemyHp + ownBoard + enemyBoard + cards + resources;
		}

	}

	public Score score(Match match, Side perspective) {
		MatchSide own = match.side(perspective);
		MatchSide enemy = match.side(perspective.opponent());
		double outcome = 0;
		if (match.status() == Status.FINISHED && match.result() != null) {
			outcome = match.result().winner() == perspective ? WIN_SCORE : -WIN_SCORE;
		}
		boolean ownHpLow = own.hp() < OWN_HP_LOW;

		double ownHp = OWN_HP_WEIGHT * own.hp() - OWN_HP_LOW_EXTRA * Math.max(0, OWN_HP_LOW - own.hp());
		double enemyHp = -ENEMY_HP_WEIGHT * enemy.hp() + ENEMY_HP_LOW_EXTRA * Math.max(0, ENEMY_HP_LOW - enemy.hp());

		double ownBoard = boardValue(match, perspective, ownHpLow);
		double enemyBoard = -boardValue(match, perspective.opponent(), false)
				* (ownHpLow ? ENEMY_BOARD_THREAT_FACTOR : 1.0);

		double cards = HAND_CARD_VALUE * (own.hand().size() - enemy.hand().size())
				- (own.deck().isEmpty() ? EMPTY_DECK_PENALTY : 0) + (enemy.deck().isEmpty() ? EMPTY_DECK_PENALTY : 0);
		double resources = BUDGET_CURRENT_WEIGHT * own.budgetCurrent() + BUDGET_MAX_WEIGHT * own.budgetMax();

		return new Score(outcome, ownHp, enemyHp, ownBoard, enemyBoard, cards, resources);
	}

	private static double boardValue(Match match, Side owner, boolean extraGuard) {
		List<ActiveEvent> playerEvents = match.player().activeEvents();
		List<ActiveEvent> opponentEvents = match.opponent().activeEvents();
		double total = 0;
		for (BoardCharacter character : match.side(owner).board()) {
			CharacterStats stats = CharacterStats.of(character, owner, playerEvents, opponentEvents);
			total += characterValue(stats, character.damage(), extraGuard);
		}
		return total;
	}

	static double characterValue(CharacterStats stats, int damage, boolean extraGuard) {
		double health = Math.max(0, stats.health() - DAMAGE_DISCOUNT * damage);
		double value = CHARACTER_BASE + ATTACK_WEIGHT * stats.attack() + HEALTH_WEIGHT * health;
		if (stats.has(AbilityKeyword.GUARD)) {
			value += GUARD_VALUE + (extraGuard ? GUARD_LOW_HP_EXTRA : 0);
		}
		if (stats.has(AbilityKeyword.OVERTIME)) {
			value += OVERTIME_VALUE;
		}
		return stats.trapped() ? value * TRAPPED_FACTOR : value;
	}

}
