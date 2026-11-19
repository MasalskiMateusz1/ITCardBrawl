# Wybór frakcji i stół partii (match-setup-table) Implementation Plan

## Overview

Element roadmapy S-02: zalogowany gracz wybiera jedną z 2 frakcji (**Developerzy** albo **Testerzy**), rozpoczyna partię przeciwko komputerowi i widzi stół: swoją rękę, liczbę kart w taliach, stres obu stron (0/30), zasób i czyja jest tura (PRD FR-002, FR-003, FR-007, US-01). Zagrywanie kart, tura komputera i koniec partii to S-03 — ten plan kończy się na poprawnie rozdanym, stale widocznym stole.

Plan ustala też podstawowe zasady startu partii, których PRD §Business Logic nie określał (roadmapa S-02 Unknowns, Open Roadmap Question 3): ręka startowa 5, losowy zaczynający, zasób 1→10 rosnący co turę, dobieranie 1 karty na turę z wyjątkiem pierwszej tury zaczynającego.

## Current State Analysis

- Backend ma tylko `MeController` (`src/main/java/com/example/itcardbrawl/api/MeController.java:14-31`), `PingController` i `SecurityConfig`. Brak kodu domeny, bazy danych i katalogu kart; v1 nie zapisuje postępu (PRD §Access Control).
- `SecurityConfig` (`src/main/java/com/example/itcardbrawl/config/SecurityConfig.java:23-45`): `anyRequest().authenticated()`, `csrf(c -> c.spa())` (POST wymaga nagłówka `X-XSRF-TOKEN` — Angular `HttpClient` wysyła go sam, tak już działa `POST /api/logout`), nieautoryzowane → 401 bez przekierowania.
- Fallback SPA (`src/main/java/com/example/itcardbrawl/web/SpaForwardController.java:21-46`): każda trasa GET spoza `/api`, `/oauth2`, `/login`, `/error` bez kropki w ostatnim segmencie dostaje `index.html` — nowe trasy `/new-match` i `/match` działają po odświeżeniu bez zmian w backendzie.
- `context/foundation/lessons.md`: każdy endpoint backendu pod `/api/**`; pilnuje tego `BackendRoutesTests`.
- Frontend (`frontend/`, Angular 22, zoneless, signals, Vitest): trasy `''` (Lobby, `authGuard`), `sign-in`, `**` (`frontend/src/app/app.routes.ts:6-10`). `AuthService` (`frontend/src/app/auth.service.ts`) to wzorzec serwisu HTTP z signalem; `authInterceptor` przekierowuje każdy 401 na `/sign-in` (`frontend/src/app/auth.interceptor.ts:9-24`). `Lobby` pokazuje powitanie i „Wyloguj” z polskim komunikatem błędu (`frontend/src/app/lobby/lobby.ts:26-36`).
- Testy: backend `@SpringBootTest` + `@AutoConfigureMockMvc` + `oauth2Login()` z rejestracjami `github`/`google` (`src/test/java/com/example/itcardbrawl/api/MeControllerTests.java:21-37`); frontend `TestBed` + `HttpTestingController` (`frontend/src/app/lobby/lobby.spec.ts`).
- CI (`.github/workflows/ci.yml`): `./mvnw -B verify`, `npx ng test --watch=false`, `npm run build`.

## Desired End State

Zalogowany gracz klika w lobby „Nowa partia”, na ekranie `/new-match` wybiera Developerów albo Testerów i klika „Rozpocznij partię”. Trafia na `/match`, gdzie widzi:

- swoją frakcję, stres `0/30`, zasób, liczbę kart w talii (25) i 5 kart na ręce (nazwa, typ, koszt, tekst; karty z puli neutralnej oznaczone „Neutralna”);
- frakcję komputera (druga grywalna), jego stres `0/30`, zasób, liczbę kart w talii (25) i na ręce (5) — bez treści jego kart;
- numer tury i czyja jest tura; zaczynający ma zasób `1/1`, druga strona `0/0`.

