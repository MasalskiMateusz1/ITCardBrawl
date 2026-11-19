package com.example.itcardbrawl.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * Regula tras SPA w SecurityConfig przepuszcza bez logowania kazdy GET spoza prefiksow backendu
 * ({@code /api}, {@code /oauth2}, {@code /login}, {@code /error}). Kazdy endpoint backendu musi wiec
 * lezec pod jednym z tych prefiksow - inaczej po cichu stalby sie publiczny.
 */
@SpringBootTest
class BackendRoutesTests {

	@Autowired
	@Qualifier("requestMappingHandlerMapping")
	private RequestMappingHandlerMapping handlerMapping;

	@Test
	void everyBackendEndpointIsUnderBackendPrefix() {
		List<String> publicByAccident = handlerMapping.getHandlerMethods().entrySet().stream()
			.filter(entry -> entry.getValue().getBeanType() != SpaForwardController.class)
			.flatMap(entry -> entry.getKey().getPatternValues().stream())
			.filter(SpaForwardController::isSpaRoute)
			.toList();

		assertThat(publicByAccident)
			.as("endpointy poza /api, /oauth2, /login, /error sa traktowane jak trasy SPA (permitAll)")
			.isEmpty();
	}

}
