package com.example.itcardbrawl.game;

import java.security.SecureRandom;
import java.util.random.RandomGenerator;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

import tools.jackson.databind.json.JsonMapper;

@Configuration
public class GameConfig {

	// Katalog wczytywany przy starcie: niepoprawny plik zatrzymuje aplikację.
	@Bean
	public CardCatalog cardCatalog(JsonMapper jsonMapper) {
		return CardCatalogLoader.load(jsonMapper, new ClassPathResource(CardCatalogLoader.CATALOG_LOCATION));
	}

	// SecureRandom jest bezpieczny wątkowo - jeden bean obsługuje równoległe żądania.
	@Bean
	public RandomGenerator gameRandom() {
		return new SecureRandom();
	}

}
