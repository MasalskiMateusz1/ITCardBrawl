package com.example.itcardbrawl.game;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import com.example.itcardbrawl.game.Match.Side;
import com.example.itcardbrawl.game.Match.Status;

import org.junit.jupiter.api.Test;

class MatchStoreTests {

	private static final String KEY = "github:gracz";

	private static final Faction FACTION = new Faction("test", new LocalizedText("Test", "Test"),
			new LocalizedText("Test", "Test"), true);

	@Test
	void parallelUpdatesDoNotLoseChanges() throws Exception {
		MatchStore store = new MatchStore();
		store.save(KEY, match(0));
		int updatesPerThread = 5_000;
		CountDownLatch start = new CountDownLatch(1);
		Runnable worker = () -> {
			try {
				start.await();
			}
			catch (InterruptedException ex) {
				Thread.currentThread().interrupt();
				return;
			}
			for (int i = 0; i < updatesPerThread; i++) {
				store.update(KEY, MatchStoreTests::nextTurn);
			}
		};

		try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
			Future<?> first = executor.submit(worker);
			Future<?> second = executor.submit(worker);
			start.countDown();
			first.get();
			second.get();
		}

		assertThat(store.findCurrent(KEY)).get().extracting(Match::turnNumber).isEqualTo(2 * updatesPerThread);
	}

	@Test
	void exceptionInChangeKeepsPreviousMatchAndPropagates() {
		MatchStore store = new MatchStore();
		Match before = match(3);
		store.save(KEY, before);

		assertThatThrownBy(() -> store.update(KEY, m -> {
			throw new IllegalMoveException("Odrzucony ruch");
		})).isInstanceOf(IllegalMoveException.class);

		assertThat(store.findCurrent(KEY)).containsSame(before);
	}

	@Test
	void updateReturnsNewStateAndStoresIt() {
		MatchStore store = new MatchStore();
		store.save(KEY, match(1));

		Match updated = store.update(KEY, MatchStoreTests::nextTurn).orElseThrow();

		assertThat(updated.turnNumber()).isEqualTo(2);
		assertThat(store.findCurrent(KEY)).containsSame(updated);
	}

	@Test
	void changeIsRetriedOnTheNewStateWhenAnotherWriteWins() {
		MatchStore store = new MatchStore();
		store.save(KEY, match(1));
		Match concurrent = match(10);
		AtomicInteger calls = new AtomicInteger();

		Match updated = store.update(KEY, m -> {
			if (calls.incrementAndGet() == 1) {
				// Another write wins the race while the first attempt is still computing.
				store.save(KEY, concurrent);
			}
			return nextTurn(m);
		}).orElseThrow();

		assertThat(calls).hasValue(2);
		assertThat(updated.id()).isEqualTo(concurrent.id());
		assertThat(updated.turnNumber()).isEqualTo(11);
		assertThat(store.findCurrent(KEY)).containsSame(updated);
	}

	@Test
	void updateWithoutMatchIsEmptyAndDoesNotCallChange() {
		MatchStore store = new MatchStore();

		assertThat(store.update(KEY, m -> {
			throw new AssertionError("Zmiana nie powinna zostać wywołana");
		})).isEmpty();
		assertThat(store.findCurrent(KEY)).isEmpty();
	}

	private static Match nextTurn(Match match) {
		return new Match(match.id(), match.turnNumber() + 1, match.activeSide(), match.player(), match.opponent(),
				match.status(), match.result(), match.log());
	}

	private static Match match(int turnNumber) {
		MatchSide side = new MatchSide(FACTION, List.of(), List.of(), MatchRules.MAX_HP, 0, 0, List.of(), List.of(), List.of());
		return new Match(UUID.randomUUID(), turnNumber, Side.PLAYER, side, side, Status.IN_PROGRESS, null, List.of());
	}

}
