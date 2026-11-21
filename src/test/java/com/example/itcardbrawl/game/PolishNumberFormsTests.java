package com.example.itcardbrawl.game;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PolishNumberFormsTests {

	// 1 → pojedyncza; końcówka 2–4 poza 12–14 → mnoga; reszta (także 0) → dopełniacz liczby mnogiej.
	@Test
	void choosesPolishFormByNumber() {
		assertThat(form(1)).isEqualTo("karta");
		assertThat(form(2)).isEqualTo("karty");
		assertThat(form(4)).isEqualTo("karty");
		assertThat(form(22)).isEqualTo("karty");
		assertThat(form(5)).isEqualTo("kart");
		assertThat(form(12)).isEqualTo("kart");
		assertThat(form(14)).isEqualTo("kart");
		assertThat(form(25)).isEqualTo("kart");
		assertThat(form(0)).isEqualTo("kart");
	}

	private static String form(int value) {
		return PolishNumberForms.form(value, "karta", "karty", "kart");
	}

}
