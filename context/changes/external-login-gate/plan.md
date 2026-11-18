# Logowanie zewnętrznym kontem (external-login-gate) Implementation Plan

## Overview

Element roadmapy S-01: gracz loguje się kontem **GitHub albo Google** i widzi lobby ze swoją nazwą (i awatarem), a niezalogowany użytkownik wchodzący do gry trafia na ekran logowania (PRD FR-001, §Access Control). Kształt z `context/foundation/tech-stack.md` §Post-bootstrap notes: Spring robi `oauth2Login` i trzyma sesję w cookie, SPA nie widzi żadnych tokenów, a przeglądarka widzi jedno źródło (`localhost:4200`, proxy `ng serve`).

## Current State Analysis

- `SecurityConfig` (`src/main/java/com/example/itcardbrawl/config/SecurityConfig.java:15-23`): `/api/ping` publiczny, reszta `authenticated()`, formLogin/httpBasic wyłączone, `HttpStatusEntryPoint(401)`. Brak `oauth2Login`, CSRF domyślny (nieużywany).
- `application.properties` ma tylko `spring.application.name`; brak `src/test/resources`. Zależności security + oauth2-client (+ `-test`) już są w `pom.xml`.
- Testy backendu: `PingControllerTests` (ping 200; `/api/anything` → 401 bez `Location`) i `ItcardbrawlApplicationTests.contextLoads`.
- Frontend (`frontend/`, Angular 22, zoneless, Vitest): brak routera (`app.config.ts:5`), root `App` pokazuje status pinga (`app.ts`, `app.html`, `ping.service.ts`, `app.spec.ts`), proxy tylko `/api` (`proxy.conf.json:2-5`). `@angular/router` jest w zależnościach.
- Brak bazy danych; v1 nie zapisuje postępu (PRD §Access Control) — tożsamość gracza żyje tylko w sesji.
- Brak `context/foundation/lessons.md`.

## Desired End State

- Niezalogowany gracz otwierający `http://localhost:4200/` ląduje na `/sign-in` z dwoma przyciskami: „Zaloguj przez GitHub” i „Zaloguj przez Google”.
- Po zgodzie u dostawcy wraca na `http://localhost:4200/` i widzi lobby: „Witaj, {displayName}” + awatar (jeśli dostawca go podał) + przycisk „Wyloguj”.
- Anulowanie zgody → `/sign-in?error` z komunikatem „Logowanie nie powiodło się — spróbuj ponownie.”; backend niedostępny → `/sign-in?unavailable` z „Serwer gry niedostępny — spróbuj później.”; wylogowanie → `/sign-in?logout` z „Wylogowano.”.
- `GET /api/me` → `200 {provider, displayName, avatarUrl}` dla zalogowanego (i wystawia cookie `XSRF-TOKEN`), `401` bez `Location` dla niezalogowanego. `POST /api/logout` z nagłówkiem XSRF → `204`, bez → `403`.
- Sekrety OAuth tylko w git-ignorowanym `.env.properties` (lub w zmiennych środowiskowych); w repo wyłącznie placeholdery i `.env.properties.example`.
- `./mvnw test`, `npx ng test --watch=false` i `npm run build` przechodzą.

### Key Discoveries:

