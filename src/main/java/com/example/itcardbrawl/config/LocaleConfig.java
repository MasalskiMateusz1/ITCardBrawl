package com.example.itcardbrawl.config;

import java.util.Arrays;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import com.example.itcardbrawl.game.Language;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver;

@Configuration
public class LocaleConfig implements WebMvcConfigurer {

	// Spring MVC szuka resolvera po nazwie beana "localeResolver" — inna nazwa byłaby zignorowana.
	@Bean
	public LocaleResolver localeResolver() {
		AcceptHeaderLocaleResolver resolver = new AcceptHeaderLocaleResolver();
		resolver.setSupportedLocales(Arrays.stream(Language.values()).map(Language::locale).toList());
		resolver.setDefaultLocale(Language.PL.locale());
		return resolver;
	}

	// Treść API zależy od Accept-Language — cache (przeglądarka, proxy) musi rozróżniać wersje językowe.
	@Override
	public void addInterceptors(InterceptorRegistry registry) {
		registry.addInterceptor(new HandlerInterceptor() {
			@Override
			public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
				response.addHeader(HttpHeaders.VARY, HttpHeaders.ACCEPT_LANGUAGE);
				return true;
			}
		}).addPathPatterns("/api/**");
	}

}
