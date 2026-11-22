package com.example.itcardbrawl.api;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

import com.example.itcardbrawl.game.CardCatalog;
import com.example.itcardbrawl.game.IllegalMoveException;
import com.example.itcardbrawl.game.Language;
import com.example.itcardbrawl.game.MatchOutcome;
import com.example.itcardbrawl.game.Match;
import com.example.itcardbrawl.game.MatchEngine;
import com.example.itcardbrawl.game.MatchService;
import com.example.itcardbrawl.game.Move;
import com.example.itcardbrawl.game.StaleMoveException;
import com.example.itcardbrawl.game.Target;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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

	private final MatchService matchService;

	private final MatchLogFormatter logFormatter;

	private final MatchEngine engine;

	public MatchController(CardCatalog catalog, MatchService matchService, MatchLogFormatter logFormatter,
			MatchEngine engine) {
		this.catalog = catalog;
		this.matchService = matchService;
		this.logFormatter = logFormatter;
		this.engine = engine;
	}

	@GetMapping("/factions")
	public List<FactionSummary> factions(Locale locale) {
		Language language = Language.from(locale);
		return catalog.playableFactions().stream()
			.map(f -> new FactionSummary(f.id(), f.name().in(language), f.description().in(language)))
			.toList();
	}

	@PostMapping(path = "/matches", consumes = MediaType.APPLICATION_JSON_VALUE)
	@ResponseStatus(HttpStatus.CREATED)
	public MatchView start(@RequestBody StartMatchRequest request, OAuth2AuthenticationToken authentication,
			Locale locale) {
		MatchOutcome outcome;
		try {
			outcome = matchService.start(playerKey(authentication), request.factionId());
		}
		catch (IllegalArgumentException ex) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nieznana lub niegrywalna frakcja", ex);
		}
		return view(outcome, locale);
	}

	@GetMapping("/matches/current")
	public ResponseEntity<MatchView> current(OAuth2AuthenticationToken authentication, Locale locale) {
		// Deliberate side effect of a GET: the turn deadline is checked lazily (no background jobs), so a read past the
		// deadline ends the player's turn and plays the computer turn. It only does what the next request would do
		// anyway and is idempotent afterwards. Steps to replay only when the turn has just ended automatically (or
		// was ended by a move rejected past the deadline).
		return respond(matchService.current(playerKey(authentication)).orElse(null), locale);
	}

	@PostMapping(path = "/matches/{matchId}/play", consumes = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<MatchView> play(@PathVariable UUID matchId, @RequestBody PlayRequest request,
			OAuth2AuthenticationToken authentication, Locale locale) {
		return move(authentication, matchId, request.turn(),
				new Move.PlayCard(request.instanceId(), target(request.target())), locale);
	}

	@PostMapping(path = "/matches/{matchId}/attack", consumes = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<MatchView> attack(@PathVariable UUID matchId, @RequestBody AttackRequest request,
			OAuth2AuthenticationToken authentication, Locale locale) {
		return move(authentication, matchId, request.turn(), new Move.Attack(request.attackerId(), request.targetId()),
				locale);
	}

	@PostMapping(path = "/matches/{matchId}/end-turn", consumes = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<MatchView> endTurn(@PathVariable UUID matchId, @RequestBody EndTurnRequest request,
			OAuth2AuthenticationToken authentication, Locale locale) {
		return move(authentication, matchId, request.turn(), new Move.EndTurn(), locale);
	}

	// A stale move (a different match or turn) — the state is unchanged, the SPA should refresh the view.
	@ExceptionHandler(StaleMoveException.class)
	public ResponseEntity<ErrorView> staleMove(StaleMoveException ex) {
		return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorView(ex.getMessage()));
	}

	@ExceptionHandler(IllegalMoveException.class)
	public ResponseEntity<ErrorView> illegalMove(IllegalMoveException ex) {
		return ResponseEntity.badRequest().body(new ErrorView(ex.getMessage()));
	}

	private ResponseEntity<MatchView> move(OAuth2AuthenticationToken authentication, UUID matchId, int turn, Move move,
			Locale locale) {
		return respond(matchService.move(playerKey(authentication), matchId, turn, move).orElse(null), locale);
	}

	// No current match for the player yields 404.
	private ResponseEntity<MatchView> respond(MatchOutcome outcome, Locale locale) {
		return outcome == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(view(outcome, locale));
	}

	private MatchView view(MatchOutcome outcome, Locale locale) {
		return MatchView.of(outcome.match(), outcome.replay(), catalog, logFormatter, Language.from(locale),
				engine.legalMoves(outcome.match(), Match.Side.PLAYER));
	}

	// The registration + principal name uniquely identify the player across providers.
	private static String playerKey(OAuth2AuthenticationToken authentication) {
		return authentication.getAuthorizedClientRegistrationId() + ":" + authentication.getName();
	}

	public record FactionSummary(String id, String name, String description) {
	}

	public record StartMatchRequest(String factionId) {
	}

	public record PlayRequest(int turn, int instanceId, TargetRequest target) {
	}

	public record TargetRequest(String kind, Integer instanceId, Match.Side side) {
	}

	private static Target target(TargetRequest request) {
		if (request == null) {
			return null;
		}
		if ("CHARACTER".equals(request.kind()) && request.instanceId() != null && request.side() == null) {
			return new Target.Character(request.instanceId());
		}
		if ("PLAYER".equals(request.kind()) && request.instanceId() == null && request.side() != null) {
			return new Target.Player(request.side());
		}
		throw new IllegalMoveException("Nieprawidłowy cel wydarzenia");
	}

	/** {@code targetId == null} means an attack on the player (the opponent's HP). */
	public record AttackRequest(int turn, int attackerId, Integer targetId) {
	}

	public record EndTurnRequest(int turn) {
	}

	public record ErrorView(String message) {
	}

}