- Jawny `authenticationEntryPoint` wygrywa nad domyślnymi z `oauth2Login` i wyłącza generowaną stronę `/login` (research §4) — kontrakt `/api/** → 401` z `PingControllerTests.java:31-36` przetrwa bez zmian w teście.
- Bez rejestracji klientów kontekst Springa się nie ładuje (`OAuth2ClientProperties` odrzuca pusty `client-id`, nierozwiązany placeholder przerywa start) — wszystkie `@SpringBootTest` potrzebują atrapowych wartości (research §2, §8).
- `csrf(c -> c.spa())` (Security 7) daje cookie `XSRF-TOKEN`/nagłówek `X-XSRF-TOKEN` zgodne z domyślnymi Angulara, ale token jest odroczony — cookie powstaje dopiero po `CsrfToken.getToken()`; token jest też czyszczony przy logowaniu (research §6).
- Proxy Vite w Angular 22 przy konfiguracji obiektowej zachowuje `Host` (`changeOrigin` domyślnie `false`), więc `redirect_uri` = `http://localhost:4200/login/oauth2/code/{id}`; klucze dopasowywane prefiksem — stąd wąski `/login/oauth2`, nie `/login` (research §3).
- `FetchBackend` (domyślny w v22) podążyłby za 302 do dostawcy i padł na CORS — dlatego API musi odpowiadać 401, a logowanie to pełna nawigacja `<a href>` (research §4, §7).
- GitHub: `getName()` = numeryczne `id`, nazwa w `name` (bywa null) → fallback `login`, awatar `avatar_url`. Google (OIDC): `getName()` = `sub`, nazwa `name` → `given_name` → `email`, awatar `picture` (research §5).

## What We're NOT Doing

- Trwałe konta / baza danych użytkowników — `/api/me` czyta wprost z principala sesji (PRD: konto tylko do logowania).
- Pobieranie emaila GitHuba (`user:email`) i jakichkolwiek innych danych poza nazwą i awatarem.
- Produkcyjne adresy zwrotne, serwowanie SPA przez Springa, `permitAll` dla zasobów statycznych — F-02.
- Wylogowanie u dostawcy (single sign-out) — kończymy tylko sesję gry.
- Przekierowanie zalogowanego gracza z `/sign-in` do lobby — ekran logowania jest zawsze dostępny.
- Testy E2E / Playwright — pełny przepływ z prawdziwymi dostawcami weryfikowany ręcznie.
- „Zapamiętaj mnie”, konfiguracja czasu życia sesji, sesje współdzielone między instancjami.

## Implementation Approach

Najpierw backend, w pełni testowalny MockMvc bez prawdziwych dostawców (post-processory `oauth2Login()`/`oidcLogin()`/`csrf()`). Potem frontend w dwóch krokach, żeby nowe pojęcia Angulara przychodziły grupami: faza 2 — router, guard, serwis ze stanem w sygnale, dwa ekrany; faza 3 — mutacja z CSRF (wylogowanie) i interceptor HTTP. Fazy 2–3 nie zmieniają backendu.

## Critical Implementation Details

- **Atrapy klientów w testach bez przesłaniania głównej konfiguracji**: plik testowy musi leżeć w `src/test/resources/config/application.properties`, a nie w `src/test/resources/application.properties` — ten drugi przesłoniłby na classpath główny `application.properties` (znikną rejestracje i import `.env.properties`, a testy przestałyby sprawdzać prawdziwą konfigurację). `classpath:/config/` jest ładowany dodatkowo, z wyższym priorytetem, więc wystarczy w nim podać wartości placeholderów. Testy nie mogą asertować konkretnego `client_id` (lokalny `.env.properties` dewelopera może mieć pierwszeństwo).
- **Komunikaty z parametrów bez wartości**: `?error`, `?unavailable`, `?logout` nie mają wartości — wykrywać obecność (`queryParamMap.has(...)`), nie prawdziwość wartości (pusty string jest falsy).
- **Kolejność CSRF po logowaniu**: po callbacku token jest wyczyszczony; nowe cookie `XSRF-TOKEN` powstaje dopiero przy sondzie `/api/me` wykonywanej przez guard lobby — dlatego `/api/me` musi wołać `getToken()`, a przycisk „Wyloguj” jest dostępny wyłącznie w lobby (za guardem).
  - **Odstępstwo (wdrożenie, 61f0fa8)**: `getToken()` w `/api/me` pominięto — w Spring Security 7.1.2 `csrf(c -> c.spa())` sam wystawia cookie `XSRF-TOKEN` (zweryfikowane na działającej aplikacji). Pilnuje tego test `MeControllerTests.responseIssuesXsrfTokenCookie` oraz ręczna weryfikacja 3.4; jeśli test zacznie padać po aktualizacji Springa, przywrócić parametr `CsrfToken` i `getToken()`.

