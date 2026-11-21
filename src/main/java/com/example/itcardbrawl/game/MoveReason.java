package com.example.itcardbrawl.game;

/**
 * Rodzaj powodu ruchu komputera ({@link MatchEvent.MoveExplained}). Zdania dla gracza składa widok.
 */
public enum MoveReason {

	/** Ruch wygrywa partię. */
	LETHAL,

	/** Ruch pokonuje postać gracza. */
	REMOVE_THREAT,

	/** Atak w postać, który osłabia stół gracza bardziej niż własny. */
	FAVORABLE_TRADE,

	/** Atak w postać z Ochroną, która blokuje inne cele. */
	OPEN_GUARD,

	/** Obrażenia w HP gracza. */
	PRESSURE,

	/** Zagranie postaci. */
	DEVELOP_BOARD,

	/** Wzmocnienie własnych postaci. */
	STRENGTHEN,

	/** Osłabienie lub uwięzienie postaci gracza. */
	WEAKEN,

	/** Leczenie własnego HP lub Ochrona przy niskim własnym HP. */
	DEFEND,

	/** Dobranie kart. */
	DRAW_CARDS,

	/** Zwiększenie budżetu. */
	GAIN_BUDGET,

	/** Koniec tury: żaden ruch nie poprawia pozycji. */
	END_NO_GAIN,

	/** Koniec tury: brak poprawnych ruchów. */
	END_NO_MOVES

}
