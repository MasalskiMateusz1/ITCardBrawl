package com.example.itcardbrawl.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.util.UriComponentsBuilder;

@SpringBootTest
@AutoConfigureMockMvc
class SecurityFlowTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ClientRegistrationRepository clientRegistrations;

	@Test
	void githubAuthorizationRedirectsToGithubWithProxyFriendlyCallback() throws Exception {
		MvcResult result = mockMvc.perform(get("/oauth2/authorization/github"))
			.andExpect(status().is3xxRedirection())
			.andReturn();

		String location = result.getResponse().getHeader("Location");
		assertThat(location).startsWith("https://github.com/login/oauth/authorize");
		assertThat(URLDecoder.decode(location, StandardCharsets.UTF_8))
			.contains("redirect_uri=http://localhost/login/oauth2/code/github");
	}

	@Test
	void googleAuthorizationRedirectsToGoogleWithProxyFriendlyCallback() throws Exception {
		MvcResult result = mockMvc.perform(get("/oauth2/authorization/google"))
			.andExpect(status().is3xxRedirection())
			.andReturn();

		String location = result.getResponse().getHeader("Location");
		assertThat(location).startsWith("https://accounts.google.com/o/oauth2/v2/auth");
		assertThat(URLDecoder.decode(location, StandardCharsets.UTF_8))
			.contains("redirect_uri=http://localhost/login/oauth2/code/google");
	}

	@Test
	void logoutWithCsrfReturnsNoContent() throws Exception {
		mockMvc.perform(post("/api/logout")
				.with(oauth2Login().clientRegistration(clientRegistrations.findByRegistrationId("github")))
				.with(csrf()))
			.andExpect(status().isNoContent());
	}

	@Test
	void logoutWithoutCsrfIsForbidden() throws Exception {
		mockMvc.perform(post("/api/logout")
				.with(oauth2Login().clientRegistration(clientRegistrations.findByRegistrationId("github"))))
			.andExpect(status().isForbidden());
	}

	@Test
	void logoutViaGetDoesNotLogOut() throws Exception {
		MvcResult result = mockMvc.perform(get("/api/logout")
				.with(oauth2Login().clientRegistration(clientRegistrations.findByRegistrationId("github"))))
			.andReturn();

		assertThat(result.getResponse().getStatus()).isNotEqualTo(HttpStatus.NO_CONTENT.value());
	}

	@Test
	void deniedConsentRedirectsToSignInWithError() throws Exception {
		MvcResult start = mockMvc.perform(get("/oauth2/authorization/github"))
			.andExpect(status().is3xxRedirection())
			.andReturn();
		String state = UriComponentsBuilder.fromUriString(start.getResponse().getHeader("Location"))
			.build()
			.getQueryParams()
			.getFirst("state");

		mockMvc.perform(get("/login/oauth2/code/github")
				.session((MockHttpSession) start.getRequest().getSession())
				.param("error", "access_denied")
				.param("state", state))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/sign-in?error"));
	}

	@Test
	void noGeneratedLoginPage() throws Exception {
		mockMvc.perform(get("/login"))
			.andExpect(status().isUnauthorized());
	}

}
