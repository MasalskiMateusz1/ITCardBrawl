package com.example.itcardbrawl.config;

import java.util.Arrays;

import com.example.itcardbrawl.game.Language;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver;

@Configuration
public class LocaleConfig {

	// Spring MVC szuka resolvera po nazwie beana "localeResolver" — inna nazwa byłaby zignorowana.
	@Bean
	public LocaleResolver localeResolver() {
		AcceptHeaderLocaleResolver resolver = new AcceptHeaderLocaleResolver();
		resolver.setSupportedLocales(Arrays.stream(Language.values()).map(Language::locale).toList());
		resolver.setDefaultLocale(Language.PL.locale());
		return resolver;
	}

}