Odświeżenie `/match` pokazuje tę samą partię; nowy start z `/new-match` zastępuje poprzednią. Wejście na `/match` bez partii przenosi na `/new-match`. Weryfikacja: testy backendu i frontu w CI zielone + ręczne przejście przepływu lokalnie.

### Key Discoveries:

- Spring Boot 4.1 używa Jackson 3 (pakiet `tools.jackson.*`, nie `com.fasterxml.jackson.databind`) — katalog JSON wczytujemy auto-konfigurowanym mapperem JSON z kontekstu.
- `Collections.shuffle(List, RandomGenerator)` istnieje od Java 21 — losowanie da się wstrzyknąć jako `java.util.random.RandomGenerator` i podmienić w testach.
- `OAuth2AuthenticationToken.getAuthorizedClientRegistrationId()` + `getName()` (`MeController.java:17-19`) jednoznacznie identyfikują gracza między dostawcami — klucz magazynu partii.
- Roadmapa ma „frakcję neutralną” w §Parked (po v1); tutaj wchodzi jako **niegrywalna pula kart** wspólna dla obu talii — decyzja użytkownika z tej sesji planowania.

## What We're NOT Doing

- Zagrywanie kart, wydawanie zasobu, dobieranie w kolejnych turach, tura komputera, koniec partii i ekran wyniku (S-03).
- Wartości efektów kart (o ile postać podnosi stres, efekty wydarzeń) — karta w S-02 ma tylko nazwę, typ, koszt i tekst (S-03/S-04).
- Docelowa treść kart i balans — talie to placeholdery agenta zdefiniowane raz w katalogu (S-06).
- Wybór frakcji neutralnej przez gracza ani przez komputer — pula neutralna jest tylko składnikiem talii.
- Trwałe przechowywanie partii (baza, plik) — restart/wdrożenie serwera przerywa trwające partie.
- „Kontynuuj partię” w lobby i ostrzeżenie przed nadpisaniem trwającej partii (wybrano: odświeżenie wznawia, nowy start zastępuje).
- Mulligan, rekompensata dla drugiego gracza, wybór frakcji komputera inny niż „druga grywalna”.
- Aktualizacja PRD/roadmapy o zasady tury i frakcję neutralną (poza synchronizacją statusu S-02) — do zrobienia przez `/10x-roadmap`.

## Implementation Approach

Serwer jest źródłem prawdy: katalog kart to plik JSON w zasobach wczytywany i walidowany przy starcie (fail-fast), partia powstaje w czystej logice domeny z wstrzykiwanym `RandomGenerator`, a magazyn w pamięci trzyma jedną partię na gracza. API zwraca **widok** stołu z perspektywy gracza — ręka komputera nigdy nie opuszcza serwera. SPA dostaje dwa nowe widoki chronione `authGuard` i jeden serwis HTTP w stylu `AuthService`.

Zasady startu (stałe w jednym miejscu domeny): talia 30, ręka startowa 5, limit stresu 30, zasób startuje od 1 w pierwszej własnej turze i rośnie o 1 na turę do 10, każda tura zaczyna się dobraniem 1 karty z wyjątkiem pierwszej tury zaczynającego. Stan po rozdaniu = początek tury 1 zaczynającego: zaczynający `1/1` zasobu, druga strona `0/0`, obie po 5 kart na ręce i 25 w talii.

## Critical Implementation Details

- **Jackson 3 w Boot 4** — przy wczytywaniu `factions.json` używaj auto-konfigurowanego mappera z kontekstu Springa (Jackson 3, `tools.jackson.databind`); import z `com.fasterxml.jackson.databind` się nie skompiluje albo pociągnie drugą wersję biblioteki.
- **Deterministyczne testy losowania** — tasowanie i wybór zaczynającego muszą iść przez ten sam wstrzyknięty `RandomGenerator`; testy API nie zakładają, kto zaczyna — sprawdzają relację (strona aktywna ma zasób `1/1`, druga `0/0`).

## Phase 1: Katalog kart i model startu partii

