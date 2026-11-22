package com.example.itcardbrawl.game;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.SplittableRandom;
import java.util.TreeMap;
import java.util.random.RandomGenerator;

import com.example.itcardbrawl.game.Match.Side;
import com.example.itcardbrawl.game.Match.Status;
import com.example.itcardbrawl.game.MatchResult.EndReason;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import tools.jackson.databind.json.JsonMapper;

/**
 * Balance simulation: the computer plays both sides of seeded matches on the real catalog, and the test writes a
 * report to {@code target/balance/} ({@code matches.csv}, {@code cards.csv}, {@code summary.md}).
 *
 * <p>Balance gates: every match finishes before {@link #MAX_TURNS} with a result, the PLAYER faction win rate of each
 * of the 4 cells is within [{@link #CELL_MIN_WIN_RATE}, {@link #CELL_MAX_WIN_RATE}], and the p90 of the estimated
 * match time is at most {@link #MAX_P90_MATCH_SECONDS}. The aggro test deck is report only. The report files are
 * written before the assertions, so they exist on failure too.
 *
 * <p>Matches are split evenly into 4 cells: PLAYER faction (developers, testers) x starting side (PLAYER, OPPONENT).
 * {@code matches.csv} and {@code cards.csv} depend only on the seeds (no wall-clock values), so two runs give identical
 * files; the runtime goes only to {@code summary.md}.
 */
class BalanceSimulationTests {

	private static final CardCatalog CATALOG = CardCatalogLoader.load(JsonMapper.builder().build(),
			new ClassPathResource(CardCatalogLoader.CATALOG_LOCATION));

	private static final MatchEngine ENGINE = new MatchEngine();

	private static final PositionEvaluator EVALUATOR = new PositionEvaluator();

	private static final List<String> FACTIONS = List.of("developers", "testers");

	private static final List<Side> STARTING_SIDES = List.of(Side.PLAYER, Side.OPPONENT);

	/**
	 * Number of simulated matches (divisible by 4: equal cells). Measured on the local machine: 1000 matches take
	 * about 1.2-1.4 s (plus 0.3 s for the aggro matches; see "Pomiar balansu" in card-pricing.md), well within the
	 * 120 s budget, so N stays at 1000.
	 */
	private static final int MATCHES = 1000;

	/** Matches of the aggro test deck against the real decks (report only), divisible by 4. */
	private static final int AGGRO_MATCHES = 200;

	/** Seed offset of the aggro matches, so that they never reuse the seeds of the main simulation. */
	private static final long AGGRO_SEED_OFFSET = 100_000;

	// Seed offsets of the two computers, so that the deck shuffle and both players draw from different streams.
	private static final long PLAYER_SEED_OFFSET = 1_000_000;

	private static final long OPPONENT_SEED_OFFSET = 2_000_000;

	// Turn limit for one match: 30-card decks end the match around turn 60 at the latest.
	private static final int MAX_TURNS = 200;

	// Balance gates and the match time estimate: see "Pomiar balansu" in context/foundation/card-pricing.md
	// (45-55% is the orientation target, [40%, 60%] is the hard gate: the 95% CI at 250 matches per cell is about
	// +-6 p.p.).

	/** Lowest allowed PLAYER faction win rate in a cell (faction x starting side). */
	static final double CELL_MIN_WIN_RATE = 0.40;

	/** Highest allowed PLAYER faction win rate in a cell (faction x starting side). */
	static final double CELL_MAX_WIN_RATE = 0.60;

	/** Highest allowed p90 of the estimated match time (15 min). */
	static final double MAX_P90_MATCH_SECONDS = 900;

	/** Assumed duration of a human turn, for the estimated match length. */
	static final double ASSUMED_HUMAN_TURN_SECONDS = 20;

	/** Duration of one replayed computer step in the SPA (REPLAY_STEP_MS, mirrored by MatchRules.REPLAY_STEP). */
	private static final double REPLAY_STEP_SECONDS = MatchRules.REPLAY_STEP.toMillis() / 1000.0;

	/** z for the 95% Wilson score interval. */
	private static final double Z_95 = 1.96;

	private static final int AGGRO_MAX_COST = 3;

	private static final String AGGRO_FACTION = "aggro";

	private static final Path OUTPUT = Path.of("target", "balance");