## Phase 1: Backend — oauth2Login, /api/me, CSRF, wylogowanie

### Overview

Spring obsługuje logowanie GitHub/Google, trzyma sesję, udostępnia znormalizowany profil gracza i wylogowanie chronione CSRF. Sekrety poza repo.

### Changes Required:

#### 1. Rejestracje klientów i import sekretów

**File**: `src/main/resources/application.properties`

**Intent**: Zarejestrować dostawców `github` i `google` z wartościami z placeholderów oraz opcjonalnie wczytać git-ignorowany plik z sekretami.

**Contract**: `spring.config.import=optional:file:.env.properties`; `spring.security.oauth2.client.registration.github.client-id=${GITHUB_CLIENT_ID}`, `...github.client-secret=${GITHUB_CLIENT_SECRET}`, analogicznie `google` z `GOOGLE_CLIENT_ID`/`GOOGLE_CLIENT_SECRET`. Bez wartości domyślnych w placeholderach (brak konfiguracji ma przerwać start). Scope'y domyślne z `CommonOAuth2Provider` (Google `openid,profile,email`; GitHub `read:user`).

#### 2. Plik sekretów — przykład i ignorowanie

**File**: `.env.properties.example` (nowy), `.gitignore`

**Intent**: Udokumentować wymagane klucze i zagwarantować, że prawdziwy plik nigdy nie trafi do repo.

**Contract**: `.env.properties.example` z czterema kluczami (`GITHUB_CLIENT_ID=`, `GITHUB_CLIENT_SECRET=`, `GOOGLE_CLIENT_ID=`, `GOOGLE_CLIENT_SECRET=`) i komentarzem o callbackach; `.gitignore` zawiera `.env.properties`. Zmienne środowiskowe o tych samych nazwach nadal działają (mają wyższy priorytet).

#### 3. Łańcuch security

**File**: `src/main/java/com/example/itcardbrawl/config/SecurityConfig.java`

**Intent**: Rozszerzyć istniejący łańcuch o logowanie OAuth2, CSRF dla SPA i wylogowanie przez API, zachowując `401` dla nieuwierzytelnionych.

**Contract**: dodać `.oauth2Login(o -> o.defaultSuccessUrl("/", true).failureUrl("/sign-in?error"))`, `.csrf(c -> c.spa())`, `.logout(l -> l.logoutUrl("/api/logout").logoutSuccessHandler(new HttpStatusReturningLogoutSuccessHandler(HttpStatus.NO_CONTENT)))`. `HttpStatusEntryPoint(UNAUTHORIZED)`, `permitAll` dla `/api/ping` i wyłączone formLogin/httpBasic bez zmian.

#### 4. Endpoint profilu gracza

**File**: `src/main/java/com/example/itcardbrawl/api/MeController.java` (+ rekord odpowiedzi, np. `MeResponse`)

**Intent**: Zwrócić znormalizowaną tożsamość zalogowanego gracza niezależnie od dostawcy i przy okazji wystawić cookie CSRF dla SPA.

**Contract**: `GET /api/me` (wymaga uwierzytelnienia), parametry `OAuth2AuthenticationToken` i `CsrfToken` (wywołać `getToken()`), odpowiedź `{ "provider": "github"|"google", "displayName": string, "avatarUrl": string|null }`. Mapowanie:

| provider | displayName (pierwsza niepusta) | avatarUrl |
| --- | --- | --- |
| `github` | `name` → `login` → `getName()` | `avatar_url` |
| `google` | `name` → `given_name` → `email` → `getName()` | `picture` |

`provider` = `getAuthorizedClientRegistrationId()`. Brak atrybutu awatara → `null`.

> **Odstępstwo (wdrożenie)**: parametr `CsrfToken` / `getToken()` pominięty — zob. Critical Implementation Details → Kolejność CSRF po logowaniu.

#### 5. Konfiguracja testowa

**File**: `src/test/resources/config/application.properties` (nowy)

**Intent**: Pozwolić kontekstowi testów załadować się bez prawdziwych sekretów, nie przesłaniając głównej konfiguracji (zob. Critical Implementation Details).