### Overview

Statyczny katalog: 2 grywalne frakcje (Developerzy, Testerzy), niegrywalna pula neutralna i listy talii po 30 kart, walidowany przy starcie aplikacji. Czysta logika domeny tworzy partię według zasad startu. Bez HTTP.

### Changes Required:

#### 1. Dane katalogu (placeholdery)

**File**: `src/main/resources/game/factions.json`

**Intent**: Raz zdefiniowane placeholderowe dane, używane przez każdą partię i podmieniane treściowo w S-06 bez zmian kodu. Frakcje z humorem IT: Developerzy i Testerzy, plus pula „Neutralne” (np. PM, Scrum Master, „Daily standup”).

**Contract**: Trzy sekcje:
- `factions`: `{ id, name, description, playable }` — `developers` (Developerzy, playable), `testers` (Testerzy, playable), `neutral` (Neutralne, `playable: false`).
- `cards`: `{ id, faction, name, type: CHARACTER|EVENT, cost: 1..10, text }` — po ~10 unikalnych kart na grywalną frakcję i ~10 neutralnych, z mieszanką postaci i wydarzeń oraz rozkładem kosztów 1–10.
- `decks`: `{ <playableFactionId>: [ { card, copies } ] }` — talia = karty frakcji + część neutralnych, suma kopii = 30 (np. 10 frakcyjnych × 2 + 10 neutralnych × 1).

#### 2. Model i wczytanie katalogu

**File**: `src/main/java/com/example/itcardbrawl/game/` — `Faction`, `Card`, `CardType`, `DeckEntry`, `CardCatalog` (+ komponent wczytujący)

**Intent**: Niemutowalne rekordy domeny i bean `CardCatalog` zbudowany z `factions.json` przy starcie; niepoprawne dane zatrzymują start aplikacji z czytelnym komunikatem, zamiast psuć partię w trakcie gry.

**Contract**: `CardCatalog` udostępnia: grywalne frakcje (kolejność z pliku), frakcję/kartę po id, rozwiniętą talię frakcji (lista 30 `Card`). Walidacja (wyjątek przy starcie): unikalne id frakcji i kart; karta wskazuje istniejącą frakcję; koszt w 1–10; dokładnie 2 grywalne frakcje i każda ma talię; brak talii dla frakcji niegrywalnej; w talii tylko karty tej frakcji lub neutralne; `copies` 1–2; suma 30. Walidacja jest wywoływalna na danych w pamięci (testy bez plików).

#### 3. Zasady i tworzenie partii

**File**: `src/main/java/com/example/itcardbrawl/game/` — `MatchRules`, `Match`, `MatchSide`, `CardInstance`, `MatchFactory`; bean `RandomGenerator` w konfiguracji gry

**Intent**: Jedno miejsce na stałe zasad i tworzenie stanu startowego partii, w pełni testowalne bez Springa.

**Contract**:
- `MatchRules`: `DECK_SIZE = 30`, `STARTING_HAND = 5`, `STRESS_LIMIT = 30`, `RESOURCE_CAP = 10`, `DRAW_PER_TURN = 1` (zaczynający pomija dobranie w swojej pierwszej turze — reguła wykonywana od S-03, tu dokumentuje stan startowy).
- `MatchFactory.create(String playerFactionId)` → `Match`; nieznana lub niegrywalna frakcja → `IllegalArgumentException`. Komputer dostaje drugą grywalną frakcję. Każda talia rozwinięta do 30 `CardInstance` (unikalny w partii `instanceId` + `Card`), potasowana `Collections.shuffle(list, random)`; z wierzchu po 5 kart na rękę. Zaczynający losowany tym samym `RandomGenerator`.
- `Match`: `id` (UUID), `turnNumber = 1`, aktywna strona (`PLAYER`|`OPPONENT`), dwie `MatchSide` (`faction`, `deck`, `hand`, `stress = 0`, `resourceCurrent`, `resourceMax`). Aktywna strona `1/1`, druga `0/0`.

