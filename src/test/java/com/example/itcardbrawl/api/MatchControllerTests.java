package com.example.itcardbrawl.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import jakarta.servlet.http.Cookie;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
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
		Cookie xsrf = mockMvc.perform(get("/api/factions").with(newPlayer()))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getCookie("XSRF-TOKEN");
		assertThat(xsrf).isNotNull();
		return post("/api/matches").cookie(xsrf).header("X-XSRF-TOKEN", xsrf.getValue())
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
	void startReturns201WithDealtTable() throws Exception {
		mockMvc.perform(startMatch("{\"factionId\":\"developers\"}").with(newPlayer()))
			.andExpect(status().isCreated())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
			.andExpect(jsonPath("$.matchId").isNotEmpty())
			.andExpect(jsonPath("$.turn.number").value(1))
			.andExpect(jsonPath("$.player.faction.id").value("developers"))
			.andExpect(jsonPath("$.player.faction.name").value("Developerzy"))
			.andExpect(jsonPath("$.opponent.faction.id").value("testers"))
			.andExpect(jsonPath("$.player.hand", hasSize(5)))
			.andExpect(jsonPath("$.player.deckCount").value(25))
			.andExpect(jsonPath("$.opponent.deckCount").value(25))
			.andExpect(jsonPath("$.opponent.handCount").value(5))
			.andExpect(jsonPath("$.player.stress").value(0))
			.andExpect(jsonPath("$.player.stressLimit").value(30))
			.andExpect(jsonPath("$.opponent.stress").value(0))
			.andExpect(jsonPath("$.opponent.stressLimit").value(30))
			.andExpect(jsonPath("$.player.hand[0].instanceId").isNumber())
			.andExpect(jsonPath("$.player.hand[0].cardId").isNotEmpty())
			.andExpect(jsonPath("$.player.hand[0].name").isNotEmpty())
			.andExpect(jsonPath("$.player.hand[0].type").isNotEmpty())
			.andExpect(jsonPath("$.player.hand[0].cost").isNumber())
			.andExpect(jsonPath("$.player.hand[0].text").exists())
			.andExpect(jsonPath("$.player.hand[0].neutral").isBoolean());
	}

	// Kto zaczyna, jest losowe - test sprawdza relację, nie konkretną stronę.
	@Test
	void activeSideHasOneResourceAndOtherSideZero() throws Exception {
		DocumentContext match = start(newPlayer(), "testers");

		String active = match.read("$.turn.active");
		assertThat(active).isIn("PLAYER", "OPPONENT");
		String activeKey = active.equals("PLAYER") ? "player" : "opponent";
		String otherKey = active.equals("PLAYER") ? "opponent" : "player";

		assertThat((Integer) match.read("$." + activeKey + ".resource.current")).isEqualTo(1);
		assertThat((Integer) match.read("$." + activeKey + ".resource.max")).isEqualTo(1);
		assertThat((Integer) match.read("$." + otherKey + ".resource.current")).isEqualTo(0);
		assertThat((Integer) match.read("$." + otherKey + ".resource.max")).isEqualTo(0);
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

}