	@Test
	void simulatesComputerAgainstComputerAndWritesReport() throws IOException {
		long start = System.nanoTime();
		List<Played> matches = new ArrayList<>();
		for (long seed = 0; seed < MATCHES; seed++) {
			int cell = (int) (seed % 4);
			matches.add(play(CATALOG, seed, FACTIONS.get(cell % 2), STARTING_SIDES.get(cell / 2)));
		}
		long mainNanos = System.nanoTime() - start;

		long aggroStart = System.nanoTime();
		List<Played> aggro = new ArrayList<>();
		for (int i = 0; i < AGGRO_MATCHES; i++) {
			int cell = i % 4;
			String real = FACTIONS.get(cell % 2);
			aggro.add(play(aggroCatalog(real), AGGRO_SEED_OFFSET + i, AGGRO_FACTION, STARTING_SIDES.get(cell / 2)));
		}
		long aggroNanos = System.nanoTime() - aggroStart;

		Files.createDirectories(OUTPUT);
		Files.writeString(OUTPUT.resolve("matches.csv"), matchesCsv(matches));
		Files.writeString(OUTPUT.resolve("cards.csv"), cardsCsv(cardStats(matches)));
		Files.writeString(OUTPUT.resolve("summary.md"), summary(matches, aggro, mainNanos, aggroNanos));

		Path report = OUTPUT.resolve("summary.md");
		List<Played> all = new ArrayList<>(matches);
		all.addAll(aggro);
		assertThat(all).as("partie bez wyniku przed turą %d (raport: %s)", MAX_TURNS, report)
			.allSatisfy(played -> {
				assertThat(played.match().status()).as("ziarno %d", played.seed()).isEqualTo(Status.FINISHED);
				assertThat(played.match().result()).as("ziarno %d", played.seed()).isNotNull();
			});

		List<String> cellViolations = new ArrayList<>();
		for (Side starting : STARTING_SIDES) {
			for (String faction : FACTIONS) {
				List<Played> cell = cell(matches, faction, starting);
				int wins = (int) cell.stream().filter(p -> p.winner() == Side.PLAYER).count();
				double winRate = (double) wins / cell.size();
				if (winRate < CELL_MIN_WIN_RATE || winRate > CELL_MAX_WIN_RATE) {
					cellViolations.add("frakcja PLAYER %s, zaczyna %s: win rate %s".formatted(faction, starting,
							percent(wins, cell.size())));
				}
			}
		}
		assertThat(cellViolations)
			.withFailMessage("Komórki poza przedziałem [%s, %s] (raport: %s):%n%s",
					format("%.0f%%", 100 * CELL_MIN_WIN_RATE), format("%.0f%%", 100 * CELL_MAX_WIN_RATE), report,
					String.join("\n", cellViolations))
			.isEmpty();

		double p90Seconds = percentile(matches.stream().map(Played::estimatedSeconds).sorted().toList(), 0.9);
		assertThat(p90Seconds)
			.withFailMessage("p90 szacowanego czasu partii %s s przekracza %s s (raport: %s)",
					format("%.0f", p90Seconds), format("%.0f", MAX_P90_MATCH_SECONDS), report)
			.isLessThanOrEqualTo(MAX_P90_MATCH_SECONDS);
	}

	private static List<Played> cell(List<Played> matches, String playerFaction, Side starting) {
		return matches.stream().filter(p -> p.starting() == starting && p.faction(Side.PLAYER).equals(playerFaction))
			.toList();
	}

	// ---- simulation ----

	/**
	 * One finished (or cut off at {@link #MAX_TURNS}) match. {@code moves} — the number of {@code playTurnSteps} steps
	 * of each side's computer, {@code turns} — the number of turns each side started.
	 */
	private record Played(long seed, Side starting, Match match, Map<Side, Integer> moves, Map<Side, Integer> turns) {

		String faction(Side side) {
			return match.side(side).faction().id();
		}

		Side winner() {
			return match.result() != null ? match.result().winner() : null;
		}

		/**
		 * Estimated duration: the turns of the "human" side x {@link #ASSUMED_HUMAN_TURN_SECONDS} plus the replayed
		 * steps of the "computer" side x {@link #REPLAY_STEP_SECONDS}; each side is the human once, the larger value
		 * counts.
		 */
		double estimatedSeconds() {
			double best = 0;
			for (Side human : Side.values()) {
				best = Math.max(best, turns.get(human) * ASSUMED_HUMAN_TURN_SECONDS
						+ moves.get(human.opponent()) * REPLAY_STEP_SECONDS);
			}
			return best;
		}

	}

