package com.example.itcardbrawl.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("prod")
class ProdProfileTests {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void redirectUriUsesForwardedHttpsSchemeAndHost() throws Exception {
		MvcResult result = mockMvc.perform(get("/oauth2/authorization/github")
				.header("X-Forwarded-Proto", "https")
				.header("X-Forwarded-Host", "gra.example.com"))
			.andExpect(status().is3xxRedirection())
			.andReturn();

		String location = result.getResponse().getHeader("Location");
		assertThat(URLDecoder.decode(location, StandardCharsets.UTF_8))
			.contains("redirect_uri=https://gra.example.com/login/oauth2/code/github");
	}

}
