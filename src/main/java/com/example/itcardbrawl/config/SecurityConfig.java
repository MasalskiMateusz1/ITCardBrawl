package com.example.itcardbrawl.config;

import com.example.itcardbrawl.web.SpaForwardController;

import jakarta.servlet.DispatcherType;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import org.springframework.security.web.util.matcher.RequestMatcher;

@Configuration
public class SecurityConfig {

	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		http
			.authorizeHttpRequests(auth -> auth
				.dispatcherTypeMatchers(DispatcherType.FORWARD, DispatcherType.ERROR).permitAll()
				.requestMatchers("/api/ping").permitAll()
				.requestMatchers(HttpMethod.GET, "/", "/index.html", "/favicon.ico", "/*.js", "/*.css", "/media/**").permitAll()
				.requestMatchers(spaRoutes()).permitAll()
				.anyRequest().authenticated())
			.formLogin(AbstractHttpConfigurer::disable)
			.httpBasic(AbstractHttpConfigurer::disable)
			.oauth2Login(o -> o
				.defaultSuccessUrl("/", true)
				.failureUrl("/sign-in?error"))
			.csrf(c -> c.spa())
			.logout(l -> l
				.logoutUrl("/api/logout")
				.logoutSuccessHandler(new HttpStatusReturningLogoutSuccessHandler(HttpStatus.NO_CONTENT)))
			.exceptionHandling(e -> e.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)));
		return http.build();
	}

	// Angular routes (GET, matching the SPA fallback rule); authorization is checked by the guard via /api/me.
	private static RequestMatcher spaRoutes() {
		return request -> HttpMethod.GET.matches(request.getMethod())
				&& SpaForwardController.isSpaRoute(SpaForwardController.pathWithinApplication(request));
	}

}