	private static Played play(CardCatalog catalog, long seed, String playerFaction, Side starting) {
		Match match = new MatchFactory(catalog, factoryRandom(seed, starting == Side.PLAYER)).create(playerFaction);
		Map<Side, ComputerOpponent> computers = new EnumMap<>(Side.class);
		computers.put(Side.PLAYER,
				new ComputerOpponent(ENGINE, EVALUATOR, new SplittableRandom(seed + PLAYER_SEED_OFFSET), Side.PLAYER));
		computers.put(Side.OPPONENT, new ComputerOpponent(ENGINE, EVALUATOR,
				new SplittableRandom(seed + OPPONENT_SEED_OFFSET), Side.OPPONENT));
		Map<Side, Integer> moves = new EnumMap<>(Map.of(Side.PLAYER, 0, Side.OPPONENT, 0));
		while (match.status() == Status.IN_PROGRESS && match.turnNumber() < MAX_TURNS) {
			Side active = match.activeSide();
			List<Match> steps = computers.get(active).playTurnSteps(match);
			moves.merge(active, steps.size(), Integer::sum);
			match = steps.getLast();
		}
		Map<Side, Integer> turns = new EnumMap<>(Map.of(Side.PLAYER, 0, Side.OPPONENT, 0));
		for (MatchEvent event : match.log()) {
			if (event instanceof MatchEvent.TurnStarted started) {
				turns.merge(started.side(), 1, Integer::sum);
			}
		}
		return new Played(seed, starting, match, moves, turns);
	}

	// Shuffling from the seed, while the choice of who starts (nextBoolean) is fixed by the simulation.
	private static RandomGenerator factoryRandom(long seed, boolean playerStarts) {
		SplittableRandom delegate = new SplittableRandom(seed);
		return new RandomGenerator() {
			@Override
			public long nextLong() {
				return delegate.nextLong();
			}

			@Override
			public boolean nextBoolean() {
				return playerStarts;
			}
		};
	}

	/**
	 * An in-memory catalog with two playable factions: the real {@code realFaction} with its real deck, and the aggro
	 * test faction whose 30-card deck holds only cards of cost 1-{@link #AGGRO_MAX_COST} from the current decks. With
	 * at most 2 copies per entry, such cards are cloned under the aggro faction with ids {@code aggro-<id>-<n>}.
	 */
	private static CardCatalog aggroCatalog(String realFaction) {
		Map<String, Faction> factions = new LinkedHashMap<>();
		Map<String, Card> cards = new LinkedHashMap<>();
		Map<String, Integer> realCopies = new LinkedHashMap<>();
		for (Card card : CATALOG.deck(realFaction)) {
			cards.putIfAbsent(card.id(), card);
			realCopies.merge(card.id(), 1, Integer::sum);
			factions.putIfAbsent(card.faction(), CATALOG.faction(card.faction()).orElseThrow());
		}
		factions.put(AGGRO_FACTION, new Faction(AGGRO_FACTION, new LocalizedText("Aggro", "Aggro"),
				new LocalizedText("Testowa talia aggro", "Aggro test deck"), true));

		List<Card> pool = FACTIONS.stream().flatMap(id -> CATALOG.deck(id).stream())
			.filter(card -> card.cost() <= AGGRO_MAX_COST).distinct()
			.sorted(Comparator.comparingInt(Card::cost).thenComparing(Card::id)).toList();
		List<DeckEntry> aggroDeck = new ArrayList<>();
		for (int i = 0; i < pool.size(); i++) {
			int copies = MatchRules.DECK_SIZE / pool.size() + (i < MatchRules.DECK_SIZE % pool.size() ? 1 : 0);
			Card original = pool.get(i);
			for (int clone = 1; copies > 0; clone++) {
				int entryCopies = Math.min(copies, CardCatalog.MAX_COPIES);
				String id = "%s-%s-%d".formatted(AGGRO_FACTION, original.id(), clone);
				cards.put(id, new Card(id, AGGRO_FACTION, original.name(), original.type(), original.cost(),
						original.text(), original.attack(), original.health(), original.ability(), original.duration(),
						original.effects()));
				aggroDeck.add(new DeckEntry(id, entryCopies));
				copies -= entryCopies;
			}
		}

		// The same second player bonus card as in the real catalog.
		String bonus = CATALOG.secondPlayerBonus().map(card -> {
			cards.putIfAbsent(card.id(), card);
			factions.putIfAbsent(card.faction(), CATALOG.faction(card.faction()).orElseThrow());
			return card.id();
		}).orElse(null);

		Map<String, List<DeckEntry>> decks = new LinkedHashMap<>();
		decks.put(realFaction, realCopies.entrySet().stream().map(e -> new DeckEntry(e.getKey(), e.getValue())).toList());
		decks.put(AGGRO_FACTION, aggroDeck);
		return new CardCatalog(List.copyOf(factions.values()), List.copyOf(cards.values()), decks, bonus);
	}