**Contract**: `GITHUB_CLIENT_ID=test-github-id`, `GITHUB_CLIENT_SECRET=test-secret`, `GOOGLE_CLIENT_ID=test-google-id`, `GOOGLE_CLIENT_SECRET=test-secret`. Bez `issuer-uri` (brak discovery i ruchu sieciowego).

#### 6. Testy backendu

**File**: `src/test/java/com/example/itcardbrawl/api/MeControllerTests.java`, `src/test/java/com/example/itcardbrawl/config/SecurityFlowTests.java` (nowe)

**Intent**: Pokryć każdą decyzję backendu bez prawdziwych dostawców.

**Contract**: `@SpringBootTest` + `@AutoConfigureMockMvc` (wzorzec `PingControllerTests`). `oauth2Login()`/`oidcLogin()` z `clientRegistration` pobraną z wstrzykniętego `ClientRegistrationRepository` (`findByRegistrationId("github"|"google")`), żeby `provider` był prawdziwy. Przypadki:
- `/api/me`: GitHub z `name` → ta nazwa; GitHub bez `name` → `login`; GitHub `avatar_url` → `avatarUrl`; Google z `name`; Google bez `name` → `given_name`; bez obu → `email`; Google `picture` → `avatarUrl`; brak awatara → `null`; odpowiedź ustawia cookie `XSRF-TOKEN`; bez uwierzytelnienia → `401` bez `Location`.
- Start przepływu: `GET /oauth2/authorization/github` → 3xx, `Location` zaczyna się od `https://github.com/login/oauth/authorize`, a po zdekodowaniu zawiera `redirect_uri=http://localhost/login/oauth2/code/github`; analogicznie Google (`https://accounts.google.com/o/oauth2/v2/auth`, `.../code/google`).
- Wylogowanie: `POST /api/logout` z `oauth2Login()` + `csrf()` → `204`; bez `csrf()` → `403`; `GET /api/logout` nie wylogowuje (nie `204`).
- `PingControllerTests` i `ItcardbrawlApplicationTests` przechodzą bez zmian.

#### 7. Instrukcja konfiguracji dostawców

**File**: `frontend/README.md`

**Intent**: Opisać jednorazową konfigurację logowania lokalnie.

**Contract**: nowa sekcja „Logowanie — konfiguracja lokalna”: (1) GitHub OAuth App z callbackiem `http://localhost:4200/login/oauth2/code/github`; (2) Google Cloud OAuth client (Web) + ekran zgody, redirect `http://localhost:4200/login/oauth2/code/google`; (3) skopiować `.env.properties.example` → `.env.properties` w katalogu głównym i uzupełnić (lub ustawić zmienne środowiskowe).

### Success Criteria:

#### Automated Verification:

- Testy backendu przechodzą: `./mvnw test`
- `.env.properties` jest ignorowany: `git check-ignore .env.properties` zwraca ścieżkę
- W repo brak sekretów: `git grep -nE "client-secret=[^$]"` zwraca pusto

#### Manual Verification:

- Aplikacje zarejestrowane u GitHuba i Google z callbackami `http://localhost:4200/login/oauth2/code/{github,google}`, wartości w `.env.properties`
- `./mvnw spring-boot:run` startuje; `curl -i http://localhost:8080/api/me` → `401` bez `Location`
- `curl -i http://localhost:8080/oauth2/authorization/github` → `302` na `github.com/login/oauth/authorize` z prawdziwym `client_id`

**Implementation Note**: After completing this phase and all automated verification passes, pause here for manual confirmation from the human that the manual testing was successful before proceeding to the next phase.

---

## Phase 2: Frontend — bramka logowania

### Overview

SPA dostaje router: lobby za guardem sprawdzającym `/api/me`, publiczny ekran logowania z komunikatami. Widok pinga znika z UI.

### Changes Required:

#### 1. Proxy

**File**: `frontend/proxy.conf.json`

**Intent**: Przepuścić start przepływu i callback OAuth do Springa przez to samo źródło.

