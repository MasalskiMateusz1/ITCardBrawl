package com.example.itcardbrawl.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.servlet.http.Cookie;

import com.example.itcardbrawl.game.Card;
import com.example.itcardbrawl.game.CardCatalog;
import com.example.itcardbrawl.game.Language;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@SpringBootTest
@AutoConfigureMockMvc
class MatchControllerTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ClientRegistrationRepository clientRegistrations;

	@Autowired
	private CardCatalog catalog;

	// Magazyn partii jest współdzielony w kontekście Springa - każdy test używa własnego gracza.
	// oauth2Login() bierze nazwę principala z atrybutu "sub".
	private RequestPostProcessor player(String name) {
		return oauth2Login()
			.clientRegistration(clientRegistrations.findByRegistrationId("github"))
			.attributes(a -> a.put("sub", name));
	}

	private RequestPostProcessor newPlayer() {
		return player("player-" + UUID.randomUUID());
	}

	// Token CSRF jak w SPA (cookie XSRF-TOKEN -> naglowek X-XSRF-TOKEN). Celowo bez post-procesora csrf():
	// podmienia on repozytorium tokenow we wspoldzielonym kontekscie (zob. SecurityFlowTests).
	private MockHttpServletRequestBuilder postMatches() throws Exception {
		return csrfPost("/api/matches");
	}

	private MockHttpServletRequestBuilder csrfPost(String url) throws Exception {
		Cookie xsrf = mockMvc.perform(get("/api/factions").with(newPlayer()))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getCookie("XSRF-TOKEN");
		assertThat(xsrf).isNotNull();
		return post(url).cookie(xsrf).header("X-XSRF-TOKEN", xsrf.getValue())
			.contentType(MediaType.APPLICATION_JSON);
	}

	private MockHttpServletRequestBuilder startMatch(String body) throws Exception {
		return postMatches().content(body);
	}

	private DocumentContext start(RequestPostProcessor player, String factionId) throws Exception {
		String json = mockMvc.perform(startMatch("{\"factionId\":\"" + factionId + "\"}").with(player))
			.andExpect(status().isCreated())
			.andReturn().getResponse().getContentAsString();
		return JsonPath.parse(json);
	}

	@Test
	void factionsListsOnlyPlayableFactions() throws Exception {
		mockMvc.perform(get("/api/factions").with(newPlayer()))
			.andExpect(status().isOk())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
			.andExpect(jsonPath("$", hasSize(2)))
			.andExpect(jsonPath("$[*].id", containsInAnyOrder("developers", "testers")))
			.andExpect(jsonPath("$[*].id", not(hasItem("neutral"))))
			.andExpect(jsonPath("$[0].name").isNotEmpty())
			.andExpect(jsonPath("$[0].description").isNotEmpty());
	}

	@Test
	void factionsWithoutLanguageHeaderArePolish() throws Exception {
		mockMvc.perform(get("/api/factions").with(newPlayer()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[*].name", containsInAnyOrder("Developerzy", "Testerzy")));
	}

	@Test
	void localizedResponsesVaryByAcceptLanguage() throws Exception {
		mockMvc.perform(get("/api/factions").with(newPlayer()).header(HttpHeaders.ACCEPT_LANGUAGE, "en"))
			.andExpect(status().isOk())
			.andExpect(header().stringValues(HttpHeaders.VARY, hasItem(HttpHeaders.ACCEPT_LANGUAGE)));
	}

	@Test
	void factionsInEnglishWhenRequested() throws Exception {
		mockMvc.perform(get("/api/factions").with(newPlayer()).header(HttpHeaders.ACCEPT_LANGUAGE, "en"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[*].name", containsInAnyOrder("Developers", "Testers")))
			.andExpect(jsonPath("$[0].id").value("developers"))
			.andExpect(jsonPath("$[0].description")
				.value(catalog.faction("developers").orElseThrow().description().in(Language.EN)));
	}

	@Test
	void unsupportedPreferredLanguageFallsBackToSupportedEnglish() throws Exception {
		mockMvc.perform(get("/api/factions").with(newPlayer()).header(HttpHeaders.ACCEPT_LANGUAGE, "de, en;q=0.9"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[*].name", containsInAnyOrder("Developers", "Testers")));
	}

	@Test
	void unsupportedLanguageOnlyFallsBackToPolish() throws Exception {
		mockMvc.perform(get("/api/factions").with(newPlayer()).header(HttpHeaders.ACCEPT_LANGUAGE, "de"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[*].name", containsInAnyOrder("Developerzy", "Testerzy")));
	}

	@Test
	void startInEnglishReturnsEnglishTable() throws Exception {
		String json = mockMvc.perform(startMatch("{\"factionId\":\"developers\"}").with(newPlayer())
				.header(HttpHeaders.ACCEPT_LANGUAGE, "en"))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.player.faction.name").value("Developers"))
			.andExpect(jsonPath("$.opponent.faction.name").value("Testers"))
			.andReturn().getResponse().getContentAsString();

		assertHandIn(JsonPath.parse(json), Language.EN);
	}

	@Test
	void sameMatchCanBeReadInBothLanguages() throws Exception {
		RequestPostProcessor player = newPlayer();
		start(player, "developers");

		DocumentContext polish = current(player, "pl");
		DocumentContext english = current(player, "en");

		assertThat((String) english.read("$.matchId")).isEqualTo(polish.read("$.matchId"));
		List<Integer> polishIds = polish.read("$.player.hand[*].instanceId");
		List<Integer> englishIds = english.read("$.player.hand[*].instanceId");
		assertThat(englishIds).isEqualTo(polishIds);
		assertThat((String) polish.read("$.player.faction.name")).isEqualTo("Developerzy");
		assertThat((String) english.read("$.player.faction.name")).isEqualTo("Developers");
		assertThat((String) english.read("$.player.hand[0].text"))
			.isNotEqualTo(polish.read("$.player.hand[0].text"));
		assertHandIn(polish, Language.PL);
		assertHandIn(english, Language.EN);
	}

	private DocumentContext current(RequestPostProcessor player, String language) throws Exception {
		String json = mockMvc.perform(get("/api/matches/current").with(player)
				.header(HttpHeaders.ACCEPT_LANGUAGE, language))
			.andExpect(status().isOk())
			.andReturn().getResponse().getContentAsString();
		return JsonPath.parse(json);
	}

	// Każda karta ręki ma nazwę i tekst z katalogu w danym języku.
	private void assertHandIn(DocumentContext match, Language language) {
		List<String> cardIds = match.read("$.player.hand[*].cardId");
		List<String> names = match.read("$.player.hand[*].name");
		List<String> texts = match.read("$.player.hand[*].text");
		assertThat(cardIds).isNotEmpty();
		for (int i = 0; i < cardIds.size(); i++) {
			Card card = catalog.card(cardIds.get(i)).orElseThrow();
			assertThat(names.get(i)).isEqualTo(card.name().in(language));
			assertThat(texts.get(i)).isEqualTo(card.text().in(language));
		}
	}

	@Test
	void startReturns201WithDealtTable() throws Exception {
		mockMvc.perform(startMatch("{\"factionId\":\"developers\"}").with(newPlayer()))
			.andExpect(status().isCreated())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
			.andExpect(jsonPath("$.matchId").isNotEmpty())
			.andExpect(jsonPath("$.status").value("IN_PROGRESS"))
			.andExpect(jsonPath("$.result").value(nullValue()))
			.andExpect(jsonPath("$.turn.number").isNumber())
			.andExpect(jsonPath("$.player.faction.id").value("developers"))
			.andExpect(jsonPath("$.player.faction.name").value("Developerzy"))
			.andExpect(jsonPath("$.opponent.faction.id").value("testers"))
			.andExpect(jsonPath("$.player.hand").isArray())
			.andExpect(jsonPath("$.player.deckCount").isNumber())
			.andExpect(jsonPath("$.opponent.deckCount").value(25))
			.andExpect(jsonPath("$.opponent.handCount").isNumber())
			// Stres gracza może już rosnąć, jeśli zaczął komputer (zdolność On play).
			.andExpect(jsonPath("$.player.stress").isNumber())
			.andExpect(jsonPath("$.player.stressLimit").value(30))
			.andExpect(jsonPath("$.opponent.stress").value(0))
			.andExpect(jsonPath("$.opponent.stressLimit").value(30))
			.andExpect(jsonPath("$.player.board").isArray())
			.andExpect(jsonPath("$.player.activeEvents").isArray())
			.andExpect(jsonPath("$.player.graveyardCount").isNumber())
			.andExpect(jsonPath("$.opponent.board").isArray())
			.andExpect(jsonPath("$.opponent.activeEvents").isArray())
			.andExpect(jsonPath("$.opponent.graveyardCount").isNumber())
			.andExpect(jsonPath("$.log").isNotEmpty())
			.andExpect(jsonPath("$.log[0].side").isNotEmpty())
			.andExpect(jsonPath("$.log[0].text").isNotEmpty())
			.andExpect(jsonPath("$.player.hand[0].instanceId").isNumber())
			.andExpect(jsonPath("$.player.hand[0].cardId").isNotEmpty())
			.andExpect(jsonPath("$.player.hand[0].name").isNotEmpty())
			.andExpect(jsonPath("$.player.hand[0].type").isNotEmpty())
			.andExpect(jsonPath("$.player.hand[0].cost").isNumber())
			.andExpect(jsonPath("$.player.hand[0].text").exists())
			.andExpect(jsonPath("$.player.hand[0].neutral").isBoolean());
	}

	// Kto zaczyna, jest losowe. Gdy zaczyna komputer, start rozgrywa od razu jego turę 1 — gracz zawsze
	// dostaje stół w swojej turze: tura 1 z ręką 5 albo tura 2 po dobraniu szóstej karty.
	@Test
	void startAlwaysHandsTurnToPlayer() throws Exception {
		DocumentContext match = start(newPlayer(), "testers");

		assertThat((String) match.read("$.turn.active")).isEqualTo("PLAYER");
		assertThat((Integer) match.read("$.player.resource.current")).isEqualTo(1);
		assertThat((Integer) match.read("$.player.resource.max")).isEqualTo(1);
		int turn = match.read("$.turn.number");
		assertThat(turn).isIn(1, 2);
		if (turn == 1) {
			assertThat((Integer) match.read("$.opponent.resource.max")).isZero();
			assertThat((List<?>) match.read("$.player.hand")).hasSize(5);
			assertThat((Integer) match.read("$.player.deckCount")).isEqualTo(25);
		}
		else {
			assertThat((Integer) match.read("$.opponent.resource.max")).isEqualTo(1);
			assertThat((List<?>) match.read("$.player.hand")).hasSize(6);
			assertThat((Integer) match.read("$.player.deckCount")).isEqualTo(24);
		}
	}

	@Test
	void responseDoesNotRevealOpponentCards() throws Exception {
		RequestPostProcessor player = newPlayer();
		mockMvc.perform(startMatch("{\"factionId\":\"developers\"}").with(player))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.opponent.hand").doesNotExist())
			.andExpect(jsonPath("$.opponent.deck").doesNotExist())
			.andExpect(jsonPath("$.player.deck").doesNotExist());

		mockMvc.perform(get("/api/matches/current").with(player))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.opponent.hand").doesNotExist())
			.andExpect(jsonPath("$.opponent.deck").doesNotExist())
			.andExpect(jsonPath("$.player.deck").doesNotExist());
	}

	@Test
	void currentReturnsStartedMatch() throws Exception {
		RequestPostProcessor player = newPlayer();
		DocumentContext started = start(player, "developers");
		String matchId = started.read("$.matchId");
		Integer firstCard = started.read("$.player.hand[0].instanceId");

		mockMvc.perform(get("/api/matches/current").with(player))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.matchId").value(matchId))
			.andExpect(jsonPath("$.player.hand[0].instanceId").value(firstCard));
	}

	@Test
	void secondStartReplacesMatch() throws Exception {
		RequestPostProcessor player = newPlayer();
		String firstId = start(player, "developers").read("$.matchId");
		String secondId = start(player, "testers").read("$.matchId");

		assertThat(secondId).isNotEqualTo(firstId);
		mockMvc.perform(get("/api/matches/current").with(player))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.matchId").value(secondId))
			.andExpect(jsonPath("$.player.faction.id").value("testers"));
	}

	@Test
	void currentWithoutMatchReturns404() throws Exception {
		mockMvc.perform(get("/api/matches/current").with(newPlayer()))
			.andExpect(status().isNotFound());
	}

	@Test
	void neutralFactionIsRejected() throws Exception {
		mockMvc.perform(startMatch("{\"factionId\":\"neutral\"}").with(newPlayer()))
			.andExpect(status().isBadRequest());
	}

	@Test
	void unknownFactionIsRejected() throws Exception {
		mockMvc.perform(startMatch("{\"factionId\":\"managers\"}").with(newPlayer()))
			.andExpect(status().isBadRequest());
	}

	@Test
	void missingFactionIsRejected() throws Exception {
		mockMvc.perform(startMatch("{}").with(newPlayer()))
			.andExpect(status().isBadRequest());
	}

	@Test
	void missingBodyIsRejected() throws Exception {
		mockMvc.perform(postMatches().with(newPlayer()))
			.andExpect(status().isBadRequest());
	}

	@Test
	void rejectedStartLeavesNoMatch() throws Exception {
		RequestPostProcessor player = newPlayer();
		mockMvc.perform(startMatch("{\"factionId\":\"neutral\"}").with(player))
			.andExpect(status().isBadRequest());
		mockMvc.perform(get("/api/matches/current").with(player))
			.andExpect(status().isNotFound());
	}

	@Test
	void playersHaveIndependentMatches() throws Exception {
		String suffix = UUID.randomUUID().toString();
		RequestPostProcessor alice = player("alice-" + suffix);
		RequestPostProcessor bob = player("bob-" + suffix);

		String aliceMatch = start(alice, "developers").read("$.matchId");
		mockMvc.perform(get("/api/matches/current").with(bob))
			.andExpect(status().isNotFound());

		String bobMatch = start(bob, "testers").read("$.matchId");
		assertThat(bobMatch).isNotEqualTo(aliceMatch);

		mockMvc.perform(get("/api/matches/current").with(alice))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.matchId").value(aliceMatch))
			.andExpect(jsonPath("$.player.faction.id").value("developers"));
		mockMvc.perform(get("/api/matches/current").with(bob))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.matchId").value(bobMatch))
			.andExpect(jsonPath("$.player.faction.id").value("testers"));
	}

	@Test
	void unauthenticatedRequestsReturn401() throws Exception {
		mockMvc.perform(get("/api/factions"))
			.andExpect(status().isUnauthorized())
			.andExpect(header().doesNotExist("Location"));
		mockMvc.perform(get("/api/matches/current"))
			.andExpect(status().isUnauthorized())
			.andExpect(header().doesNotExist("Location"));
		mockMvc.perform(startMatch("{\"factionId\":\"developers\"}"))
			.andExpect(status().isUnauthorized())
			.andExpect(header().doesNotExist("Location"));
	}

	@Test
	void startWithoutCsrfReturns403() throws Exception {
		RequestPostProcessor player = newPlayer();
		mockMvc.perform(post("/api/matches").with(player)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"factionId\":\"developers\"}"))
			.andExpect(status().isForbidden());
		mockMvc.perform(get("/api/matches/current").with(player))
			.andExpect(status().isNotFound());
	}

	// --- Ruchy ---

	private MockHttpServletRequestBuilder movePost(String matchId, String move, String body) throws Exception {
		return csrfPost("/api/matches/" + matchId + "/" + move).content(body);
	}

	private DocumentContext move(RequestPostProcessor player, String matchId, String move, String body)
			throws Exception {
		String json = mockMvc.perform(movePost(matchId, move, body).with(player))
			.andExpect(status().isOk())
			.andReturn().getResponse().getContentAsString();
		return JsonPath.parse(json);
	}

	private DocumentContext endTurn(RequestPostProcessor player, DocumentContext match) throws Exception {
		return move(player, match.read("$.matchId"), "end-turn", "{\"turn\":" + match.read("$.turn.number") + "}");
	}

	private static String playBody(DocumentContext match, int instanceId) {
		return "{\"turn\":" + match.read("$.turn.number") + ",\"instanceId\":" + instanceId + "}";
	}

	// Pierwsza karta z ręki, która jest (albo nie jest) do opłacenia bieżącym zasobem gracza; null, gdy brak.
	private static Map<String, Object> handCard(DocumentContext match, boolean affordable) {
		int resource = match.read("$.player.resource.current");
		List<Map<String, Object>> hand = match.read("$.player.hand");
		return hand.stream()
			.filter(card -> ((Integer) card.get("cost") <= resource) == affordable)
			.findFirst()
			.orElse(null);
	}

	@Test
	void playAffordableCardMovesItFromHandToTable() throws Exception {
		RequestPostProcessor player = newPlayer();
		DocumentContext match = start(player, "developers");
		// Zasób rośnie co turę - kończymy tury, aż na ręce pojawi się karta do zagrania.
		while (handCard(match, true) == null) {
			assertThat((String) match.read("$.status")).isEqualTo("IN_PROGRESS");
			match = endTurn(player, match);
		}
		Map<String, Object> card = handCard(match, true);
		int instanceId = (Integer) card.get("instanceId");
		int graveyardBefore = match.read("$.player.graveyardCount");

		DocumentContext after = move(player, match.read("$.matchId"), "play", playBody(match, instanceId));

		List<Integer> hand = after.read("$.player.hand[*].instanceId");
		assertThat(hand).doesNotContain(instanceId);
		List<Integer> board = after.read("$.player.board[*].instanceId");
		List<Integer> events = after.read("$.player.activeEvents[*].instanceId");
		int graveyardAfter = after.read("$.player.graveyardCount");
		if ("CHARACTER".equals(card.get("type"))) {
			assertThat(board).contains(instanceId);
			List<String> names = after.read("$.player.board[?(@.instanceId == " + instanceId + ")].name");
			assertThat(names).containsExactly((String) card.get("name"));
		}
		else {
			assertThat(events.contains(instanceId) || graveyardAfter == graveyardBefore + 1).isTrue();
		}
		List<String> log = after.read("$.log[*].text");
		assertThat(log).contains("Zagrywasz: " + card.get("name"));
	}

	@Test
	void unaffordableCardReturns400AndLeavesStateUnchanged() throws Exception {
		RequestPostProcessor player = newPlayer();
		DocumentContext match = start(player, "developers");
		// Na starcie zasób to 1, a talia ma najwyżej 3 karty za 1 - na ręce zawsze jest karta za droga.
		Map<String, Object> card = handCard(match, false);
		assertThat(card).isNotNull();
		int instanceId = (Integer) card.get("instanceId");

		mockMvc.perform(movePost(match.read("$.matchId"), "play", playBody(match, instanceId)).with(player))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").isNotEmpty());

		List<Integer> hand = current(player, "pl").read("$.player.hand[*].instanceId");
		assertThat(hand).contains(instanceId);
	}

	@Test
	void attackWithUnknownCharacterReturns400() throws Exception {
		RequestPostProcessor player = newPlayer();
		DocumentContext match = start(player, "testers");
		String body = "{\"turn\":" + match.read("$.turn.number") + ",\"attackerId\":-1,\"targetId\":null}";

		mockMvc.perform(movePost(match.read("$.matchId"), "attack", body).with(player))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").isNotEmpty());
	}

	@Test
	void wrongTurnReturns409AndLeavesStateUnchanged() throws Exception {
		RequestPostProcessor player = newPlayer();
		DocumentContext match = start(player, "developers");
		int turn = match.read("$.turn.number");

		mockMvc.perform(movePost(match.read("$.matchId"), "end-turn", "{\"turn\":" + (turn + 1) + "}").with(player))
			.andExpect(status().isConflict());

		DocumentContext after = current(player, "pl");
		assertThat((Integer) after.read("$.turn.number")).isEqualTo(turn);
		assertThat((String) after.read("$.turn.active")).isEqualTo("PLAYER");
	}

	@Test
	void foreignMatchIdReturns409() throws Exception {
		RequestPostProcessor player = newPlayer();
		DocumentContext match = start(player, "developers");
		String body = "{\"turn\":" + match.read("$.turn.number") + "}";

		mockMvc.perform(movePost(UUID.randomUUID().toString(), "end-turn", body).with(player))
			.andExpect(status().isConflict());
	}

	@Test
	void moveWithoutMatchReturns404() throws Exception {
		mockMvc.perform(movePost(UUID.randomUUID().toString(), "end-turn", "{\"turn\":1}").with(newPlayer()))
			.andExpect(status().isNotFound());
	}

	@Test
	void moveWithoutCsrfReturns403() throws Exception {
		RequestPostProcessor player = newPlayer();
		DocumentContext match = start(player, "developers");
		int turn = match.read("$.turn.number");

		mockMvc.perform(post("/api/matches/" + match.read("$.matchId") + "/end-turn").with(player)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"turn\":" + turn + "}"))
			.andExpect(status().isForbidden());
		assertThat((Integer) current(player, "pl").read("$.turn.number")).isEqualTo(turn);
	}

	@Test
	void missingOrMalformedMoveBodyReturns400() throws Exception {
		RequestPostProcessor player = newPlayer();
		String matchId = start(player, "developers").read("$.matchId");

		mockMvc.perform(csrfPost("/api/matches/" + matchId + "/end-turn").with(player))
			.andExpect(status().isBadRequest());
		mockMvc.perform(movePost(matchId, "play", "{}").with(player))
			.andExpect(status().isBadRequest());
		mockMvc.perform(movePost(matchId, "attack", "{\"turn\":\"abc\"}").with(player))
			.andExpect(status().isBadRequest());
	}

	@Test
	void endTurnPlaysOpponentTurnAndReturnsToPlayer() throws Exception {
		RequestPostProcessor player = newPlayer();
		DocumentContext match = start(player, "developers");
		int turn = match.read("$.turn.number");

		DocumentContext after = endTurn(player, match);

		List<String> sides = after.read("$.log[*].side");
		List<String> texts = after.read("$.log[*].text");
		assertThat(texts).contains("Kończysz turę");
		assertThat(sides).contains("OPPONENT");
		if ("FINISHED".equals(after.read("$.status"))) {
			assertThat((String) after.read("$.result.winner")).isIn("PLAYER", "OPPONENT");
			return;
		}
		assertThat((String) after.read("$.turn.active")).isEqualTo("PLAYER");
		assertThat((Integer) after.read("$.turn.number")).isEqualTo(turn + 2);
		assertThat(texts).contains("Tura przeciwnika " + (turn + 1), "Przeciwnik dobiera kartę",
				"Przeciwnik kończy turę", "Twoja tura " + (turn + 2));
		assertThat((Object) after.read("$.result")).isNull();
	}

	// Przeciwnik dobiera w swojej turze - log mówi tylko, że dobrał, a widok nie ma jego ręki ani talii.
	@Test
	void viewDoesNotRevealOpponentHandOrDraws() throws Exception {
		RequestPostProcessor player = newPlayer();
		DocumentContext after = endTurn(player, start(player, "developers"));

		Map<String, Object> opponent = after.read("$.opponent");
		assertThat(opponent).doesNotContainKeys("hand", "deck");
		List<String> opponentTexts = after.read("$.log[?(@.side == 'OPPONENT')].text");
		assertThat(opponentTexts).noneMatch(text -> text.startsWith("Dobierasz"));
		assertThat(opponentTexts).filteredOn(text -> text.contains("dobiera"))
			.containsOnly("Przeciwnik dobiera kartę");
	}

	// Kroki tury komputera: lekkie stany bez logu, z rosnącą liczbą widocznych wpisów i bez ręki przeciwnika.
	@Test
	void endTurnReturnsReplayStepsWithGrowingLogSize() throws Exception {
		RequestPostProcessor player = newPlayer();
		DocumentContext after = endTurn(player, start(player, "developers"));

		List<Map<String, Object>> steps = after.read("$.replay");
		List<?> log = after.read("$.log");
		assertThat(steps).hasSizeGreaterThanOrEqualTo(2);
		List<Integer> logSizes = after.read("$.replay[*].logSize");
		assertThat(logSizes).isSorted();
		assertThat(logSizes.getFirst()).isLessThan(logSizes.getLast());
		assertThat(logSizes.getLast()).isLessThanOrEqualTo(log.size());
		assertThat((String) after.read("$.replay[0].turn.active")).isEqualTo("OPPONENT");
		for (Map<String, Object> step : steps) {
			assertThat(step).containsKeys("turn", "status", "result", "player", "opponent", "logSize")
				.doesNotContainKeys("log", "replay", "matchId");
			@SuppressWarnings("unchecked")
			Map<String, Object> opponent = (Map<String, Object>) step.get("opponent");
			assertThat(opponent).containsKey("handCount").doesNotContainKeys("hand", "deck");
		}
	}

	@Test
	void playAndCurrentReturnEmptyReplay() throws Exception {
		RequestPostProcessor player = newPlayer();
		DocumentContext match = start(player, "developers");
		while (handCard(match, true) == null) {
			match = endTurn(player, match);
		}
		int instanceId = (Integer) handCard(match, true).get("instanceId");

		DocumentContext played = move(player, match.read("$.matchId"), "play", playBody(match, instanceId));

		assertThat((List<?>) played.read("$.replay")).isEmpty();
		mockMvc.perform(get("/api/matches/current").with(player))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.replay").isArray())
			.andExpect(jsonPath("$.replay", hasSize(0)));
	}

	@Test
	void sameLogCanBeReadInBothLanguages() throws Exception {
		RequestPostProcessor player = newPlayer();
		endTurn(player, start(player, "developers"));

		DocumentContext polish = current(player, "pl");
		DocumentContext english = current(player, "en");

		List<String> polishSides = polish.read("$.log[*].side");
		List<String> englishSides = english.read("$.log[*].side");
		List<String> polishTexts = polish.read("$.log[*].text");
		List<String> englishTexts = english.read("$.log[*].text");
		assertThat(englishSides).isEqualTo(polishSides);
		assertThat(englishTexts).hasSameSizeAs(polishTexts);
		for (int i = 0; i < polishTexts.size(); i++) {
			assertThat(englishTexts.get(i)).isNotBlank().isNotEqualTo(polishTexts.get(i));
		}
		assertThat(polishTexts).contains("Kończysz turę");
		assertThat(englishTexts).contains("You end your turn");
	}

}
