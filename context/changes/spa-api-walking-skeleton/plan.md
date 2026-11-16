# Szkielet SPA ↔ API (spa-api-walking-skeleton) Implementation Plan

## Overview

Pierwszy element roadmapy (F-01): cienka aplikacja Angular w `frontend/` uruchamia się lokalnie i wyświetla odpowiedź z endpointu JSON Spring Boota, przy czym przeglądarka widzi **jedno źródło** — wywołania `/api/**` idą przez proxy dev servera Angulara do Springa (kształt z `context/foundation/tech-stack.md` §Post-bootstrap notes). Przy okazji powstaje minimalny łańcuch Spring Security, który S-01 (`external-login-gate`) tylko rozszerzy o `oauth2Login`.

## Current State Analysis

- Backend to czysty scaffold Spring Boot 4.1.2 / Java 21: `src/main/java/com/example/itcardbrawl/ItcardbrawlApplication.java` + test `contextLoads` (`src/test/java/com/example/itcardbrawl/ItcardbrawlApplicationTests.java`). Brak kontrolerów; `application.properties` zawiera tylko `spring.application.name`.
- `pom.xml` ma już `spring-boot-starter-security`, `spring-boot-starter-security-oauth2-client`, `spring-boot-starter-webmvc` oraz ich `-test` startery. Brak `SecurityFilterChain` i rejestracji OAuth2 → dziś każdy endpoint jest chroniony domyślną konfiguracją (form login + wygenerowane hasło), więc SPA dostałoby stronę logowania/401 zamiast JSON.
- Frontend nie istnieje. Lokalnie: Node 24.21, npm 12.1 (Angular CLI 22.2.7 wymaga Node `^24.15`), brak globalnego `ng`. JDK 21 (Corretto).
- Brak `context/foundation/lessons.md`.

## Desired End State

- `GET /api/ping` zwraca `200` z JSON `{"status":"ok"}` bez logowania; każdy inny `/api/**` bez uwierzytelnienia zwraca `401` (nie przekierowanie, nie stronę logowania).
- `frontend/` zawiera projekt Angular 22 (standalone, zoneless, bez routera, bez SSR), którego root component przy starcie woła `/api/ping` i pokazuje wynik albo komunikat o niedostępnym API.
- `npm start` w `frontend/` serwuje aplikację na `http://localhost:4200`, a żądanie `/api/ping` w DevTools widnieje jako `http://localhost:4200/api/ping` (jedno źródło, brak CORS).
- `./mvnw test` i testy Vitest w `frontend/` przechodzą.

### Key Discoveries:

- Spring Security bez własnego łańcucha włącza form login — `pom.xml:33-40`, brak klasy konfiguracji w `src/main/java`.
- Łańcuch z wyłączonym form login i basic auth domyślnie odpowiada `403`; dla SPA (i późniejszego S-01) potrzebny jest jawny entry point `401`.
- Spring Boot 4 rozbił autokonfiguracje testowe na moduły (`spring-boot-starter-webmvc-test` jest już w `pom.xml:61-65`); `@SpringBootTest` + `@AutoConfigureMockMvc` ładuje prawdziwy łańcuch security bez ręcznego `@Import`.
- `ng new` (CLI 22) domyślnie: Vitest jako test runner, aplikacja zoneless, standalone components; flagi `--routing=false --ssr=false --skip-git --ai-config=none` dają minimalny workspace.

## What We're NOT Doing

- Logowanie OAuth2, rejestracja dostawcy, sesja, CSRF dla mutacji — S-01.
- Pakowanie zbudowanego SPA do jara / serwowanie z `static/`, Dockerfile, CI — F-02 (tu tylko proxy deweloperskie).
- Router Angulara, zarządzanie stanem, biblioteki UI, style poza domyślnymi — dojdą, gdy wymaga ich wycinek.
- Testy E2E / Playwright — osobno przez `/10x-e2e-setup`, gdy pojawi się ryzyko wymagające przeglądarki.
- Ręczny test w czterech przeglądarkach z NFR — tu wystarczą dwie; pełna macierz przy wdrożonej wersji.

## Implementation Approach

Najpierw backend (testowalny samodzielnie MockMvc), potem frontend, który go konsumuje. Jedna nowa technologia na krok (cel `learn`): faza 1 nie dotyka Angulara, faza 2 nie zmienia już backendu.

## Phase 1: Backend — ping + minimalny łańcuch security

### Overview

Publiczny endpoint zdrowia API i łańcuch zabezpieczeń domyślnie odmawiający dostępu, odpowiadający `401` zamiast przekierowań.

### Changes Required:

#### 1. Endpoint ping

**File**: `src/main/java/com/example/itcardbrawl/api/PingController.java`

**Intent**: Najprostszy endpoint JSON, który SPA może wywołać, by udowodnić połączenie przez proxy.

