package com.example.itcardbrawl.web;

import java.util.Set;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UrlPathHelper;

/**
 * SPA route fallback: a GET to an Angular route (e.g. {@code /sign-in}) returns {@code index.html},
 * so that refreshes and deep links work. Files with a dot in the last segment
 * (bundles, {@code favicon.ico}) are served by the default static resource handler.
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
	 * An SPA route: the last segment has no dot and the first segment is not one of the backend prefixes
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

	// The decoded and normalized path (as in MVC routing), not the raw URI:
	// otherwise "/%61pi/me" would bypass the security rule as an "SPA route".
	public static String pathWithinApplication(HttpServletRequest request) {
		return UrlPathHelper.defaultInstance.getPathWithinApplication(request);
	}

}