#### 4. Testy jednostkowe domeny

**File**: `src/test/java/com/example/itcardbrawl/game/CardCatalogTests.java`, `MatchFactoryTests.java`

**Intent**: Pokryć walidację katalogu i zasady startu, w tym obie gałęzie losowania zaczynającego.

**Contract**: Katalog: prawdziwy `factions.json` się wczytuje (2 grywalne frakcje: Developerzy, Testerzy; każda talia 30, zawiera karty neutralne); każdy błąd walidacji z listy wyżej ma test na danych w pamięci. Fabryka (sterowalny `RandomGenerator`): ręce 5 / talie 25 po obu stronach; stres 0; komputer ma drugą frakcję; gracz zaczyna → gracz `1/1`, komputer `0/0` (i odwrotnie); `instanceId` unikalne; frakcja neutralna/nieznana → wyjątek; ten sam seed → ta sama ręka.

### Success Criteria:

#### Automated Verification:

- Testy backendu przechodzą: `./mvnw -B verify`
- `CardCatalogTests` pokrywa każdy przypadek walidacji katalogu i wczytanie prawdziwego `factions.json`
- `MatchFactoryTests` pokrywa obie gałęzie zaczynającego i odrzucenie frakcji neutralnej

#### Manual Verification:

- Przegląd placeholderów w `factions.json`: nazwy kart czytelne dla ludzi z IT, obie frakcje mają postacie i wydarzenia w różnych kosztach

**Implementation Note**: After completing this phase and all automated verification passes, pause here for manual confirmation from the human that the manual testing was successful before proceeding to the next phase.

---

## Phase 2: API partii

### Overview

Magazyn partii w pamięci (jedna na gracza) i trzy endpointy pod `/api`, zwracające widok stołu z perspektywy gracza.

### Changes Required:

#### 1. Magazyn partii

**File**: `src/main/java/com/example/itcardbrawl/game/MatchStore.java`

**Intent**: Trzymać bieżącą partię każdego gracza w pamięci procesu; nowy start zastępuje poprzednią.

**Contract**: Bean z `ConcurrentHashMap<String, Match>`; `save(playerKey, match)` (nadpisuje), `findCurrent(playerKey)` → `Optional<Match>`. Klucz gracza = `"<registrationId>:<principalName>"` z `OAuth2AuthenticationToken`.

#### 2. Kontroler i widok stołu

**File**: `src/main/java/com/example/itcardbrawl/api/MatchController.java` (+ rekordy widoku)

**Intent**: Wystawić katalog frakcji i cykl „start / bieżąca partia”, nigdy nie ujawniając ręki ani kolejności talii komputera.

**Contract** (wszystko `authenticated`, JSON):
- `GET /api/factions` → `200 [ { id, name, description } ]` — tylko grywalne frakcje.
- `POST /api/matches` body `{ "factionId": "developers" }` → `201` + `MatchView`; brak/nieznana/niegrywalna frakcja → `400`.
- `GET /api/matches/current` → `200` + `MatchView` albo `404` gdy gracz nie ma partii.
- `MatchView`: `{ matchId, turn: { number, active: "PLAYER"|"OPPONENT" }, player: { faction: {id,name}, stress, stressLimit, resource: {current,max}, deckCount, hand: [ { instanceId, cardId, name, type, cost, text, neutral } ] }, opponent: { faction: {id,name}, stress, stressLimit, resource: {current,max}, deckCount, handCount } }`.

#### 3. Testy API

**File**: `src/test/java/com/example/itcardbrawl/api/MatchControllerTests.java`

**Intent**: Sprawdzić kontrakt HTTP, bezpieczeństwo i semantykę „jedna partia na gracza” wzorem `MeControllerTests`.