**Contract**: obok `/api` klucze `/oauth2` i `/login/oauth2` z `target: http://localhost:8080`, `secure: false`. **Bez** `changeOrigin` (zachowany `Host` → poprawny `redirect_uri`). Nie dodawać klucza `/login`.

#### 2. Stan tożsamości

**File**: `frontend/src/app/auth.service.ts` (nowy)

**Intent**: Jedno miejsce na profil zalogowanego gracza i odczyt `/api/me`.

**Contract**: typ `Player { provider: 'github' | 'google'; displayName: string; avatarUrl: string | null }`; `@Injectable({ providedIn: 'root' })`; tylko-do-odczytu sygnał `user: Signal<Player | null>`; `loadMe(): Observable<Player>` — `GET /api/me`, przy sukcesie ustawia `user`.

#### 3. Guard

**File**: `frontend/src/app/auth.guard.ts` (nowy)

**Intent**: Wpuścić do lobby tylko zalogowanego gracza; resztę skierować na logowanie z właściwym komunikatem.

**Contract**: `authGuard: CanActivateFn` (`inject()` na górze) — `loadMe()` → `true`; błąd `401` → `router.parseUrl('/sign-in')`; każdy inny błąd (status 0, 5xx, 504 z proxy) → `router.parseUrl('/sign-in?unavailable')`.

#### 4. Ekran logowania

**File**: `frontend/src/app/sign-in/sign-in.ts`, `sign-in.html` (+ `.css` opcjonalnie)

**Intent**: Ekran dla niezalogowanego gracza z wyborem dostawcy i komunikatami stanu.

**Contract**: dwa linki (pełna nawigacja, nie `routerLink`, nie HttpClient): `href="/oauth2/authorization/github"` „Zaloguj przez GitHub”, `href="/oauth2/authorization/google"` „Zaloguj przez Google”. Komunikaty wg obecności parametru: `error` → „Logowanie nie powiodło się — spróbuj ponownie.”, `unavailable` → „Serwer gry niedostępny — spróbuj później.” (komunikat `logout` dochodzi w fazie 3).

#### 5. Lobby

**File**: `frontend/src/app/lobby/lobby.ts`, `lobby.html` (+ `.css` opcjonalnie)

**Intent**: Pierwszy ekran zalogowanego gracza.

**Contract**: czyta `AuthService.user`; pokazuje „Witaj, {displayName}”; `<img>` z `avatarUrl` (`alt=""`, stały mały rozmiar) tylko gdy `avatarUrl !== null`.

#### 6. Trasy, konfiguracja i shell

**File**: `frontend/src/app/app.routes.ts` (nowy), `app.config.ts`, `app.ts`, `app.html`

**Intent**: Włączyć router i zamienić root component w shell.

**Contract**: trasy: `''` → `Lobby` z `canActivate: [authGuard]`; `'sign-in'` → `SignIn`; `'**'` → `redirectTo: ''`. Nazwy tras nie kolidują z prefiksami proxy (`/api`, `/oauth2`, `/login/oauth2`). `app.config.ts`: `provideRouter(routes)` obok istniejących providerów. `App`: nagłówek „ItCardBrawl” + `<router-outlet>`, bez logiki pinga.

#### 7. Usunięcie pinga z frontendu

**File**: `frontend/src/app/ping.service.ts` (usunąć), `frontend/src/app/app.spec.ts`

**Intent**: Lobby i guard zastępują widok statusu API; endpoint `/api/ping` zostaje w backendzie (health check dla F-02).

**Contract**: brak odwołań do `PingService` i `/api/ping` w `frontend/src`; `app.spec.ts` sprawdza tylko nagłówek i obecność `router-outlet`.

#### 8. Testy frontendu

**File**: `frontend/src/app/auth.guard.spec.ts`, `sign-in/sign-in.spec.ts`, `lobby/lobby.spec.ts` (nowe), `app.spec.ts`

**Intent**: Pokryć bramkę i oba ekrany.

