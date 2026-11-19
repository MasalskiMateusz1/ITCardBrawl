package com.example.itcardbrawl.api;

import java.util.List;

import com.example.itcardbrawl.game.CardCatalog;
import com.example.itcardbrawl.game.Match;
import com.example.itcardbrawl.game.MatchFactory;
import com.example.itcardbrawl.game.MatchStore;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping(path = "/api", produces = MediaType.APPLICATION_JSON_VALUE)
public class MatchController {

	private final CardCatalog catalog;

	private final MatchFactory matchFactory;

	private final MatchStore matchStore;

	public MatchController(CardCatalog catalog, MatchFactory matchFactory, MatchStore matchStore) {
		this.catalog = catalog;
		this.matchFactory = matchFactory;
		this.matchStore = matchStore;
	}

	@GetMapping("/factions")
	public List<FactionSummary> factions() {
		return catalog.playableFactions().stream()
			.map(f -> new FactionSummary(f.id(), f.name(), f.description()))
			.toList();
	}

	@PostMapping(path = "/matches", consumes = MediaType.APPLICATION_JSON_VALUE)
	@ResponseStatus(HttpStatus.CREATED)
	public MatchView start(@RequestBody StartMatchRequest request, OAuth2AuthenticationToken authentication) {
		Match match;
		try {
			match = matchFactory.create(request.factionId());
		}
		catch (IllegalArgumentException ex) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nieznana lub niegrywalna frakcja", ex);
		}
		matchStore.save(playerKey(authentication), match);
		return MatchView.of(match, catalog);
	}

	@GetMapping("/matches/current")
	public ResponseEntity<MatchView> current(OAuth2AuthenticationToken authentication) {
		return matchStore.findCurrent(playerKey(authentication))
			.map(match -> ResponseEntity.ok(MatchView.of(match, catalog)))
			.orElseGet(() -> ResponseEntity.notFound().build());
	}

	// Rejestracja + nazwa principala jednoznacznie identyfikują gracza między dostawcami.
	private static String playerKey(OAuth2AuthenticationToken authentication) {
		return authentication.getAuthorizedClientRegistrationId() + ":" + authentication.getName();
	}

	public record FactionSummary(String id, String name, String description) {
	}

	public record StartMatchRequest(String factionId) {
	}

}
