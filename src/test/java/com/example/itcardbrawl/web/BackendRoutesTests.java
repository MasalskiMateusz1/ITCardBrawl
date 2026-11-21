package com.example.itcardbrawl.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * The SPA route rule in SecurityConfig lets through, without login, every GET outside the backend prefixes
 * ({@code /api}, {@code /oauth2}, {@code /login}, {@code /error}). Every backend endpoint must therefore
 * live under one of these prefixes - otherwise it would silently become public.
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
