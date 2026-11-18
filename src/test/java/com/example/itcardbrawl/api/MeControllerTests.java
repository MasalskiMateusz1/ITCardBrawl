package com.example.itcardbrawl.api;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class MeControllerTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ClientRegistrationRepository clientRegistrations;

	private ClientRegistration github() {
		return clientRegistrations.findByRegistrationId("github");
	}

	private ClientRegistration google() {
		return clientRegistrations.findByRegistrationId("google");
	}

	@Test
	void githubUserWithNameUsesName() throws Exception {
		mockMvc.perform(get("/api/me").with(oauth2Login()
				.clientRegistration(github())
				.attributes(a -> {
					a.put("name", "Octo Cat");
					a.put("login", "octocat");
				})))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.provider").value("github"))
			.andExpect(jsonPath("$.displayName").value("Octo Cat"));
	}

	@Test
	void githubUserWithoutNameFallsBackToLogin() throws Exception {
		mockMvc.perform(get("/api/me").with(oauth2Login()
				.clientRegistration(github())
				.attributes(a -> a.put("login", "octocat"))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.provider").value("github"))
			.andExpect(jsonPath("$.displayName").value("octocat"));
	}

	@Test
	void githubAvatarUrlIsReturned() throws Exception {
		mockMvc.perform(get("/api/me").with(oauth2Login()
				.clientRegistration(github())
				.attributes(a -> {
					a.put("login", "octocat");
					a.put("avatar_url", "https://avatars.example/octocat.png");
				})))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.avatarUrl").value("https://avatars.example/octocat.png"));
	}

	@Test
	void googleUserWithNameUsesName() throws Exception {
		mockMvc.perform(get("/api/me").with(oidcLogin()
				.clientRegistration(google())
				.idToken(t -> t
					.claim("name", "Jan Kowalski")
					.claim("given_name", "Jan")
					.claim("email", "jan@example.com"))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.provider").value("google"))
			.andExpect(jsonPath("$.displayName").value("Jan Kowalski"));
	}

	@Test
	void googleUserWithoutNameFallsBackToGivenName() throws Exception {
		mockMvc.perform(get("/api/me").with(oidcLogin()
				.clientRegistration(google())
				.idToken(t -> t
					.claim("given_name", "Jan")
					.claim("email", "jan@example.com"))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.displayName").value("Jan"));
	}

	@Test
	void googleUserWithoutNameAndGivenNameFallsBackToEmail() throws Exception {
		mockMvc.perform(get("/api/me").with(oidcLogin()
				.clientRegistration(google())
				.idToken(t -> t.claim("email", "jan@example.com"))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.displayName").value("jan@example.com"));
	}

	@Test
	void googlePictureIsReturnedAsAvatarUrl() throws Exception {
		mockMvc.perform(get("/api/me").with(oidcLogin()
				.clientRegistration(google())
				.idToken(t -> t
					.claim("name", "Jan Kowalski")
					.claim("picture", "https://lh3.example/jan.png"))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.avatarUrl").value("https://lh3.example/jan.png"));
	}

	@Test
	void missingAvatarIsNull() throws Exception {
		mockMvc.perform(get("/api/me").with(oauth2Login()
				.clientRegistration(github())
				.attributes(a -> a.put("login", "octocat"))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.avatarUrl").value(nullValue()));
	}

	@Test
	void responseIssuesXsrfTokenCookie() throws Exception {
		mockMvc.perform(get("/api/me").with(oauth2Login()
				.clientRegistration(github())
				.attributes(a -> a.put("login", "octocat"))))
			.andExpect(status().isOk())
			.andExpect(cookie().exists("XSRF-TOKEN"));
	}

	@Test
	void unauthenticatedRequestReturns401WithoutRedirect() throws Exception {
		mockMvc.perform(get("/api/me"))
			.andExpect(status().isUnauthorized())
			.andExpect(header().doesNotExist("Location"));
	}

}
