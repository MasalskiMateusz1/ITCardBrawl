---
date: 2026-11-18T14:02:26+01:00
researcher: Claude (Opus 5.5) for Mateusz Masalski
git_commit: 0b9271b
branch: master
repository: a-cards (ItCardBrawl)
topic: "S-01 external-login-gate — logowanie GitHub + Google (Spring oauth2Login) w SPA Angular za proxy dev servera"
tags: [research, spring-security, oauth2-login, csrf, angular, router, proxy, s-01]
status: complete
last_updated: 2026-11-18
last_updated_by: Claude (Opus 5.5)
---

# Research: S-01 external-login-gate

**Date**: 2026-11-18T14:02:26+01:00
**Researcher**: Claude (Opus 5.5)
**Git Commit**: 0b9271b
**Branch**: master
**Repository**: a-cards (ItCardBrawl)

## Research Question

Jak dodać do istniejącego szkieletu (F-01) logowanie zewnętrznym kontem — **GitHub i Google** (decyzja użytkownika 2026-11-18, zamyka Unknown z roadmapy S-01) — tak, by gracz po zalogowaniu widział lobby ze swoją nazwą, a niezalogowany był kierowany do logowania (roadmap S-01, PRD FR-001, Access Control `context/foundation/prd.md:121-127`)? Kształt narzucony przez `context/foundation/tech-stack.md` §Post-bootstrap notes: Spring robi `oauth2Login` i trzyma sesję (cookie), SPA nie widzi tokenów, przeglądarka widzi jedno źródło (proxy `ng serve`).

## Summary

- **Backend da się rozszerzyć bez zmiany kontraktu `/api/**` → 401.** Spring Security 7.1.2 (z Boot 4.1.2): jawny `authenticationEntryPoint` wygrywa nad domyślnymi z `oauth2Login`, a przy własnym entry poincie nie powstaje generowana strona `/login` (szczegóły i kotwice niżej). Istniejące `PingControllerTests` zachowują asercje, ale **kontekst przestanie się ładować bez rejestracji klientów** — testy potrzebują atrapowych `client-id`.
- **Oba dostawcy są wbudowani** (`CommonOAuth2Provider` GOOGLE/GITHUB) — wystarczą `client-id`/`client-secret`; Google idzie ścieżką OIDC (scope `openid,profile,email`), GitHub zwykłym OAuth2 (`read:user`, nazwa principala = numeryczne `id`).
- **Proxy:** trzeba dodać `/oauth2` i `/login/oauth2` do `frontend/proxy.conf.json` (dziś tylko `/api`). Proxy Vite w Angular 22 domyślnie zachowuje nagłówek `Host`, więc Spring liczy `redirect_uri = http://localhost:4200/login/oauth2/code/{id}` — ten adres rejestruje się u obu dostawców. **Nie ustawiać `changeOrigin: true`.**
- **CSRF:** `csrf(c -> c.spa())` istnieje w 7.x (cookie `XSRF-TOKEN`/nagłówek `X-XSRF-TOKEN`, zgodne z domyślnymi Angulara), ale **nie wystawia cookie zachłannie** — potrzebny endpoint/filtr wywołujący `CsrfToken.getToken()` (np. w `GET /api/me`).
- **Frontend:** rekomendowany minimalny router (`''` lobby za `CanActivateFn` + `'sign-in'`), `AuthService` z sygnałem `user`, interceptor 401; logowanie jako pełna nawigacja (`<a href="/oauth2/authorization/github">`), wylogowanie `POST` z automatycznym nagłówkiem XSRF.

## Detailed Findings

### 1. Stan obecny (punkt wyjścia)

