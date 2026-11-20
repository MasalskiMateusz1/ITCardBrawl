package com.example.itcardbrawl.game;

import java.util.List;

/**
 * Wynik startu albo ruchu: stan końcowy partii i kroki tury komputera do odtworzenia w SPA. Kroki nie trafiają
 * do magazynu — żyją tylko w odpowiedzi. Pusty {@code replay} oznacza brak tury komputera do pokazania.
 */
public record MatchOutcome(Match match, List<Match> replay) {

	public MatchOutcome {
		replay = List.copyOf(replay);
	}

}
