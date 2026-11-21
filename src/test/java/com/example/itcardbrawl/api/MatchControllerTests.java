package com.example.itcardbrawl.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import jakarta.servlet.http.Cookie;

import com.example.itcardbrawl.game.BoardCharacter;
import com.example.itcardbrawl.game.Card;
import com.example.itcardbrawl.game.CardCatalog;
import com.example.itcardbrawl.game.CardInstance;
import com.example.itcardbrawl.game.Language;
import com.example.itcardbrawl.game.Match;
import com.example.itcardbrawl.game.MatchEngine;
import com.example.itcardbrawl.game.MatchService;
import com.example.itcardbrawl.game.MatchSide;
import com.example.itcardbrawl.game.MatchStore;
import com.example.itcardbrawl.game.Move;
import com.example.itcardbrawl.game.Target;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.mockito.ArgumentCaptor;

@SpringBootTest
@AutoConfigureMockMvc
class MatchControllerTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ClientRegistrationRepository clientRegistrations;

	@Autowired
	private CardCatalog catalog;

	@Autowired
	private MatchLogFormatter logFormatter;

	@Autowired
	private MatchEngine engine;

	@Autowired
	private MatchStore store;

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
		// Ten test sprawdza ruch bez celu; wydarzenia celowane dostaną osobny test w fazie API.
		while (affordableCharacter(match) == null) {
			assertThat((String) match.read("$.status")).isEqualTo("IN_PROGRESS");
			match = endTurn(player, match);
		}
		Map<String, Object> card = affordableCharacter(match);
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
	void playRequestMapsCharacterAndPlayerTargetsToEngineMoves() {
		MatchService service = mock(MatchService.class);
		OAuth2AuthenticationToken authentication = mock(OAuth2AuthenticationToken.class);
		when(authentication.getAuthorizedClientRegistrationId()).thenReturn("github");
		when(authentication.getName()).thenReturn("target-test");
		when(service.move(anyString(), any(UUID.class), anyInt(), any(Move.class))).thenReturn(Optional.empty());
		MatchController controller = new MatchController(catalog, service, logFormatter, engine);
		UUID matchId = UUID.randomUUID();

		controller.play(matchId, new MatchController.PlayRequest(2, 12,
				new MatchController.TargetRequest("CHARACTER", 7, null)), authentication, Locale.forLanguageTag("pl"));
		ArgumentCaptor<Move> move = ArgumentCaptor.forClass(Move.class);
		verify(service).move(eq("github:target-test"), eq(matchId), eq(2), move.capture());
		assertThat(move.getValue()).isEqualTo(new Move.PlayCard(12, new Target.Character(7)));

		reset(service);
		when(service.move(anyString(), any(UUID.class), anyInt(), any(Move.class))).thenReturn(Optional.empty());
		controller.play(matchId, new MatchController.PlayRequest(2, 13,
				new MatchController.TargetRequest("PLAYER", null, Match.Side.OPPONENT)), authentication,
				Locale.forLanguageTag("pl"));
		verify(service).move(eq("github:target-test"), eq(matchId), eq(2), move.capture());
		assertThat(move.getValue()).isEqualTo(new Move.PlayCard(13, new Target.Player(Match.Side.OPPONENT)));
	}

	@Test
	void playRequestRejectsInvalidTargetShapesAsBadRequest() throws Exception {
		MatchService service = mock(MatchService.class);
		OAuth2AuthenticationToken authentication = mock(OAuth2AuthenticationToken.class);
		MatchController controller = new MatchController(catalog, service, logFormatter, engine);
		UUID matchId = UUID.randomUUID();

		assertThatThrownBy(() -> controller.play(matchId,
				new MatchController.PlayRequest(1, 1, new MatchController.TargetRequest("PLAYER", 7, null)),
				authentication, Locale.forLanguageTag("pl")))
				.isInstanceOf(com.example.itcardbrawl.game.IllegalMoveException.class);
		assertThatThrownBy(() -> controller.play(matchId,
				new MatchController.PlayRequest(1, 1, new MatchController.TargetRequest("CHARACTER", null, null)),
				authentication, Locale.forLanguageTag("pl")))
				.isInstanceOf(com.example.itcardbrawl.game.IllegalMoveException.class);
	}

	// --- Wydarzenia z celem przez HTTP (stół ustawiony w magazynie, żeby nie zależeć od losowania) ---

	private static final int TARGETED_EVENT = 900; // dev-force-push: DAMAGE TARGET 5

	private static final int UNTARGETED_EVENT = 901; // dev-stackoverflow: DRAW SELF 2

	private static final int OWN_CHARACTER = 902;

	private static final int ENEMY_CHARACTER = 903;

	// Startuje partię przez HTTP, potem podmienia rękę, zasób i stoły na znane karty.
	private DocumentContext seededTable(String sub) throws Exception {
		DocumentContext started = start(player(sub), "developers");
		store.update("github:" + sub, m -> {
			MatchSide p = m.player();
			MatchSide o = m.opponent();
			MatchSide player = new MatchSide(p.faction(), p.deck(),
					List.of(instance(TARGETED_EVENT, "dev-force-push"), instance(UNTARGETED_EVENT, "dev-stackoverflow")),
					0, 10, 10, List.of(new BoardCharacter(instance(OWN_CHARACTER, "dev-tech-lead"), 0, false, false)),
					p.graveyard(), List.of());
			MatchSide opponent = new MatchSide(o.faction(), o.deck(), o.hand(), 0, o.resourceCurrent(),
					o.resourceMax(), List.of(new BoardCharacter(instance(ENEMY_CHARACTER, "tst-manual"), 0, false, false)),
					o.graveyard(), List.of());
			return new Match(m.id(), m.turnNumber(), m.activeSide(), player, opponent, m.status(), m.result(), m.log());
		});
		return started;
	}

	private CardInstance instance(int instanceId, String cardId) {
		return new CardInstance(instanceId, catalog.card(cardId).orElseThrow());
	}

	private static String playBody(DocumentContext match, int instanceId, String target) {
		return "{\"turn\":" + match.read("$.turn.number") + ",\"instanceId\":" + instanceId + ",\"target\":" + target
				+ "}";
	}

	@Test
	void currentExposesEffectsTargetsPlayableAndAttackTargets() throws Exception {
		String sub = "seed-" + UUID.randomUUID();
		seededTable(sub);
		Match stored = store.findCurrent("github:" + sub).orElseThrow();
		long attackMoves = engine.legalMoves(stored, Match.Side.PLAYER).stream()
			.filter(m -> m instanceof Move.Attack a && a.attackerId() == OWN_CHARACTER)
			.count();

		for (Language language : Language.values()) {
			DocumentContext match = current(player(sub), language.name().toLowerCase(Locale.ROOT));
			String targeted = "$.player.hand[?(@.instanceId == " + TARGETED_EVENT + ")]";
			String untargeted = "$.player.hand[?(@.instanceId == " + UNTARGETED_EVENT + ")]";
			List<Boolean> requiresTarget = match.read(targeted + ".requiresTarget");
			List<Boolean> playable = match.read(targeted + ".playable");
			List<String> effectTexts = match.read(targeted + ".effects[*].text");
			List<Map<String, Object>> targets = match.read(targeted + ".targets[*]");
			assertThat(requiresTarget).containsExactly(true);
			assertThat(playable).containsExactly(true);
			assertThat(effectTexts).containsExactly(
					catalog.card("dev-force-push").orElseThrow().effects().getFirst().describe().in(language));
			assertThat(targets)
				.extracting(t -> t.get("kind") + ":" + t.get("instanceId") + ":" + t.get("side"))
				.containsExactlyInAnyOrder("CHARACTER:" + OWN_CHARACTER + ":null",
						"CHARACTER:" + ENEMY_CHARACTER + ":null", "PLAYER:null:PLAYER", "PLAYER:null:OPPONENT");

			List<Boolean> untargetedRequires = match.read(untargeted + ".requiresTarget");
			List<Boolean> untargetedPlayable = match.read(untargeted + ".playable");
			List<List<?>> untargetedTargets = match.read(untargeted + ".targets");
			assertThat(untargetedRequires).containsExactly(false);
			assertThat(untargetedPlayable).containsExactly(true);
			assertThat(untargetedTargets).containsExactly(List.of());

			List<List<?>> attackTargets = match.read("$.player.board[?(@.instanceId == " + OWN_CHARACTER
					+ ")].attackTargets");
			assertThat(attackTargets).hasSize(1);
			assertThat(attackTargets.getFirst()).hasSize((int) attackMoves).isNotEmpty();
			List<List<?>> enemyAttackTargets = match.read("$.opponent.board[*].attackTargets");
			assertThat(enemyAttackTargets).containsExactly(List.of());
		}
	}

	@Test
	void playWithPlayerTargetAppliesEffectOverHttp() throws Exception {
		String sub = "seed-" + UUID.randomUUID();
		DocumentContext match = seededTable(sub);

		DocumentContext after = move(player(sub), match.read("$.matchId"), "play",
				playBody(match, TARGETED_EVENT, "{\"kind\":\"PLAYER\",\"side\":\"OPPONENT\"}"));

		assertThat((Integer) after.read("$.opponent.stress")).isEqualTo(5);
		assertThat((List<Integer>) after.read("$.player.hand[*].instanceId")).doesNotContain(TARGETED_EVENT);
	}

	@Test
	void playWithCharacterTargetAppliesEffectOverHttp() throws Exception {
		String sub = "seed-" + UUID.randomUUID();
		DocumentContext match = seededTable(sub);

		DocumentContext after = move(player(sub), match.read("$.matchId"), "play",
				playBody(match, TARGETED_EVENT, "{\"kind\":\"CHARACTER\",\"instanceId\":" + ENEMY_CHARACTER + "}"));

		// Tester manualny ma mniej niż 5 HP - pięć obrażeń go pokonuje.
		assertThat((List<Integer>) after.read("$.opponent.board[*].instanceId")).doesNotContain(ENEMY_CHARACTER);
		assertThat((Integer) after.read("$.opponent.stress")).isZero();
	}

	@Test
	void missingRequiredTargetOrUnnecessaryTargetReturns400() throws Exception {
		String sub = "seed-" + UUID.randomUUID();
		DocumentContext match = seededTable(sub);
		String matchId = match.read("$.matchId");

		mockMvc.perform(movePost(matchId, "play", playBody(match, TARGETED_EVENT)).with(player(sub)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").isNotEmpty());
		mockMvc.perform(movePost(matchId, "play",
				playBody(match, UNTARGETED_EVENT, "{\"kind\":\"PLAYER\",\"side\":\"OPPONENT\"}")).with(player(sub)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").isNotEmpty());

		List<Integer> hand = current(player(sub), "pl").read("$.player.hand[*].instanceId");
		assertThat(hand).contains(TARGETED_EVENT, UNTARGETED_EVENT);
	}

	private static Map<String, Object> affordableCharacter(DocumentContext match) {
		int resource = match.read("$.player.resource.current");
		List<Map<String, Object>> hand = match.read("$.player.hand");
		return hand.stream()
			.filter(card -> "CHARACTER".equals(card.get("type")) && (Integer) card.get("cost") <= resource)
			.findFirst()
			.orElse(null);
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
		mockMvc.perform(movePost(matchId, "play", "{\"turn\":1,\"instanceId\":1,"
				+ "\"target\":{\"kind\":\"CHARACTER\",\"side\":\"PLAYER\"}}").with(player))
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
		while (affordableCharacter(match) == null) {
			match = endTurn(player, match);
		}
		int instanceId = (Integer) affordableCharacter(match).get("instanceId");

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
