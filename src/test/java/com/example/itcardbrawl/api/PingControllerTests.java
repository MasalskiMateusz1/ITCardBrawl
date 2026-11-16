package com.example.itcardbrawl.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class PingControllerTests {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void pingIsPublicAndReturnsOk() throws Exception {
		mockMvc.perform(get("/api/ping"))
			.andExpect(status().isOk())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
			.andExpect(jsonPath("$.status").value("ok"));
	}

	@Test
	void unauthenticatedRequestToProtectedEndpointReturns401WithoutRedirect() throws Exception {
		mockMvc.perform(get("/api/anything"))
			.andExpect(status().isUnauthorized())
			.andExpect(header().doesNotExist("Location"));
	}

}
