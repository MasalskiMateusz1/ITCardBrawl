package com.example.itcardbrawl.game;

import java.security.SecureRandom;
import java.util.random.RandomGenerator;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

import tools.jackson.databind.json.JsonMapper;

@Configuration
public class GameConfig {

	// The catalog is loaded at startup: an invalid file stops the application.
	@Bean
	public CardCatalog cardCatalog(JsonMapper jsonMapper) {
		return CardCatalogLoader.load(jsonMapper, new ClassPathResource(CardCatalogLoader.CATALOG_LOCATION));
	}

	// SecureRandom is thread-safe - a single bean serves concurrent requests.
	@Bean
	public RandomGenerator gameRandom() {
		return new SecureRandom();
	}

}