**Contract**: `factions` zwraca 2 pozycje bez `neutral`; start → 201, ręka 5, `deckCount` 25 po obu stronach, stres 0/30, strona aktywna ma `1/1`, druga `0/0`; odpowiedź nie zawiera kart przeciwnika (brak pola `opponent.hand`); `current` po starcie zwraca ten sam `matchId`; drugi start zmienia `matchId`; `current` bez partii → 404; `factionId` neutralne/nieznane/brak → 400; dwóch różnych graczy (`oauth2Login` z różnymi `name`) ma niezależne partie; bez logowania wszystkie trzy → 401; `POST` bez CSRF → 403.

### Success Criteria:

#### Automated Verification:

- Testy backendu przechodzą: `./mvnw -B verify`
- `MatchControllerTests` pokrywa 201/400/404/401/403, zastępowanie partii i izolację graczy
- `BackendRoutesTests` dalej zielone (nowe endpointy tylko pod `/api`)

#### Manual Verification:

- Lokalnie po zalogowaniu `GET /api/factions` i `GET /api/matches/current` (404) w przeglądarce zwracają oczekiwany JSON

**Implementation Note**: After completing this phase and all automated verification passes, pause here for manual confirmation from the human that the manual testing was successful before proceeding to the next phase.

---

## Phase 3: Ekran wyboru frakcji

### Overview

Serwis HTTP gry, chroniona trasa `/new-match` z wyborem frakcji i startem partii oraz link z lobby.

### Changes Required:

#### 1. Serwis i typy gry

**File**: `frontend/src/app/game/game.service.ts`, `frontend/src/app/game/match.ts`

**Intent**: Jedno miejsce na wywołania API gry, wzorem `AuthService`.

**Contract**: Typy `FactionSummary`, `MatchView` (lustro kontraktu z Fazy 2). Metody: `factions(): Observable<FactionSummary[]>` (`GET /api/factions`), `startMatch(factionId): Observable<MatchView>` (`POST /api/matches`), `currentMatch(): Observable<MatchView>` (`GET /api/matches/current`). 401 obsługuje istniejący `authInterceptor`.

#### 2. Widok `/new-match`

**File**: `frontend/src/app/new-match/new-match.{ts,html,css,spec.ts}`, `frontend/src/app/app.routes.ts`

**Intent**: Gracz wybiera jedną z 2 frakcji i rozpoczyna partię.

**Contract**: Trasa `new-match` z `authGuard` (przed `**`). Po wejściu wczytuje frakcje i pokazuje je jako wybór jednokrotny (nazwa + opis). „Rozpocznij partię” nieaktywny bez wyboru i w trakcie żądania; sukces → `navigateByUrl('/match')`. Błąd wczytania frakcji → „Nie udało się wczytać frakcji — odśwież stronę.”; błąd startu (nie-401) → „Nie udało się rozpocząć partii — spróbuj ponownie.” Link powrotu do lobby.

#### 3. Link z lobby

**File**: `frontend/src/app/lobby/lobby.{ts,html,spec.ts}`

**Intent**: Wejście do nowego przepływu z istniejącego lobby.

**Contract**: Link „Nowa partia” (`routerLink="/new-match"`) obok powitania.

#### 4. Testy

**File**: `new-match.spec.ts`, `lobby.spec.ts`, `game.service` przez testy komponentów

**Intent**: Pokryć przepływ i stany błędów wzorem `lobby.spec.ts`.

**Contract**: frakcje się renderują; przycisk nieaktywny bez wyboru; wybór + klik → `POST /api/matches` z `{ factionId }` i nawigacja do `/match`; przycisk nieaktywny w trakcie żądania; 500 przy starcie → komunikat, brak nawigacji; 500 przy frakcjach → komunikat; lobby zawiera link do `/new-match`.

### Success Criteria:

#### Automated Verification:

- Testy frontu przechodzą: `cd frontend && npx ng test --watch=false`
- Build produkcyjny przechodzi: `cd frontend && npm run build`

#### Manual Verification:

- Lokalnie: lobby → „Nowa partia” → widać Developerów i Testerów, start przenosi na `/match`

**Implementation Note**: After completing this phase and all automated verification passes, pause here for manual confirmation from the human that the manual testing was successful before proceeding to the next phase.

---