	// ---- metrics ----

	private static final class CardStat {

		int plays;

		int matches;

		int wins;

	}

	/**
	 * Per card: plays, matches in which a side played it, and that side's wins in those matches. A neutral card played
	 * by both sides in one match counts once for each side.
	 */
	private static Map<String, CardStat> cardStats(List<Played> matches) {
		Map<String, CardStat> stats = new TreeMap<>();
		for (Played played : matches) {
			Map<Side, Map<String, Integer>> bySide = new EnumMap<>(Side.class);
			for (MatchEvent event : played.match().log()) {
				if (event instanceof MatchEvent.CardPlayed cardPlayed) {
					bySide.computeIfAbsent(cardPlayed.side(), side -> new TreeMap<>())
						.merge(cardPlayed.card().card().id(), 1, Integer::sum);
				}
			}
			bySide.forEach((side, counts) -> counts.forEach((id, count) -> {
				CardStat stat = stats.computeIfAbsent(id, key -> new CardStat());
				stat.plays += count;
				stat.matches++;
				if (played.winner() == side) {
					stat.wins++;
				}
			}));
		}
		return stats;
	}

	private static Map<MoveReason, Map<Side, Integer>> reasonCounts(List<Played> matches) {
		Map<MoveReason, Map<Side, Integer>> counts = new EnumMap<>(MoveReason.class);
		for (MoveReason reason : MoveReason.values()) {
			counts.put(reason, new EnumMap<>(Map.of(Side.PLAYER, 0, Side.OPPONENT, 0)));
		}
		for (Played played : matches) {
			for (MatchEvent event : played.match().log()) {
				if (event instanceof MatchEvent.MoveExplained explained) {
					counts.get(explained.reason()).merge(explained.side(), 1, Integer::sum);
				}
			}
		}
		return counts;
	}

	/** Nearest-rank percentile of already sorted values. */
	private static double percentile(List<Double> sorted, double p) {
		if (sorted.isEmpty()) {
			return Double.NaN;
		}
		int rank = (int) Math.ceil(p * sorted.size());
		return sorted.get(Math.clamp(rank - 1, 0, sorted.size() - 1));
	}

	/** 95% Wilson score interval for {@code wins} out of {@code n}. */
	private static double[] wilson(int wins, int n) {
		if (n == 0) {
			return new double[] { Double.NaN, Double.NaN };
		}
		double p = (double) wins / n;
		double z2 = Z_95 * Z_95;
		double center = (p + z2 / (2 * n)) / (1 + z2 / n);
		double half = Z_95 * Math.sqrt(p * (1 - p) / n + z2 / (4.0 * n * n)) / (1 + z2 / n);
		return new double[] { center - half, center + half };
	}

	// ---- output ----

	private static String matchesCsv(List<Played> matches) {
		StringBuilder csv = new StringBuilder("seed,player_faction,opponent_faction,starting_side,winner_side,"
				+ "winner_faction,end_reason,turns,player_turns,opponent_turns,player_moves,opponent_moves,player_hp,"
				+ "opponent_hp,estimated_seconds\n");
		for (Played played : matches) {
			Match match = played.match();
			Side winner = played.winner();
			csv.append(String.join(",", Long.toString(played.seed()), played.faction(Side.PLAYER),
					played.faction(Side.OPPONENT), played.starting().name(), winner != null ? winner.name() : "",
					winner != null ? played.faction(winner) : "",
					match.result() != null ? match.result().reason().name() : "",
					Integer.toString(match.turnNumber()), Integer.toString(played.turns().get(Side.PLAYER)),
					Integer.toString(played.turns().get(Side.OPPONENT)),
					Integer.toString(played.moves().get(Side.PLAYER)),
					Integer.toString(played.moves().get(Side.OPPONENT)), Integer.toString(match.player().hp()),
					Integer.toString(match.opponent().hp()), format("%.1f", played.estimatedSeconds())))
				.append('\n');
		}
		return csv.toString();
	}