**Contract**: `@RestController`, `GET /api/ping` → `200`, `Content-Type: application/json`, ciało `{"status":"ok"}` (rekord Java, np. `PingResponse(String status)`).

#### 2. Konfiguracja security

**File**: `src/main/java/com/example/itcardbrawl/config/SecurityConfig.java`

**Intent**: Zastąpić domyślny form login własnym `SecurityFilterChain`: ping publiczny, wszystko inne wymaga uwierzytelnienia, brak form login i HTTP Basic, nieuwierzytelnione żądania dostają `401`. S-01 doda do tego samego łańcucha `oauth2Login`.

**Contract**: bean `SecurityFilterChain`; reguły `"/api/ping"` → `permitAll`, `anyRequest()` → `authenticated`; `formLogin` i `httpBasic` wyłączone; `exceptionHandling` z `HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)`.

```java
http
    .authorizeHttpRequests(auth -> auth
        .requestMatchers("/api/ping").permitAll()
        .anyRequest().authenticated())
    .formLogin(AbstractHttpConfigurer::disable)
    .httpBasic(AbstractHttpConfigurer::disable)
    .exceptionHandling(e -> e.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)));
```

#### 3. Testy MockMvc

**File**: `src/test/java/com/example/itcardbrawl/api/PingControllerTests.java`

**Intent**: Udowodnić kontrakt endpointu i default-deny łańcucha.

**Contract**: `@SpringBootTest` + `@AutoConfigureMockMvc`; przypadki: `GET /api/ping` bez uwierzytelnienia → `200` i `$.status == "ok"`; `GET /api/anything` bez uwierzytelnienia → `401` (bez nagłówka `Location`).

### Success Criteria:

#### Automated Verification:

- Testy backendu przechodzą: `./mvnw test`
- Test `GET /api/ping` → 200 `{"status":"ok"}` bez logowania przechodzi
- Test nieuwierzytelnionego `GET /api/anything` → 401 przechodzi

#### Manual Verification:

- Po `./mvnw spring-boot:run` adres `http://localhost:8080/api/ping` w przeglądarce pokazuje `{"status":"ok"}`, a `http://localhost:8080/api/foo` zwraca 401 bez strony logowania

**Implementation Note**: After completing this phase and all automated verification passes, pause here for manual confirmation from the human that the manual testing was successful before proceeding to the next phase.

---

## Phase 2: Frontend — szkielet Angular z proxy

### Overview

Minimalny workspace Angular w `frontend/`, który przez proxy dev servera woła `/api/ping` i pokazuje stan ładowania, sukcesu albo błędu.

### Changes Required:

#### 1. Wygenerowanie workspace

**File**: `frontend/` (nowy katalog)

**Intent**: Postawić projekt Angular bez struktur „na zapas”, w istniejącym repo.

**Contract**: z katalogu głównego repo: `npx @angular/cli@22 new itcardbrawl --directory frontend --routing=false --ssr=false --style=css --skip-git --ai-config=none --package-manager=npm`. Wygenerowany `frontend/.gitignore` zostaje (wyklucza `node_modules`, `dist`, `.angular`). Placeholderowy szablon `app.html` zastąpić minimalnym widokiem (pkt 4).

#### 2. Proxy deweloperskie

**File**: `frontend/proxy.conf.json`, `frontend/angular.json`

**Intent**: Przeglądarka rozmawia tylko z `localhost:4200`; dev server przekazuje `/api` do Springa na `:8080`.

**Contract**: `proxy.conf.json` = `{ "/api": { "target": "http://localhost:8080", "secure": false } }`; w `angular.json` → `projects.itcardbrawl.architect.serve.options.proxyConfig: "proxy.conf.json"`. Kod SPA używa wyłącznie względnych URL-i (`/api/...`), nigdy `http://localhost:8080`.

#### 3. Serwis API

**File**: `frontend/src/app/ping.service.ts`, `frontend/src/app/app.config.ts`

**Intent**: Jedno miejsce wywołania API, wzorzec dla kolejnych wycinków.

**Contract**: `provideHttpClient()` dodane do `appConfig.providers`; `PingService` (`providedIn: 'root'`) z metodą `ping(): Observable<{ status: string }>` wołającą `GET /api/ping`.

#### 4. Root component

**File**: `frontend/src/app/app.ts`, `frontend/src/app/app.html`

**Intent**: Przy starcie wywołać ping i pokazać wynik — dowód działania szkieletu.

**Contract**: stan w sygnale z trzema wariantami: ładowanie („Łączenie z API…”), sukces („API: ok”), błąd („API niedostępne”). Nagłówek z nazwą „ItCardBrawl”.

#### 5. Test komponentu

**File**: `frontend/src/app/app.spec.ts`

**Intent**: Zastąpić wygenerowany test placeholdera testem zachowania wobec API.