## Phase 4: Stół partii

### Overview

Chroniona trasa `/match` wczytuje bieżącą partię i renderuje stół z perspektywy gracza.

### Changes Required:

#### 1. Widok `/match`

**File**: `frontend/src/app/match-table/match-table.{ts,html,css,spec.ts}`, `frontend/src/app/app.routes.ts`

**Intent**: Pokazać stół zgodnie z FR-007 (stres obu stron zawsze widoczny) i odzyskać partię po odświeżeniu.

**Contract**: Trasa `match` z `authGuard`. Po wejściu `currentMatch()`; `404` → `navigateByUrl('/new-match')`; inny błąd (nie-401) → „Nie udało się wczytać partii — spróbuj ponownie.” z linkiem do lobby. Układ: strefa przeciwnika (frakcja, stres `X/30`, zasób `c/m`, kart na ręce, kart w talii), pasek tury („Tura N — Twoja tura” / „Tura N — tura przeciwnika”), strefa gracza (frakcja, stres `X/30`, zasób `c/m`, kart w talii, ręka: nazwa, typ „Postać”/„Wydarzenie”, koszt, tekst, oznaczenie „Neutralna” dla `neutral: true`). Link do lobby.

#### 2. Testy

**File**: `match-table.spec.ts`

**Intent**: Pokryć renderowanie i ścieżki wczytania.

**Contract**: renderuje 5 kart gracza z nazwą/kosztem/typem; pokazuje stres `0/30` obu stron, liczby kart przeciwnika i zasób; etykieta „Neutralna” tylko dla kart neutralnych; tekst tury dla `PLAYER` i `OPPONENT`; 404 → nawigacja do `/new-match`; 500 → komunikat.

### Success Criteria:

#### Automated Verification:

- Testy frontu przechodzą: `cd frontend && npx ng test --watch=false`
- Build produkcyjny przechodzi: `cd frontend && npm run build`
- Testy backendu dalej zielone: `./mvnw -B verify`

#### Manual Verification:

- Pełny przepływ lokalnie: logowanie → lobby → `/new-match` → wybór frakcji → stół z 5 kartami, 25/25 w taliach, stresem 0/30 obu stron, zasobem i turą
- Odświeżenie `/match` pokazuje tę samą rękę; nowy start z `/new-match` daje inną partię
- Wejście na `/match` po restarcie backendu przenosi na `/new-match`
- Stół czytelny w Chrome i Firefox na szerokości desktopowej

**Implementation Note**: After completing this phase and all automated verification passes, pause here for manual confirmation from the human that the manual testing was successful before proceeding to the next phase.

---

## Testing Strategy

### Unit Tests:

- Walidacja katalogu: każdy rodzaj błędnych danych (duplikaty, koszt poza 1–10, talia ≠ 30, obca karta w talii, `copies` > 2, talia dla frakcji neutralnej, ≠ 2 grywalne frakcje).
- Fabryka partii: obie gałęzie zaczynającego, rozmiary ręki/talii, druga frakcja dla komputera, determinizm przy tym samym seedzie.

### Integration Tests:

- MockMvc dla trzech endpointów: statusy, kształt `MatchView`, brak ręki komputera w odpowiedzi, jedna partia na gracza, izolacja graczy, 401/403.
- Komponenty Angulara z `HttpTestingController`: przepływ `/new-match` i `/match`, stany błędów.

### Manual Testing Steps:

1. Zaloguj się lokalnie, przejdź z lobby do „Nowa partia”, wybierz Developerów, rozpocznij.
2. Sprawdź stół: 5 kart, 25/25, stres 0/30 obu stron, zasób 1/1 po stronie zaczynającej i 0/0 po drugiej, tekst tury.
3. Odśwież `/match` — ta sama ręka. Wróć i rozpocznij jako Testerzy — nowa partia, komputer gra Developerami.
4. Zrestartuj backend i odśwież `/match` — przeniesienie na `/new-match`.

## Performance Considerations