	private static String cardsCsv(Map<String, CardStat> stats) {
		StringBuilder csv = new StringBuilder("card_id,plays,matches_played,wins_in_played_matches,win_rate\n");
		stats.forEach((id, stat) -> csv.append(String.join(",", id, Integer.toString(stat.plays),
				Integer.toString(stat.matches), Integer.toString(stat.wins),
				format("%.4f", (double) stat.wins / stat.matches))).append('\n'));
		return csv.toString();
	}

	private static String summary(List<Played> matches, List<Played> aggro, long mainNanos, long aggroNanos) {
		StringBuilder md = new StringBuilder();
		md.append("# Balance simulation\n\n");
		md.append("Computer vs computer on the real catalog. Matches: %d, seeds 0-%d.".formatted(matches.size(),
				matches.size() - 1)).append("\n\n");
		md.append(("Gates: PLAYER faction win rate of every cell in [%s, %s], p90 of the estimated match time <= %s s. "
				+ "The aggro test deck is report only.").formatted(format("%.0f%%", 100 * CELL_MIN_WIN_RATE),
						format("%.0f%%", 100 * CELL_MAX_WIN_RATE), format("%.0f", MAX_P90_MATCH_SECONDS)))
			.append("\n\n");

		md.append("## Cells (win rate of the PLAYER faction)\n\n");
		md.append("| PLAYER faction | OPPONENT faction | Starting side | Matches | PLAYER wins | Win rate | 95% CI |\n");
		md.append("| --- | --- | --- | ---: | ---: | ---: | --- |\n");
		for (Side starting : STARTING_SIDES) {
			for (String faction : FACTIONS) {
				List<Played> cell = cell(matches, faction, starting);
				int wins = (int) cell.stream().filter(p -> p.winner() == Side.PLAYER).count();
				String opponent = cell.isEmpty() ? "-" : cell.getFirst().faction(Side.OPPONENT);
				md.append("| %s | %s | %s | %d | %d | %s | %s |\n".formatted(faction, opponent, starting, cell.size(),
						wins, percent(wins, cell.size()), interval(wins, cell.size())));
			}
		}

		md.append("\n## Win rate per faction\n\n");
		md.append("| Faction | Matches | Wins | Win rate | 95% CI |\n| --- | ---: | ---: | ---: | --- |\n");
		for (String faction : FACTIONS) {
			List<Played> withFaction = matches.stream().filter(p -> p.faction(Side.PLAYER).equals(faction)
					|| p.faction(Side.OPPONENT).equals(faction)).toList();
			int wins = (int) withFaction.stream().filter(p -> p.winner() != null && p.faction(p.winner()).equals(faction))
				.count();
			md.append("| %s | %d | %d | %s | %s |\n".formatted(faction, withFaction.size(), wins,
					percent(wins, withFaction.size()), interval(wins, withFaction.size())));
		}
		int startingWins = (int) matches.stream().filter(p -> p.winner() == p.starting()).count();
		md.append("\nStarting side wins: %d/%d = %s (95%% CI %s).\n".formatted(startingWins, matches.size(),
				percent(startingWins, matches.size()), interval(startingWins, matches.size())));

		md.append("\n## End reasons\n\n| Reason | Matches |\n| --- | ---: |\n");
		for (EndReason reason : EndReason.values()) {
			long count = matches.stream().filter(p -> p.match().result() != null && p.match().result().reason() == reason)
				.count();
			md.append("| %s | %d |\n".formatted(reason, count));
		}
		long unfinished = matches.stream().filter(p -> p.match().result() == null).count();
		md.append("| no result (cut off at turn %d) | %d |\n".formatted(MAX_TURNS, unfinished));

		List<Double> turns = matches.stream().map(p -> (double) p.match().turnNumber()).sorted().toList();
		md.append("\n## Match length in turns\n\n| Median | p90 | Max |\n| ---: | ---: | ---: |\n");
		md.append("| %s | %s | %s |\n".formatted(format("%.0f", percentile(turns, 0.5)),
				format("%.0f", percentile(turns, 0.9)), format("%.0f", turns.isEmpty() ? Double.NaN : turns.getLast())));

		List<Double> seconds = matches.stream().map(Played::estimatedSeconds).sorted().toList();
		md.append("\n## Estimated match time\n\n");
		md.append(("Human turns x %s s + computer steps x %s s (replay), computed with each side as the human; "
				+ "the larger value counts.\n\n").formatted(format("%.0f", ASSUMED_HUMAN_TURN_SECONDS),
						format("%.1f", REPLAY_STEP_SECONDS)));
		md.append("| Median | p90 | Max |\n| ---: | ---: | ---: |\n");
		md.append("| %s | %s | %s |\n".formatted(minutes(percentile(seconds, 0.5)), minutes(percentile(seconds, 0.9)),
				minutes(seconds.isEmpty() ? Double.NaN : seconds.getLast())));

		Map<MoveReason, Map<Side, Integer>> reasons = reasonCounts(matches);
		md.append("\n## Move reasons (MoveReason)\n\n");
		md.append("FAVORABLE_TRADE: %d.\n\n".formatted(
				reasons.get(MoveReason.FAVORABLE_TRADE).values().stream().mapToInt(Integer::intValue).sum()));
		md.append("| Reason | PLAYER | OPPONENT | Total |\n| --- | ---: | ---: | ---: |\n");
		reasons.forEach((reason, bySide) -> md.append("| %s | %d | %d | %d |\n".formatted(reason,
				bySide.get(Side.PLAYER), bySide.get(Side.OPPONENT), bySide.get(Side.PLAYER) + bySide.get(Side.OPPONENT))));

		md.append("\n## Aggro test deck (report only)\n\n");
		md.append(("30 cards of cost 1-%d from the current decks (PLAYER side) against each real deck (OPPONENT side). "
				+ "Matches: %d, seeds %d-%d.\n\n").formatted(AGGRO_MAX_COST, aggro.size(), AGGRO_SEED_OFFSET,
						AGGRO_SEED_OFFSET + aggro.size() - 1));
		md.append("| Opponent deck | Starting side | Matches | Aggro wins | Aggro win rate | Starting side win rate |\n");
		md.append("| --- | --- | ---: | ---: | ---: | ---: |\n");
		for (Side starting : STARTING_SIDES) {
			for (String faction : FACTIONS) {
				List<Played> cell = aggro.stream()
					.filter(p -> p.starting() == starting && p.faction(Side.OPPONENT).equals(faction)).toList();
				int wins = (int) cell.stream().filter(p -> p.winner() == Side.PLAYER).count();
				int startWins = (int) cell.stream().filter(p -> p.winner() == p.starting()).count();
				md.append("| %s | %s | %d | %d | %s | %s |\n".formatted(faction,
						starting == Side.PLAYER ? "aggro" : faction, cell.size(), wins, percent(wins, cell.size()),
						percent(startWins, cell.size())));
			}
		}
		int aggroStartWins = (int) aggro.stream().filter(p -> p.winner() == p.starting()).count();
		int aggroOtherWins = (int) aggro.stream().filter(p -> p.winner() != null && p.winner() != p.starting()).count();
		md.append("\nStarting side: %s, non-starting side: %s.\n".formatted(percent(aggroStartWins, aggro.size()),
				percent(aggroOtherWins, aggro.size())));

		md.append("\n## Runtime\n\n");
		md.append("| Part | Matches | Time |\n| --- | ---: | ---: |\n");
		md.append("| Main simulation | %d | %s s |\n".formatted(matches.size(), format("%.1f", mainNanos / 1e9)));
		md.append("| Aggro deck | %d | %s s |\n".formatted(aggro.size(), format("%.1f", aggroNanos / 1e9)));
		md.append("| Total | %d | %s s |\n".formatted(matches.size() + aggro.size(),
				format("%.1f", (mainNanos + aggroNanos) / 1e9)));
		return md.toString();
	}

	private static String percent(int wins, int n) {
		return n == 0 ? "-" : format("%.1f%%", 100.0 * wins / n);
	}

	private static String interval(int wins, int n) {
		double[] ci = wilson(wins, n);
		return n == 0 ? "-" : format("%.1f%%-%.1f%%", 100 * ci[0], 100 * ci[1]);
	}

	private static String minutes(double seconds) {
		return Double.isNaN(seconds) ? "-" : format("%.0f s (%.1f min)", seconds, seconds / 60);
	}

	private static String format(String pattern, Object... args) {
		return String.format(Locale.ROOT, pattern, args);
	}

}