- `SecurityConfig` (`src/main/java/com/example/itcardbrawl/config/SecurityConfig.java:15-23`): `/api/ping` permitAll, `anyRequest().authenticated()`, formLogin i httpBasic wyłączone, `HttpStatusEntryPoint(UNAUTHORIZED)`. Brak `oauth2Login`, brak konfiguracji CSRF (więc domyślny CSRF jest włączony, ale nieużywany — w F-01 nie ma mutacji).
- `application.properties` (`src/main/resources/application.properties:1`) zawiera wyłącznie `spring.application.name`. Brak `src/test/resources`.
- `pom.xml:35-40,54-63` — startery security + oauth2-client + ich `-test` już są (nic nie trzeba dodawać).
- Testy: `PingControllerTests.java:23-36` (ping 200; `/api/anything` 401 bez `Location`) i `ItcardbrawlApplicationTests` (`contextLoads`).
- Frontend: brak routera (`frontend/src/app/app.config.ts:5` — tylko `provideBrowserGlobalErrorListeners()`, `provideHttpClient()`), stan w sygnale (`app.ts:15`), proxy tylko `/api` (`frontend/proxy.conf.json:2-5`, podpięte w `frontend/angular.json:59`). `@angular/router` jest już w zależnościach (`frontend/package.json:19`).

### 2. Rejestracja klientów (Boot 4.1.2 / Security 7.1.2)

Źródła: jar-y `spring-security-config-7.1.2`, `spring-boot-security-oauth2-client-4.1.2` z `~/.m2`; https://docs.spring.io/spring-boot/reference/security/oauth2.html.

- Właściwości `spring.security.oauth2.client.registration.<id>.client-id|client-secret|scope` — bez zmian w 4.1.2. Gdy `<id>` to `github` lub `google`, mapper przyjmuje `<id>` jako provider i bierze domyślne URI z `CommonOAuth2Provider` (`OAuth2ClientPropertiesMapper.java:94-107`).
- Domyślne (`CommonOAuth2Provider.java`): GOOGLE `:38-50` — scope `openid,profile,email`, `userNameAttributeName=sub`; GITHUB `:57-67` — scope `read:user`, user-info `https://api.github.com/user`, `userNameAttributeName=id`. Szablon redirectu `{baseUrl}/{action}/oauth2/code/{registrationId}` (`:122`, action=`login`).
- Start aplikacji: pusty `client-id` → `IllegalStateException` (`OAuth2ClientProperties.java:53-59`); nierozwiązany placeholder `${GITHUB_CLIENT_ID}` również przerywa start. Brak jakiejkolwiek rejestracji → Boot nie tworzy `ClientRegistrationRepository` (`OAuth2ClientConfigurations.java:30-35`), a `oauth2Login()` go wymaga (`OAuth2ClientConfigurerUtils.java:56-58`) → `NoSuchBeanDefinitionException`.
- Sekrety: w repo tylko `client-id=${GITHUB_CLIENT_ID}` itd.; wartości z env lub z git-ignorowanego pliku (np. `spring.config.import=optional:file:.env[.properties]` albo profil `local`). Domyślne wartości-atrapy w głównym configu (`${X:dummy}`) maskowałyby brak konfiguracji — odradzane.
- Email GitHuba: `read:user` nie zwraca prywatnego emaila (wymaga `user:email` + `/user/emails`). S-01 emaila nie potrzebuje.

### 3. Przepływ za proxy dev servera