Jedna partia na gracza w pamięci (~60 obiektów kart); przy skali `small` z PRD bez limitu i bez wygaszania. Wpisy żyją do restartu procesu.

## Migration Notes

Brak danych do migracji. Wdrożenie (F-02) restartuje kontener i kasuje trwające partie — akceptowane (PRD: v1 bez zapisu postępu).

## References

- Roadmapa: `context/foundation/roadmap.md` — S-02 (match-setup-table), Open Roadmap Question 3, §Parked (frakcja neutralna)
- PRD: `context/foundation/prd.md` — FR-002, FR-003, FR-007, US-01, §Business Logic
- Lessons: `context/foundation/lessons.md` — endpointy pod `/api/**`
- Wzorzec endpointu i testów: `src/main/java/com/example/itcardbrawl/api/MeController.java:14-31`, `src/test/java/com/example/itcardbrawl/api/MeControllerTests.java:21-37`
- Wzorzec serwisu/komponentu: `frontend/src/app/auth.service.ts`, `frontend/src/app/lobby/lobby.ts`, `frontend/src/app/lobby/lobby.spec.ts`
- Poprzednia zmiana: `context/archive/2026-11-18-external-login-gate/plan.md`

## Progress

> Convention: `- [ ]` pending, `- [x]` done. Append ` — <commit sha>` when a step lands. Do not rename step titles. See `references/progress-format.md`.

### Phase 1: Katalog kart i model startu partii

#### Automated

- [x] 1.1 Testy backendu przechodzą: `./mvnw -B verify` — 4b1f338
- [x] 1.2 `CardCatalogTests` pokrywa każdy przypadek walidacji katalogu i wczytanie prawdziwego `factions.json` — 4b1f338
- [x] 1.3 `MatchFactoryTests` pokrywa obie gałęzie zaczynającego i odrzucenie frakcji neutralnej — 4b1f338

#### Manual

- [x] 1.4 Przegląd placeholderów w `factions.json`: nazwy kart czytelne dla ludzi z IT, obie frakcje mają postacie i wydarzenia w różnych kosztach — 4b1f338

### Phase 2: API partii

#### Automated

- [x] 2.1 Testy backendu przechodzą: `./mvnw -B verify` — 365780e
- [x] 2.2 `MatchControllerTests` pokrywa 201/400/404/401/403, zastępowanie partii i izolację graczy — 365780e
- [x] 2.3 `BackendRoutesTests` dalej zielone (nowe endpointy tylko pod `/api`) — 365780e

#### Manual

- [x] 2.4 Lokalnie po zalogowaniu `GET /api/factions` i `GET /api/matches/current` (404) w przeglądarce zwracają oczekiwany JSON — 365780e

### Phase 3: Ekran wyboru frakcji

#### Automated

- [x] 3.1 Testy frontu przechodzą: `cd frontend && npx ng test --watch=false` — e5b09b5
- [x] 3.2 Build produkcyjny przechodzi: `cd frontend && npm run build` — e5b09b5

#### Manual

- [x] 3.3 Lokalnie: lobby → „Nowa partia” → widać Developerów i Testerów, start przenosi na `/match` — e5b09b5

### Phase 4: Stół partii

#### Automated

- [x] 4.1 Testy frontu przechodzą: `cd frontend && npx ng test --watch=false`
- [x] 4.2 Build produkcyjny przechodzi: `cd frontend && npm run build`
- [x] 4.3 Testy backendu dalej zielone: `./mvnw -B verify`

#### Manual

- [x] 4.4 Pełny przepływ lokalnie: logowanie → lobby → `/new-match` → wybór frakcji → stół z 5 kartami, 25/25 w taliach, stresem 0/30 obu stron, zasobem i turą
- [x] 4.5 Odświeżenie `/match` pokazuje tę samą rękę; nowy start z `/new-match` daje inną partię
- [x] 4.6 Wejście na `/match` po restarcie backendu przenosi na `/new-match`
- [x] 4.7 Stół czytelny w Chrome i Firefox na szerokości desktopowej