**Contract**: Vitest + TestBed, `provideHttpClient()` + `provideHttpClientTesting()`, `httpTesting.verify()` w `afterEach` (wzorzec obecnego `app.spec.ts`). Guard przez `TestBed.runInInjectionContext`: `200` → `true` i ustawiony `user`; `401` → `UrlTree` `/sign-in`; `504` → `/sign-in?unavailable`. Sign-in: atrybuty `href` obu linków; komunikat dla `?error`, dla `?unavailable`, brak komunikatu bez parametrów. Lobby: nazwa gracza; `<img>` z `src` gdy jest `avatarUrl`; brak `<img>` gdy `null`.

### Success Criteria:

#### Automated Verification:

- Testy frontendu przechodzą: `cd frontend && npx ng test --watch=false`
- Build produkcyjny przechodzi: `cd frontend && npm run build`
- Brak odwołań do pinga we frontendzie: `git grep -nE "PingService|/api/ping" frontend/src` zwraca pusto
- Testy backendu nadal przechodzą: `./mvnw test`

#### Manual Verification:

- Niezalogowany: `http://localhost:4200/` → `/sign-in` z dwoma przyciskami
- Logowanie GitHubem → powrót na `http://localhost:4200/`, lobby z nazwą i awatarem
- Logowanie Google (w nowym oknie incognito) → lobby z nazwą i awatarem
- Anulowanie zgody u dostawcy → `/sign-in?error` z komunikatem błędu
- Spring zatrzymany, odświeżenie `/` → `/sign-in?unavailable` z komunikatem
- DevTools → Network: callback idzie na `localhost:4200/login/oauth2/code/...`, żadnych błędów CORS; po wejściu do lobby istnieje cookie `XSRF-TOKEN`

**Implementation Note**: After completing this phase and all automated verification passes, pause here for manual confirmation from the human that the manual testing was successful before proceeding to the next phase.

---

## Phase 3: Frontend — cykl sesji

### Overview

Wylogowanie z lobby (pierwsza mutacja z nagłówkiem XSRF) z potwierdzeniem na ekranie logowania oraz globalny interceptor kierujący na logowanie po wygaśnięciu sesji.

### Changes Required:

#### 1. Wylogowanie w serwisie

**File**: `frontend/src/app/auth.service.ts`

**Intent**: Zakończyć sesję gry po stronie Springa i wyczyścić stan SPA.

**Contract**: `logout(): Observable<void>` — `POST /api/logout` (pusty body; nagłówek `X-XSRF-TOKEN` dokłada wbudowany mechanizm `provideHttpClient()`), przy sukcesie `user` → `null`.

#### 2. Przycisk w lobby i komunikat na sign-in

**File**: `frontend/src/app/lobby/lobby.ts`, `lobby.html`, `frontend/src/app/sign-in/sign-in.ts`, `sign-in.html`

**Intent**: Dać graczowi wylogowanie i potwierdzić je.

**Contract**: lobby: przycisk „Wyloguj” → `logout()`; sukces → `router.navigateByUrl('/sign-in?logout')`; błąd inny niż 401 → komunikat w lobby „Nie udało się wylogować — spróbuj ponownie.” (401 obsługuje interceptor). Sign-in: parametr `logout` → „Wylogowano.”.

#### 3. Interceptor 401

**File**: `frontend/src/app/auth.interceptor.ts` (nowy), `app.config.ts`

**Intent**: Gdy sesja wygaśnie w trakcie korzystania, każde wywołanie API kończące się 401 kieruje gracza na logowanie — przygotowanie pod endpointy gry z S-02/S-03.

**Contract**: `authInterceptor: HttpInterceptorFn` — dla `HttpErrorResponse` ze statusem `401` i URL innym niż `/api/me` (sondę obsługuje guard) → `router.navigateByUrl('/sign-in')`; błąd zawsze propagowany dalej. Rejestracja: `provideHttpClient(withInterceptors([authInterceptor]))`.

#### 4. Testy

**File**: `frontend/src/app/auth.interceptor.spec.ts` (nowy), `lobby/lobby.spec.ts`, `sign-in/sign-in.spec.ts`