**Contract**: TestBed z `provideHttpClient()` + `provideHttpClientTesting()`; `HttpTestingController.expectOne('/api/ping')`; przypadki: odpowiedź `{status:'ok'}` → widoczne „API: ok”; odpowiedź błędu (np. 504 z proxy) → widoczne „API niedostępne”.

#### 6. Instrukcja uruchomienia

**File**: `frontend/README.md`

**Intent**: Zastąpić wygenerowany README krótką instrukcją lokalnego uruchomienia dwóch procesów.

**Contract**: sekcja „Uruchomienie lokalne”: (1) `./mvnw spring-boot:run` w katalogu głównym, (2) `npm install` + `npm start` w `frontend/`, (3) otwórz `http://localhost:4200`; komenda testów.

### Success Criteria:

#### Automated Verification:

- Testy frontendu przechodzą: `cd frontend && npx ng test --watch=false`
- Build produkcyjny przechodzi: `cd frontend && npm run build`
- `frontend/node_modules` nie jest śledzone przez git: `git status --porcelain frontend | grep node_modules` zwraca pusto
- Testy backendu nadal przechodzą: `./mvnw test`

#### Manual Verification:

- Przy uruchomionym Springu i `npm start` strona `http://localhost:4200` pokazuje „API: ok”
- W DevTools → Network żądanie pinga ma URL `http://localhost:4200/api/ping` i nie ma błędów CORS w konsoli
- Po zatrzymaniu Springa i odświeżeniu strona pokazuje „API niedostępne”
- Działa w Chrome i Firefox

**Implementation Note**: After completing this phase and all automated verification passes, pause here for manual confirmation from the human that the manual testing was successful.

---

## Testing Strategy

### Unit Tests:

- Backend (MockMvc, pełny kontekst): publiczny ping, `401` dla innych `/api/**` bez przekierowania.
- Frontend (Vitest + `HttpTestingController`): render sukcesu i błędu na podstawie odpowiedzi `/api/ping`.

### Integration Tests:

- Brak automatycznych — proxy `ng serve` ↔ Spring weryfikowane ręcznie (świadoma decyzja; E2E później przez `/10x-e2e-setup`).

### Manual Testing Steps:

1. `./mvnw spring-boot:run`, w drugim terminalu `cd frontend && npm start`.
2. Otwórz `http://localhost:4200` → „API: ok”; sprawdź URL żądania w Network.
3. Zatrzymaj Springa, odśwież → „API niedostępne”.
4. Powtórz krok 2 w Firefox.

## Performance Considerations

Brak — pojedyncze żądanie przy starcie.

## Migration Notes

Brak danych i istniejących użytkowników. Domyślny form login Springa znika (świadomie); S-01 dodaje logowanie OAuth2 do `SecurityConfig`.

## References

- Roadmap: `context/foundation/roadmap.md` (F-01)
- Tech stack i kształt proxy: `context/foundation/tech-stack.md` §Post-bootstrap notes
- Zależności security: `pom.xml:33-40`
- Następny wycinek: S-01 `external-login-gate`

## Progress

> Convention: `- [ ]` pending, `- [x]` done. Append ` — <commit sha>` when a step lands. Do not rename step titles. See `references/progress-format.md`.

### Phase 1: Backend — ping + minimalny łańcuch security

#### Automated

- [x] 1.1 Testy backendu przechodzą: `./mvnw test`
- [x] 1.2 Test `GET /api/ping` → 200 `{"status":"ok"}` bez logowania przechodzi
- [x] 1.3 Test nieuwierzytelnionego `GET /api/anything` → 401 przechodzi

#### Manual

- [x] 1.4 Po `./mvnw spring-boot:run` adres `http://localhost:8080/api/ping` w przeglądarce pokazuje `{"status":"ok"}`, a `http://localhost:8080/api/foo` zwraca 401 bez strony logowania

### Phase 2: Frontend — szkielet Angular z proxy

#### Automated

- [ ] 2.1 Testy frontendu przechodzą: `cd frontend && npx ng test --watch=false`
- [ ] 2.2 Build produkcyjny przechodzi: `cd frontend && npm run build`
- [ ] 2.3 `frontend/node_modules` nie jest śledzone przez git
- [ ] 2.4 Testy backendu nadal przechodzą: `./mvnw test`

#### Manual

- [ ] 2.5 Przy uruchomionym Springu i `npm start` strona `http://localhost:4200` pokazuje „API: ok”
- [ ] 2.6 W DevTools → Network żądanie pinga ma URL `http://localhost:4200/api/ping` i nie ma błędów CORS w konsoli
- [ ] 2.7 Po zatrzymaniu Springa i odświeżeniu strona pokazuje „API niedostępne”
- [ ] 2.8 Działa w Chrome i Firefox