- Endpointy: start `/oauth2/authorization/{registrationId}` (`DefaultOAuth2AuthorizationRequestResolver.java:74`), callback `/login/oauth2/code/*` (`OAuth2LoginAuthenticationFilter.java:106`). `baseUrl` liczony z `UrlUtils.buildFullRequestUrl(request)` (`DefaultOAuth2AuthorizationRequestResolver.java:248-268`), czyli z nagłówka `Host` widzianego przez Tomcat.
- Proxy Angular 22 = middleware Vite 8.3.8 z wbudowanym http-proxy-3. Dla konfiguracji obiektowej (taka jest w repo) `changeOrigin` jest domyślnie `false`; `Host` przepisywany tylko przy `changeOrigin` (`frontend/node_modules/vite/dist/node/chunks/node.js:18924`), a wymuszenie `changeOrigin: true` dotyczy wyłącznie skróconej formy stringowej (`:19856-19858`). `followRedirects` domyślnie `false` (`vite/dist/node/index.d.ts:627`); `Location` i `Set-Cookie` przepisywane tylko przy jawnych opcjach rewrite (`node.js:19071-19090`). Wniosek: 302 do dostawcy i `Set-Cookie: JSESSIONID` przechodzą do przeglądarki niezmienione, na origin `localhost:4200`.
- Dopasowanie kluczy proxy to prefiks `url.startsWith(key)` (`node.js:19843-19849`; Angular zachowuje kolejność kluczy, `@angular/build/src/utils/load-proxy-config.js:130-138`). Klucz `/login` łapałby też trasę SPA `/login` przy odświeżeniu i domyślny `/login?error` — dlatego wąski klucz `/login/oauth2`.
- Domyślny `failureUrl` to `/login?error` — przy wyłączonym formLogin nie istnieje; trzeba ustawić własny (np. `/sign-in?error`).
- Callbacki do rejestracji (dev): `http://localhost:4200/login/oauth2/code/github` i `.../google`. Google wymaga dokładnego dopasowania i zwalnia localhost z https (https://developers.google.com/identity/protocols/oauth2/web-server#uri-validation). GitHub: dokładne dopasowanie; dla loopback port może się różnić, docs zalecają `127.0.0.1` (https://docs.github.com/en/apps/oauth-apps/building-oauth-apps/authorizing-oauth-apps#loopback-redirect-urls) — dla `localhost:4200` wystarczy dokładne dopasowanie.
- Alternatywa „callback na :8080” odrzucona: ląduje się po logowaniu na :8080, łamie model jednego źródła; działa jedynie dzięki temu, że cookie nie izolują portów.

### 4. Entry point, strona logowania, sukces/porażka

- `ExceptionHandlingConfigurer.java:266-270` — jawny `authenticationEntryPoint` jest używany zamiast domyślnych rejestrowanych przez konfiguratory logowania. Przy 2 rejestracjach `OAuth2LoginConfigurer.java:326-337` i tak pomija auto-redirect do jedynego dostawcy. `DefaultLoginPageConfigurer.java:91` dodaje generator strony `/login` tylko gdy entry point jest null → w tej konfiguracji strony nie ma (niezależnie od liczby rejestracji).
- Sukces: `.oauth2Login(o -> o.defaultSuccessUrl("/", true))` (`AbstractAuthenticationFilterConfigurer.java:130`); względne `/` rozwiązuje się do `:4200`. Domyślny request cache i tak pomija XHR/`Accept: application/json` (`RequestCacheConfigurer.java:145-175`).
- Interceptor 401 po stronie Angulara działa poprawnie tylko dlatego, że API odpowiada 401, a nie 302: domyślnym backendem HttpClient w v22 jest `FetchBackend` (`@angular/common/fesm2022/_module-chunk.mjs:2041-2045`), który podążyłby za 302 do dostawcy i padł na CORS. **Zachować `HttpStatusEntryPoint` co najmniej dla `/api/**`.**

### 5. Principal i nazwa gracza

- GitHub → `DefaultOAuth2User`, `getName()` = numeryczne `id`; Google → `DefaultOidcUser` (`OidcUser extends OAuth2User`), `getName()` = `sub`. Oba jako `OAuth2AuthenticationToken` z `getAuthorizedClientRegistrationId()` = `github`/`google`.
- Normalizacja dla `GET /api/me`: `provider` (registrationId), `providerUserId` (`getName()`), `displayName` — GitHub: `name`, fallback `login`; Google: `name` → `given_name` → `email`. Klucz tożsamości = provider + id (nie `login` — zmienny; nie email).
- Niezweryfikowane w docs: że GitHub `name` bywa null (https://docs.github.com/en/rest/users/users#get-the-authenticated-user) — fallback i tak tani.

### 6. CSRF i wylogowanie

- `CsrfConfigurer.spa()` — `CsrfConfigurer.java:225-234` (`@since 7.0`): `CookieCsrfTokenRepository.withHttpOnlyFalse()` (cookie `XSRF-TOKEN`, nagłówek `X-XSRF-TOKEN`, `CookieCsrfTokenRepository.java:45,49`) + `SpaCsrfTokenRequestHandler` (`CsrfConfigurer.java:397-417`).
- `spa()` nie dodaje filtra ładującego token — token jest odroczony; cookie powstaje dopiero po `getToken()`. Token jest też czyszczony przy logowaniu (`CsrfAuthenticationStrategy.java:69-73`) i wylogowaniu. Docs: https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html#csrf-integration-javascript-spa. Rozwiązanie: `GET /api/me` (lub osobny `GET /api/csrf`) przyjmuje `CsrfToken` i woła `getToken()`, albo mały `OncePerRequestFilter`.
- Angular: XSRF włączony domyślnie przez `provideHttpClient()` (`_module-chunk.mjs:2046-2050`), domyślne nazwy `XSRF-TOKEN`/`X-XSRF-TOKEN` (`:1073,1077`) — zgodne ze Springiem, `withXsrfConfiguration` zbędne. Nagłówek dodawany dla metod innych niż GET/HEAD, gdy origin żądania == origin strony (`:1154-1178`).
- Wylogowanie: `.logout(l -> l.logoutUrl("/api/logout").logoutSuccessHandler(new HttpStatusReturningLogoutSuccessHandler(HttpStatus.NO_CONTENT)))` — przy włączonym CSRF `LogoutFilter` obsługuje tylko POST; `/api/logout` mieści się w istniejącym wpisie proxy. Bezargumentowy konstruktor zwraca 200 (`HttpStatusReturningLogoutSuccessHandler.java:46-56`).

### 7. Frontend: bramka logowania

- Router vs brak routera: (a) przełącznik `@if` na sygnale — najmniej kodu, ale brak URL-i i „kierowanie do logowania” jest tylko domyślne; (b) `provideRouter(routes)` + funkcyjny `CanActivateFn` (`@angular/router/types/_router_module-chunk.d.ts:2863`), który woła `/api/me` i zwraca `true` albo `router.parseUrl('/sign-in')`. Rekomendacja: (b) w minimalnej postaci — dosłownie realizuje outcome roadmapy i przygotowuje kolejne ekrany. Nazwy tras nie mogą kolidować z prefiksami proxy (`/api`, `/oauth2`, `/login/oauth2`).
- Interceptor 401: `HttpInterceptorFn` + `provideHttpClient(withInterceptors([...]))` (`@angular/common/types/http.d.ts:1995,2145`); `inject()` na górze funkcji; pominąć przekierowanie dla sondy `/api/me` wykonywanej przez guard.
- Logowanie: pełna nawigacja (`<a href="/oauth2/authorization/github">` / `google`) — XHR/fetch nie przejdzie przez redirect do dostawcy (CORS, ekran zgody). Po callbacku Spring przekierowuje na `/` → guard → `/api/me`.
- Testy (Vitest + TestBed): wzorzec jak `frontend/src/app/app.spec.ts:10-20`; guard przez `TestBed.runInInjectionContext` lub `RouterTestingHarness` (`@angular/router/types/testing.d.ts:58-70`); dla linków logowania sprawdzać tylko atrybut `href` (jsdom nie implementuje nawigacji).

### 8. Testy backendu

- `SecurityMockMvcRequestPostProcessors` (spring-security-test 7.1.2): `oauth2Login()` `:401` (domyślny atrybut nazwy `sub`=`"user"`, `:1292`), `oidcLogin()` `:429`, `csrf()` `:180`. Nie wymagają prawdziwego `ClientRegistrationRepository`.
- Weryfikacja startu przepływu: `GET /oauth2/authorization/github` → 3xx, `Location` zaczyna się od `https://github.com/login/oauth/authorize?` i zawiera `redirect_uri=http://localhost/login/oauth2/code/github`.
- Atrapowe rejestracje (np. `src/test/resources/application.properties` z `client-id=test-github-id`, `client-secret` z atrapową wartością, analogicznie google) są warunkiem ładowania kontekstu dla wszystkich obecnych testów `@SpringBootTest`. Domyślne Google nie uruchamia discovery (tylko gdy ustawione `provider.google.issuer-uri`, `OAuth2ClientPropertiesMapper.java:80-87`) → brak ruchu sieciowego w testach.

## Code References

- `src/main/java/com/example/itcardbrawl/config/SecurityConfig.java:15-23` — łańcuch do rozszerzenia o `oauth2Login`, `csrf.spa()`, `logout`.
- `src/main/resources/application.properties:1` — miejsce na rejestracje z placeholderami env.
- `src/test/java/com/example/itcardbrawl/api/PingControllerTests.java:31-36` — kontrakt 401 bez `Location`, który musi przetrwać.
- `frontend/proxy.conf.json:2-5` — dodać `/oauth2`, `/login/oauth2`.
- `frontend/src/app/app.config.ts:5` — `provideRouter`, `withInterceptors`.
- `frontend/src/app/app.ts:12-23`, `app.html:1-13` — dziś widok pinga; staje się shellem z `<router-outlet>` lub ustępuje lobby.
- `frontend/src/app/app.spec.ts:10-20` — wzorzec testów HTTP.

## Architecture Insights

- Jeden origin w dev zapewnia zachowany `Host` w proxy; w produkcji (F-02) Spring sam serwuje SPA, więc `baseUrl` będzie prawdziwą domeną — te same ścieżki callbacku, inny host do rejestracji u dostawców.
- Granica odpowiedzialności: Spring = tożsamość, sesja, CSRF; SPA = tylko odczyt `/api/me` i nawigacje. Żaden token dostawcy nie trafia do przeglądarki.
- Persistencja użytkowników nie jest potrzebna dla outcome S-01 (PRD `context/foundation/prd.md:125`: konto służy do logowania, bez zapisywanego postępu); `/api/me` może czytać wprost z principala sesji. Brak bazy danych w repo.

## Historical Context (from prior changes)

- `context/archive/2026-11-16-spa-api-walking-skeleton/plan.md` — świadomie przygotował `SecurityConfig` pod S-01 („S-01 doda do tego samego łańcucha `oauth2Login`”) i odłożył OAuth/sesję/CSRF do S-01 (§What We're NOT Doing) — wspierane przez obecny kod.
- `context/archive/2026-11-16-spa-api-walking-skeleton/reviews/impl-review.md` F1 — `anyRequest().authenticated()` zablokuje statyczne zasoby SPA serwowane przez Springa; dotyczy F-02, nie S-01 (w dev SPA serwuje `ng serve`).
- `context/foundation/shape-notes.md:33,68` — wcześniejsza decyzja „Google/GitHub”; potwierdzona przez użytkownika 2026-11-18 (oba).

## Related Research

Brak innych `research.md` w `context/changes/**` ani `context/archive/**`.

## Open Questions

- **Konfiguracja sekretów lokalnie** — env vs git-ignorowany plik `.env`/profil `local` (wybór użytkownika; oba działają).
- **Rejestracja aplikacji u dostawców** (GitHub OAuth App, Google Cloud OAuth client + consent screen) — krok ręczny użytkownika; bez niego weryfikacja ręczna niemożliwa.
- **Kształt `/api/me`** — czy dołączyć `avatarUrl`; czy osobny `GET /api/csrf` zamiast ładowania tokenu w `/api/me`.
- **Router (b) vs przełącznik (a)** — rekomendacja (b); decyzja przy planowaniu.
- Do potwierdzenia ręcznie: obecność cookie `XSRF-TOKEN` przed pierwszym POST oraz `redirect_uri` w `Location` z `curl -i http://localhost:4200/oauth2/authorization/github`.