**Intent**: Pokryć wylogowanie i interceptor.

**Contract**: interceptor (`provideHttpClient(withInterceptors([authInterceptor]))` + `provideHttpClientTesting()`, szpieg na `Router.navigateByUrl`): `401` z `/api/other` → nawigacja na `/sign-in`; `401` z `/api/me` → brak nawigacji; `500` → brak nawigacji; w każdym przypadku subskrybent dostaje błąd. Lobby: klik „Wyloguj” → `expectOne({ method: 'POST', url: '/api/logout' })`; `204` → nawigacja na `/sign-in?logout` i `user()` = `null`; `500` → komunikat błędu, brak nawigacji. Sign-in: `?logout` → „Wylogowano.”.

### Success Criteria:

#### Automated Verification:

- Testy frontendu przechodzą: `cd frontend && npx ng test --watch=false`
- Build produkcyjny przechodzi: `cd frontend && npm run build`
- Testy backendu nadal przechodzą: `./mvnw test`

#### Manual Verification:

- Klik „Wyloguj” w lobby → `/sign-in?logout` z „Wylogowano.”; w DevTools żądanie `POST /api/logout` ma nagłówek `X-XSRF-TOKEN` i status `204`
- Po wylogowaniu wejście na `/` → `/sign-in` (sesja naprawdę zakończona)
- Wygaśnięcie sesji: usunięcie cookie `JSESSIONID` w DevTools (lub restart Springa) i odświeżenie `/` → `/sign-in`

**Implementation Note**: After completing this phase and all automated verification passes, pause here for manual confirmation from the human that the manual testing was successful.

---

## Testing Strategy

### Unit Tests:

- Backend (MockMvc + `spring-security-test`): mapowanie profilu dla obu dostawców z fallbackami nazwy i awatarem `null`, cookie `XSRF-TOKEN` z `/api/me`, `401` bez `Location`, start przepływu (`Location` + `redirect_uri`), wylogowanie z/bez CSRF.
- Frontend (Vitest + TestBed): guard (200/401/inny błąd), ekran logowania (`href`, trzy komunikaty), lobby (nazwa, awatar obecny/brak, wylogowanie sukces/błąd), interceptor (401 vs `/api/me` vs 500).

### Integration Tests:

- `@SpringBootTest` ładuje prawdziwy łańcuch security z atrapowymi rejestracjami — testy z fazy 1 są jednocześnie testami integracyjnymi konfiguracji.

### Manual Testing Steps:

1. `./mvnw spring-boot:run` (z uzupełnionym `.env.properties`), w drugim terminalu `cd frontend && npm start`.
2. `http://localhost:4200/` → ekran logowania; zaloguj GitHubem → lobby z nazwą i awatarem; wyloguj → „Wylogowano.”.
3. W oknie incognito to samo dla Google.
4. Anuluj zgodę u dostawcy → komunikat błędu logowania.
5. Zatrzymaj Springa, odśwież → komunikat „Serwer gry niedostępny”.
6. Zaloguj się, usuń `JSESSIONID`, odśwież → ekran logowania.

## Performance Considerations

Guard woła `/api/me` przy każdej aktywacji lobby — pojedyncze lekkie żądanie, bez cache'owania (świeży stan sesji ważniejszy niż milisekundy).

## Migration Notes

Brak danych do migracji. Po wdrożeniu S-01 każdy deweloper potrzebuje `.env.properties` (albo zmiennych środowiskowych) — bez nich Spring nie wystartuje. Produkcyjne callbacki dojdą w F-02.

## References

- Related research: `context/changes/external-login-gate/research.md`
- Roadmap: `context/foundation/roadmap.md` §S-01; PRD: `context/foundation/prd.md` §Access Control
- Poprzedni plan (wzorzec łańcucha i testów): `context/archive/2026-11-16-spa-api-walking-skeleton/plan.md`
- Łańcuch do rozszerzenia: `src/main/java/com/example/itcardbrawl/config/SecurityConfig.java:15-23`
- Kontrakt 401: `src/test/java/com/example/itcardbrawl/api/PingControllerTests.java:31-36`
- Wzorzec testu HTTP we frontendzie: `frontend/src/app/app.spec.ts:10-20`
- Spring Security CSRF dla SPA: https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html#csrf-integration-javascript-spa

