package com.example.itcardbrawl.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URI;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.RequestDispatcher;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
class SpaForwardTests {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void spaRouteForwardsToIndexWithoutLogin() throws Exception {
		mockMvc.perform(get("/sign-in"))
			.andExpect(status().isOk())
			.andExpect(forwardedUrl("/index.html"));
	}

	@Test
	void nestedSpaRouteForwardsToIndexWithoutLogin() throws Exception {
		mockMvc.perform(get("/game/123"))
			.andExpect(status().isOk())
			.andExpect(forwardedUrl("/index.html"));
	}

	@Test
	void rootForwardsToIndexWithoutLogin() throws Exception {
		mockMvc.perform(get("/"))
			.andExpect(status().isOk())
			.andExpect(forwardedUrl("/index.html"));
	}

	@Test
	void indexHtmlIsServedWithoutLogin() throws Exception {
		mockMvc.perform(get("/index.html"))
			.andExpect(status().isOk());
	}

	@Test
	void apiStaysProtected() throws Exception {
		mockMvc.perform(get("/api/me"))
			.andExpect(status().isUnauthorized());
	}

	// The SPA route rule must decide on the decoded path, just like MVC routing:
	// "/%61pi/me" is "/api/me" and must not bypass authentication as an "SPA route".
	@Test
	void percentEncodedApiPathStaysProtected() throws Exception {
		mockMvc.perform(get(URI.create("/%61pi/me")))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void percentEncodedSpaRouteStillForwardsToIndex() throws Exception {
		mockMvc.perform(get(URI.create("/%73ign-in")))
			.andExpect(status().isOk())
			.andExpect(forwardedUrl("/index.html"));
	}

	@Test
	void missingStaticFileIsNotFoundNotUnauthorized() throws Exception {
		mockMvc.perform(get("/nie-istnieje.js"))
			.andExpect(status().isNotFound());
	}

	// MockMvc does not perform the ERROR dispatch after sendError, so we simulate it directly:
	// in the container a missing file ends in an ERROR dispatch to /error, which must not yield 401.
	@Test
	void errorDispatchForMissingFileIsNotFoundNotUnauthorized() throws Exception {
		mockMvc.perform(get("/error").with(request -> {
				request.setDispatcherType(DispatcherType.ERROR);
				request.setAttribute(RequestDispatcher.ERROR_STATUS_CODE, HttpStatus.NOT_FOUND.value());
				request.setAttribute(RequestDispatcher.ERROR_REQUEST_URI, "/nie-istnieje.js");
				return request;
			}))
			.andExpect(status().isNotFound());
	}

	@Test
	void postToSpaRouteIsNotServed() throws Exception {
		MvcResult result = mockMvc.perform(post("/sign-in")).andReturn();

		assertThat(result.getResponse().getStatus()).isNotEqualTo(HttpStatus.OK.value());
	}

}
