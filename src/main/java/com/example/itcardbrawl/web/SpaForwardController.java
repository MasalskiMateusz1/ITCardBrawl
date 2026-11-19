package com.example.itcardbrawl.web;

import java.util.Set;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.server.ResponseStatusException;

/**
 * Fallback tras SPA: GET na trase Angulara (np. {@code /sign-in}) zwraca {@code index.html},
 * zeby odswiezenie i glebokie linki dzialaly. Pliki z kropka w ostatnim segmencie
 * (bundle, {@code favicon.ico}) obsluguje domyslny handler zasobow statycznych.
 */
@Controller
public class SpaForwardController {

	private static final Set<String> BACKEND_PREFIXES = Set.of("api", "oauth2", "login", "error");

	@GetMapping({ "/", "/**/{last:[^.]*}" })
	public String forwardToIndex(HttpServletRequest request) {
		if (!isSpaRoute(pathWithinApplication(request))) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND);
		}
		return "forward:/index.html";
	}

	/**
	 * Trasa SPA: ostatni segment bez kropki i pierwszy segment spoza prefiksow backendu
	 * ({@code /api}, {@code /oauth2}, {@code /login}, {@code /error}).
	 */
	public static boolean isSpaRoute(String path) {
		if (path == null || !path.startsWith("/")) {
			return false;
		}
		String lastSegment = path.substring(path.lastIndexOf('/') + 1);
		if (lastSegment.contains(".")) {
			return false;
		}
		int firstSlashAfterRoot = path.indexOf('/', 1);
		String firstSegment = (firstSlashAfterRoot < 0) ? path.substring(1) : path.substring(1, firstSlashAfterRoot);
		return !BACKEND_PREFIXES.contains(firstSegment);
	}

	public static String pathWithinApplication(HttpServletRequest request) {
		return request.getRequestURI().substring(request.getContextPath().length());
	}

}