## Progress

> Convention: `- [ ]` pending, `- [x]` done. Append ` — <commit sha>` when a step lands. Do not rename step titles. See `references/progress-format.md`.

### Phase 1: Backend — oauth2Login, /api/me, CSRF, wylogowanie

#### Automated

- [x] 1.1 Testy backendu przechodzą: `./mvnw test` — 61f0fa8
- [x] 1.2 `.env.properties` jest ignorowany: `git check-ignore .env.properties` zwraca ścieżkę — 61f0fa8
- [x] 1.3 W repo brak sekretów: `git grep -nE "client-secret=[^$]"` zwraca pusto — 61f0fa8

#### Manual

- [x] 1.4 Aplikacje zarejestrowane u GitHuba i Google z callbackami `http://localhost:4200/login/oauth2/code/{github,google}`, wartości w `.env.properties` — 61f0fa8
- [x] 1.5 `./mvnw spring-boot:run` startuje; `curl -i http://localhost:8080/api/me` → `401` bez `Location` — 61f0fa8
- [x] 1.6 `curl -i http://localhost:8080/oauth2/authorization/github` → `302` na `github.com/login/oauth/authorize` z prawdziwym `client_id` — 61f0fa8

### Phase 2: Frontend — bramka logowania

#### Automated

- [x] 2.1 Testy frontendu przechodzą: `cd frontend && npx ng test --watch=false` — 61f2bf3
- [x] 2.2 Build produkcyjny przechodzi: `cd frontend && npm run build` — 61f2bf3
- [x] 2.3 Brak odwołań do pinga we frontendzie: `git grep -nE "PingService|/api/ping" frontend/src` zwraca pusto — 61f2bf3
- [x] 2.4 Testy backendu nadal przechodzą: `./mvnw test` — 61f2bf3

#### Manual

- [x] 2.5 Niezalogowany: `http://localhost:4200/` → `/sign-in` z dwoma przyciskami — 61f2bf3
- [x] 2.6 Logowanie GitHubem → powrót na `http://localhost:4200/`, lobby z nazwą i awatarem — 61f2bf3
- [x] 2.7 Logowanie Google (w nowym oknie incognito) → lobby z nazwą i awatarem — 61f2bf3
- [x] 2.8 Anulowanie zgody u dostawcy → `/sign-in?error` z komunikatem błędu — 61f2bf3
- [x] 2.9 Spring zatrzymany, odświeżenie `/` → `/sign-in?unavailable` z komunikatem — 61f2bf3
- [x] 2.10 DevTools → Network: callback idzie na `localhost:4200/login/oauth2/code/...`, żadnych błędów CORS; po wejściu do lobby istnieje cookie `XSRF-TOKEN` — 61f2bf3

### Phase 3: Frontend — cykl sesji

#### Automated

- [x] 3.1 Testy frontendu przechodzą: `cd frontend && npx ng test --watch=false` — a05b33f
- [x] 3.2 Build produkcyjny przechodzi: `cd frontend && npm run build` — a05b33f
- [x] 3.3 Testy backendu nadal przechodzą: `./mvnw test` — a05b33f

#### Manual

- [x] 3.4 Klik „Wyloguj” w lobby → `/sign-in?logout` z „Wylogowano.”; w DevTools żądanie `POST /api/logout` ma nagłówek `X-XSRF-TOKEN` i status `204` — a05b33f
- [x] 3.5 Po wylogowaniu wejście na `/` → `/sign-in` (sesja naprawdę zakończona) — a05b33f
- [x] 3.6 Wygaśnięcie sesji: usunięcie cookie `JSESSIONID` w DevTools (lub restart Springa) i odświeżenie `/` → `/sign-in` — a05b33f
