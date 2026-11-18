package com.example.itcardbrawl.api;

import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class MeController {

	@GetMapping(path = "/me", produces = MediaType.APPLICATION_JSON_VALUE)
	public MeResponse me(OAuth2AuthenticationToken authentication) {
		String provider = authentication.getAuthorizedClientRegistrationId();
		OAuth2User user = authentication.getPrincipal();
		Map<String, Object> attributes = user.getAttributes();

		return switch (provider) {
			case "github" -> new MeResponse(provider,
					firstNonBlank(attributes, user.getName(), "name", "login"),
					stringAttribute(attributes, "avatar_url"));
			case "google" -> new MeResponse(provider,
					firstNonBlank(attributes, user.getName(), "name", "given_name", "email"),
					stringAttribute(attributes, "picture"));
			default -> new MeResponse(provider, user.getName(), null);
		};
	}

	private static String firstNonBlank(Map<String, Object> attributes, String fallback, String... keys) {
		for (String key : keys) {
			String value = stringAttribute(attributes, key);
			if (value != null && !value.isBlank()) {
				return value;
			}
		}
		return fallback;
	}

	private static String stringAttribute(Map<String, Object> attributes, String key) {
		Object value = attributes.get(key);
		return value != null ? value.toString() : null;
	}

	public record MeResponse(String provider, String displayName, String avatarUrl) {
	}

}
