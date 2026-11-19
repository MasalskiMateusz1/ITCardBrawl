# Interfejs i treść gry po angielsku i po polsku (ui-in-english-and-polish) Implementation Plan

## Overview

Gracz przełącza język aplikacji między polskim a angielskim w trakcie działania (bez przeładowania i bez osobnych buildów). Przełączenie obejmuje cały interfejs SPA oraz treść gry serwowaną przez API: nazwy i opisy frakcji oraz nazwy i teksty kart. Pierwsza wizyta dostaje język przeglądarki, wybór jest pamiętany w przeglądarce, a przełącznik jest widoczny na każdym ekranie, także na logowaniu.

## Current State Analysis

Na podstawie `context/changes/ui-in-english-and-polish/research.md`:

- Brak jakiejkolwiek i18n: we froncie brak `@angular/localize`/Transloco/ngx-translate, `LOCALE_ID` i bloku `i18n` w `angular.json`; w backendzie brak `LocaleResolver`, `MessageSource`, `messages*.properties` i ustawień `spring.web.locale`.
- Interfejs: ok. 36 polskich tekstów zaszytych w szablonach i TS pięciu komponentów (sign-in, lobby, new-match, match-table, app shell) + `<html lang="pl">` (`frontend/src/index.html:2`). Komunikaty błędów trzymane jako gotowe napisy w signalach (`new-match.ts:29,54`, `lobby.ts:32`, `match-table.ts:34`); `sign-in.ts:14-26` już wylicza komunikat z kluczy query param.
- Treść gry: jednojęzyczne `name`/`description` frakcji i `name`/`text` kart w `src/main/resources/game/factions.json:2-55`; rekordy `Faction(id, name, description, playable)` (`game/Faction.java:6`) i `Card(id, faction, name, type, cost, text)` (`game/Card.java:6`). Walidacja wymaga niepustej nazwy frakcji i karty (`CardCatalog.java:91-92,114-115`); opisu i tekstu nie sprawdza.
- Partia trzyma migawkę obiektów katalogu (`MatchSide.java:8` → `Faction`, `CardInstance.java:6` → `Card`); `MatchView` czyta `name`/`text` prosto z nich (`MatchView.java:37-38,62-65`). `GET /api/factions` mapuje `Faction` na `FactionSummary(id, name, description)` (`MatchController.java:38-43,71`).
- Nigdzie nie ma miejsca na preferencję: brak bazy danych, brak pola w `MeResponse` (`MeController.java:48`), brak `localStorage` we froncie.
- Błędy backendu nie docierają do UI (front pokazuje własne komunikaty dla nie-401) — polskie komunikaty wewnętrzne (walidacja katalogu, wyjątki) mogą zostać po polsku.
- `HttpClient` z tablicą funkcyjnych interceptorów (`frontend/src/app/app.config.ts:10`); `app.html` to `<h1>ItCardBrawl</h1>` + `<router-outlet />`.
- Testy: 23 asercje/wyszukiwania po polskim tekście w 4 specach frontu; backend asertuje polskie nazwy w `MatchControllerTests.java:99` i `CardCatalogTests.java:67`, a fixtury budują `Faction`/`Card` konstruktorami.
- `context/foundation/lessons.md`: każdy endpoint pod `/api/**` — ten plan nie dodaje endpointów.

## Desired End State

- Pierwsza wizyta: język z `navigator.language` (`pl*` → polski, wszystko inne → angielski). Wybór zapisany w `localStorage` ma pierwszeństwo przy kolejnych wizytach i przeżywa przekierowanie logowania OAuth.
- W nagłówku aplikacji (obok „ItCardBrawl”) na każdym ekranie przełącznik PL | EN z zaznaczonym bieżącym językiem. Kliknięcie natychmiast zmienia wszystkie teksty interfejsu, `<html lang>` i — na `/new-match` i `/match` — treść z serwera (frakcje, karty), bez przeładowania strony i bez utraty wybranej frakcji czy partii.
- Każde żądanie `/api/**` niesie `Accept-Language: pl|en`; API zwraca nazwy, opisy i teksty w tym języku (domyślnie i dla nieobsługiwanych języków: polski).
- `factions.json` trzyma każde pole tekstowe jako `{ "pl": …, "en": … }`; aplikacja nie wystartuje, jeśli któremukolwiek polu brakuje któregoś języka.
- Roadmapa S-06 odnotowuje, że treść kart musi powstać w obu językach.

Weryfikacja: testy backendu i frontu zielone, build produkcyjny przechodzi, ręczne przejście przepływu w obu językach.

### Key Discoveries:

- Migawka obiektów w partii (`CardInstance.java:6`, `MatchSide.java:8`) przestaje być problemem, gdy `Card`/`Faction` niosą oba języki — `MatchView` wybiera język przy renderowaniu, więc przełączenie w trakcie partii działa bez ponownego wyszukiwania po id.
- Spring MVC rozwiązuje `java.util.Locale` jako argument metody kontrolera przez bean `localeResolver`; `AcceptHeaderLocaleResolver` z listą obsługiwanych języków i domyślnym `pl` wybiera najlepsze dopasowanie z `Accept-Language` (np. `de, en;q=0.9` → `en`).
- jsdom w testach Vitest zwraca `navigator.language = "en-US"` — bez jawnego ustawienia języka istniejące specy dostałyby angielski.
- Zoneless + signals: odczyt signala w szablonie wystarcza do ponownego renderu po zmianie języka; czysty pipe z niezmienionym argumentem by się nie przeliczył.

## What We're NOT Doing

- Zapisywania preferencji języka po stronie serwera/konta (brak bazy; wybór jest per przeglądarka).
- Tłumaczenia wewnętrznych komunikatów backendu (walidacja katalogu, wyjątki, powód 400) — nie trafiają do UI.
- Bibliotek i18n (Transloco, ngx-translate) ani `@angular/localize`; pluralizacji/ICU (obecne teksty jej nie potrzebują).
- Innych języków niż polski i angielski; tłumaczenia nazwy marki „ItCardBrawl” i `<title>`.
- Docelowej treści kart i balansu — angielskie teksty to placeholdery agenta, podmieniane w S-06 razem z polskimi.
- Aktualizacji PRD (zostaje dla `/10x-roadmap`/przeglądu PRD); tylko notatka w S-06 roadmapy.
- Nowych endpointów, zmian tras ani kształtu URL-i.

## Implementation Approach

Język to wartość żądania, nie stan serwera. SPA trzyma bieżący język w signalu (`LanguageService`), utrwala go w `localStorage` i dokleja `Accept-Language` do każdego żądania `/api`. Backend zamienia nagłówek na `Locale` (bean `localeResolver`: obsługiwane `pl`, `en`, domyślnie `pl`), kontrolery mapują go na enum `Language`, a widoki API wybierają tekst z dwujęzycznych pól katalogu. Katalog pozostaje jednym plikiem i jednym źródłem id, kosztów i talii; walidacja fail-fast wymaga kompletu tłumaczeń.

Interfejs korzysta z dwóch typowanych słowników (`pl`, `en`) implementujących wspólny interfejs `Messages`, więc kompilator pilnuje, by każdy tekst istniał w obu językach. Komponenty czytają `texts()` (signal wyliczany z języka) w szablonach; teksty z parametrem są funkcjami w słowniku; komunikaty błędów trzymane jako klucze, żeby po przełączeniu wyświetlały się w nowym języku. Ekrany z treścią z serwera ponownie pobierają dane po zmianie języka.

Kolejność: najpierw backend (API dwujęzyczne, front dalej działa — dostaje polski domyślnie), potem warstwa tłumaczeń i przełącznik, na końcu przepięcie ekranów.

## Critical Implementation Details

- **Kolejność w testach frontu**: `LanguageService` (root) czyta `localStorage` przy tworzeniu, a jsdom ma `navigator.language = "en-US"`. Specy, które asertują polskie teksty, muszą ustawić polski przed pierwszym `TestBed.inject`/`createComponent` (np. zapis klucza w `localStorage` w `beforeEach` i czyszczenie w `afterEach`), inaczej dostaną angielski.
- **Bean `localeResolver`**: Spring MVC szuka beana o dokładnie tej nazwie; bean o innej nazwie zostanie zignorowany i nagłówek nie zmieni języka.
- **Ponowne pobranie po zmianie języka**: efekt reagujący na język odpala się także przy pierwszym uruchomieniu — trzeba uniknąć podwójnego żądania przy wejściu na ekran (jedno żądanie na wejście, jedno na każdą zmianę języka); `httpTesting.verify()` w specach to wychwyci.

## Phase 1: Dwujęzyczny katalog i API zależne od języka

### Overview

Katalog kart trzyma każde pole tekstowe w obu językach, walidacja wymaga kompletu, a `/api/factions` i widok partii zwracają tekst w języku z `Accept-Language`. Front działa bez zmian (dostaje polski domyślnie).

### Changes Required:

#### 1. Model języka i tekstu dwujęzycznego

**File**: `src/main/java/com/example/itcardbrawl/game/Language.java`, `src/main/java/com/example/itcardbrawl/game/LocalizedText.java`

**Intent**: Jedno miejsce na obsługiwane języki i wybór tekstu, używane przez katalog i widoki API.

**Contract**: `enum Language { PL, EN }` z `static Language from(Locale)` (`en` → `EN`, wszystko inne → `PL`) i `Locale locale()`. `record LocalizedText(String pl, String en)` z `String in(Language)`; deserializowany przez Jacksona z `{ "pl": …, "en": … }`.

#### 2. Rekordy katalogu i dane

**File**: `src/main/java/com/example/itcardbrawl/game/Faction.java`, `Card.java`, `src/main/resources/game/factions.json`

**Intent**: Pola tekstowe frakcji i kart stają się dwujęzyczne; obecne polskie teksty zostają jako `pl`, agent dopisuje angielskie placeholdery z tym samym humorem IT. Nazwy będące angielskim żargonem (np. „Junior Developer”, „git push --force”) mogą mieć identyczną wartość w obu językach.

**Contract**: `Faction(String id, LocalizedText name, LocalizedText description, boolean playable)`, `Card(String id, String faction, LocalizedText name, CardType type, int cost, LocalizedText text)`. W `factions.json` każde `name`, `description` (frakcje) i `name`, `text` (karty) ma postać `{ "pl": "…", "en": "…" }`; id, koszty, typy i talie bez zmian.

#### 3. Walidacja kompletu tłumaczeń

**File**: `src/main/java/com/example/itcardbrawl/game/CardCatalog.java`

**Intent**: Fail-fast przy brakującym tłumaczeniu — taka sama zasada jak dla reszty katalogu, z czytelnym komunikatem w zbiorczym `IllegalStateException`.

**Contract**: Dla każdej frakcji `name` i `description`, dla każdej karty `name` i `text`: wartość obecna i niepusta w każdym `Language` (dotychczasowe sprawdzenie samej nazwy zastąpione tym). Komunikat wskazuje id, pole i język, np. `karta 'dev-junior': brak tekstu 'text' w języku EN`. Komunikaty walidacji zostają po polsku.

#### 4. Rozwiązywanie języka żądania

**File**: `src/main/java/com/example/itcardbrawl/config/LocaleConfig.java` (nowy) — lub istniejąca klasa konfiguracji, jeśli pasuje lepiej

**Intent**: `Accept-Language` → `Locale` w kontrolerach, z polskim jako domyślnym i dla nieobsługiwanych języków.

**Contract**: Bean o nazwie `localeResolver`: `AcceptHeaderLocaleResolver` z `supportedLocales = [pl, en]` i `defaultLocale = pl`.

#### 5. Widoki API zależne od języka

**File**: `src/main/java/com/example/itcardbrawl/api/MatchController.java`, `src/main/java/com/example/itcardbrawl/api/MatchView.java`

**Intent**: Wszystkie trzy endpointy treści zwracają tekst w języku żądania; kształt JSON bez zmian (pola dalej są zwykłymi napisami).

**Contract**: Metody `factions`, `start`, `current` przyjmują `Locale` i mapują przez `Language.from`. `FactionSummary(id, name, description)` i `MatchView.of(Match, CardCatalog, Language)` wybierają `LocalizedText.in(language)` dla nazw frakcji, nazw i tekstów kart. Przełączenie języka dla tej samej partii zmienia teksty, nie `matchId` ani `instanceId`.

#### 6. Testy

**File**: `src/test/java/com/example/itcardbrawl/game/CardCatalogTests.java`, `MatchFactoryTests.java` (o ile dotyka nazw), `src/test/java/com/example/itcardbrawl/api/MatchControllerTests.java`, nowy `src/test/java/com/example/itcardbrawl/game/LanguageTests.java` (lub w istniejącym pliku)

**Intent**: Pokryć nowe reguły walidacji, mapowanie języka i treść API w obu językach.

**Contract**: Fixtury `CardCatalogTests` na `LocalizedText`; prawdziwy katalog ładuje się i ma `Developerzy/Testerzy` po polsku oraz niepuste angielskie nazwy; po jednym teście dla braku/pustej wartości `pl` i `en` w każdym z czterech pól (komunikat wskazuje pole i język). `Language.from`: `en`, `en-GB` → `EN`; `pl`, `de`, `null`-bezpieczny domyślny → `PL`. API: bez nagłówka → polskie nazwy (dotychczasowa asercja `Developerzy` zostaje); `Accept-Language: en` → angielska nazwa frakcji na `/api/factions`, w `player.faction.name` i w nazwie/tekście kart ręki; `Accept-Language: de, en;q=0.9` → angielski; ta sama partia pobrana po `pl` i po `en` ma ten sam `matchId` i `instanceId`, różne teksty.

#### 7. Notatka w roadmapie

**File**: `context/foundation/roadmap.md`

**Intent**: S-06 nie może przeoczyć, że treść kart powstaje teraz w dwóch językach.

**Contract**: W bloku `### S-06` dopisz do `Unknowns` (lub doprecyzuj istniejący punkt o treści kart), że nazwy, opisy i teksty kart i frakcji muszą powstać po polsku i po angielsku (walidacja katalogu wymaga obu). Pozostałe pola i statusy bez zmian.

### Success Criteria:

#### Automated Verification:

- Testy backendu przechodzą: `./mvnw -B verify`
- `CardCatalogTests` pokrywa brak/pustą wartość `pl` i `en` dla nazwy i opisu frakcji oraz nazwy i tekstu karty
- `MatchControllerTests` pokrywa domyślny polski, `Accept-Language: en`, dopasowanie `de, en;q=0.9` → angielski i tę samą partię w obu językach

#### Manual Verification:

- Przegląd angielskich placeholderów w `factions.json`: czytelne dla ludzi z IT, z zachowanym humorem
- Lokalnie po zalogowaniu `GET /api/factions` w przeglądarce zwraca polskie nazwy; z nagłówkiem `Accept-Language: en` (np. DevTools / curl z ciasteczkiem sesji) — angielskie

**Implementation Note**: After completing this phase and all automated verification passes, pause here for manual confirmation from the human that the manual testing was successful before proceeding to the next phase.

---

## Phase 2: Warstwa tłumaczeń i przełącznik języka

### Overview

Serwis języka, słowniki PL/EN, interceptor `Accept-Language` i przełącznik PL | EN w nagłówku aplikacji. Ekrany jeszcze używają zaszytych tekstów (Faza 3), ale nagłówek i API już reagują na język.

### Changes Required:

#### 1. Serwis języka

**File**: `frontend/src/app/i18n/language.service.ts`

**Intent**: Jedno źródło bieżącego języka dla całej aplikacji, z wyborem domyślnym z przeglądarki, pamięcią w `localStorage` i synchronizacją `<html lang>`.

**Contract**: `type Language = 'pl' | 'en'`; `LANGUAGE_STORAGE_KEY = 'itcardbrawl.language'`. `LanguageService` (`providedIn: 'root'`): `language: Signal<Language>`, `texts: Signal<Messages>` (słownik dla bieżącego języka), `setLanguage(lang)` (zapis do `localStorage` + aktualizacja signala). Start: poprawna wartość z `localStorage` → ona; inaczej `navigator.language` zaczynające się od `pl` → `pl`, w przeciwnym razie `en`. Za każdym razem `document.documentElement.lang` = bieżący język. Niedostępny lub rzucający `localStorage` nie psuje aplikacji (język działa w pamięci).

#### 2. Słowniki

**File**: `frontend/src/app/i18n/messages.ts`, `frontend/src/app/i18n/pl.ts`, `frontend/src/app/i18n/en.ts`

**Intent**: Wszystkie teksty interfejsu w dwóch typowanych słownikach — brak tekstu w którymkolwiek języku to błąd kompilacji.

**Contract**: `interface Messages` pogrupowany per ekran (`shell`, `signIn`, `lobby`, `newMatch`, `matchTable`, `cardType`), obejmujący każdy tekst z inwentarza w `research.md` (Detailed Findings → Frontend UI strings). Teksty z parametrem to funkcje, np. `welcome: (name: string) => string`, `turn: (n: number, mine: boolean) => string`, `cost: (n: number) => string`. `pl: Messages` zawiera obecne polskie teksty dosłownie (te same znaki, w tym „—”), `en: Messages` ich angielskie odpowiedniki. Etykiety przełącznika: `PL`, `EN` oraz dostępna nazwa grupy (np. „Język” / „Language”).

#### 3. Interceptor języka

**File**: `frontend/src/app/i18n/language.interceptor.ts`, `frontend/src/app/app.config.ts`

**Intent**: Każde żądanie do API niesie bieżący język, bez zmian w wywołaniach serwisów.

**Contract**: Funkcyjny interceptor dodaje `Accept-Language: <language>` do żądań, których URL zaczyna się od `/api`; inne żądania bez zmian. Rejestrowany w `withInterceptors([...])` obok `authInterceptor`.

#### 4. Przełącznik w nagłówku aplikacji

**File**: `frontend/src/app/i18n/language-switch.{ts,html,css}` (komponent), `frontend/src/app/app.{ts,html,css}`

**Intent**: Przełącznik widoczny na każdym ekranie, także przed zalogowaniem.

**Contract**: Komponent z dwoma przyciskami `PL` i `EN` w grupie z dostępną nazwą (`role="group"` + `aria-label` ze słownika); bieżący język oznaczony `aria-pressed="true"`; klik → `setLanguage`. `app.html`: nagłówek z „ItCardBrawl” i przełącznikiem, pod nim `<router-outlet />`.

#### 5. Testy

**File**: `frontend/src/app/i18n/language.service.spec.ts`, `language.interceptor.spec.ts`, `language-switch.spec.ts`, `frontend/src/app/app.spec.ts`

**Intent**: Pokryć wybór domyślny, pamięć, `<html lang>`, nagłówek HTTP i przełącznik.

**Contract**: Serwis: brak zapisu + `navigator.language` `pl-PL` → `pl`, `en-US`/`de` → `en`; zapis w `localStorage` wygrywa z przeglądarką; nieznana wartość w `localStorage` ignorowana; `setLanguage` zapisuje i zmienia `texts()` oraz `document.documentElement.lang`. Interceptor: żądanie `/api/...` ma `Accept-Language` zgodny z bieżącym językiem i zmienia się po `setLanguage`; żądanie spoza `/api` go nie ma. Przełącznik: dwa przyciski, `aria-pressed` na bieżącym, klik `EN` zmienia język. `app.spec.ts`: nagłówek zawiera „ItCardBrawl” i przełącznik.

### Success Criteria:

#### Automated Verification:

- Testy frontu przechodzą: `cd frontend && npx ng test --watch=false`
- Build produkcyjny przechodzi: `cd frontend && npm run build`

#### Manual Verification:

- Lokalnie: przełącznik PL | EN widoczny na `/sign-in` i w lobby; klik zmienia `<html lang>` i zaznaczenie, a wybór przeżywa odświeżenie strony i logowanie przez GitHub/Google
- W DevTools żądania `/api/*` mają `Accept-Language` zgodny z wybranym językiem

**Implementation Note**: After completing this phase and all automated verification passes, pause here for manual confirmation from the human that the manual testing was successful before proceeding to the next phase.

---

## Phase 3: Tłumaczenie ekranów

### Overview

Wszystkie ekrany biorą teksty ze słownika, komunikaty błędów przechowują klucze, a ekrany z treścią z serwera pobierają ją ponownie po zmianie języka.

### Changes Required:

#### 1. Logowanie i lobby

**File**: `frontend/src/app/sign-in/sign-in.{ts,html}`, `frontend/src/app/lobby/lobby.{ts,html}`

**Intent**: Teksty z `texts()`; komunikaty pozostają reaktywne po zmianie języka.

**Contract**: `sign-in`: przyciski i trzy komunikaty z `texts().signIn` (wyliczane z kluczy query param jak dziś). `lobby`: powitanie przez funkcję `welcome(displayName)`, „Nowa partia”, „Wyloguj”; błąd wylogowania przechowywany jako flaga/klucz, tekst z słownika przy renderowaniu.

#### 2. Wybór frakcji

**File**: `frontend/src/app/new-match/new-match.{ts,html}`

**Intent**: Teksty ze słownika; lista frakcji (nazwy i opisy z API) w bieżącym języku.

**Contract**: Nagłówek, legenda, przycisk, link i dwa komunikaty błędów ze słownika; błąd jako klucz (`'loadFactions' | 'startMatch' | null`). Po zmianie języka ponowne `GET /api/factions`; zaznaczona frakcja (id) zostaje. Jedno żądanie przy wejściu, jedno na każdą zmianę języka.

#### 3. Stół partii

**File**: `frontend/src/app/match-table/match-table.{ts,html}`

**Intent**: Wszystkie etykiety stołu ze słownika; karty i frakcje (z API) w bieżącym języku.

**Contract**: Etykiety stref, `aria-label`, „Stres”, „Zasób”, liczniki, tekst tury przez funkcję słownika, „Twoja ręka”, koszt, „Neutralna”, link do lobby — ze słownika; typ karty z `texts().cardType[type]` (zastępuje `CARD_TYPE_LABELS`). Błąd jako klucz. Po zmianie języka ponowne `GET /api/matches/current`; 404 dalej przenosi na `/new-match`. Jedno żądanie przy wejściu, jedno na każdą zmianę języka.

#### 4. Testy

**File**: `sign-in.spec.ts`, `lobby.spec.ts`, `new-match.spec.ts`, `match-table.spec.ts`

**Intent**: Istniejące asercje dalej sprawdzają polskie teksty; nowe testy dowodzą angielskiego i przełączenia na żywo.

**Contract**: Każdy z czterech speców ustawia polski przed utworzeniem serwisu/komponentu (patrz Critical Implementation Details) i sprząta `localStorage` po teście — dotychczasowe polskie asercje bez zmian treści. Na ekran co najmniej: render po angielsku (np. „Sign in with GitHub”, „New match”, „Start match”, „Turn 1 — your turn”) oraz przełączenie języka po renderze zmienia tekst bez ponownego tworzenia komponentu. `new-match` i `match-table`: zmiana języka wysyła dokładnie jedno ponowne żądanie (frakcje / bieżąca partia) i renderuje nowe nazwy; wybrana frakcja zostaje zaznaczona; komunikat błędu pokazany po polsku zmienia się na angielski po przełączeniu.

### Success Criteria:

#### Automated Verification:

- Testy frontu przechodzą: `cd frontend && npx ng test --watch=false`
- Build produkcyjny przechodzi: `cd frontend && npm run build`
- Testy backendu dalej zielone: `./mvnw -B verify`

#### Manual Verification:

- Pełny przepływ po angielsku: logowanie → lobby → `/new-match` → start → stół; wszystkie teksty interfejsu, frakcje i karty po angielsku
- Przełączenie na `/match` w trakcie partii zmienia etykiety i teksty kart bez zmiany ręki; przełączenie na `/new-match` zachowuje wybraną frakcję
- Pierwsza wizyta w czystym profilu przeglądarki z językiem polskim → polski, z angielskim → angielski
- Brak polskich tekstów interfejsu po wybraniu EN (poza nazwą marki i treścią kart celowo identyczną w obu językach)

**Implementation Note**: After completing this phase and all automated verification passes, pause here for manual confirmation from the human that the manual testing was successful before proceeding to the next phase.

---

## Testing Strategy

### Unit Tests:

- Backend: walidacja kompletu tłumaczeń (każde z czterech pól × każdy język, brak i pusta wartość), `Language.from` dla `en`, `en-GB`, `pl`, `de`.
- Frontend: `LanguageService` (domyślny z przeglądarki, pamięć, nieznana wartość, `<html lang>`), interceptor (tylko `/api`, zmiana po przełączeniu), przełącznik (`aria-pressed`, klik).

### Integration Tests:

- MockMvc: domyślny polski, `Accept-Language: en`, dopasowanie wielu języków, ta sama partia w dwóch językach (stałe `matchId`/`instanceId`).
- Komponenty z `HttpTestingController`: teksty w obu językach, przełączenie na żywo, dokładnie jedno ponowne żądanie treści po przełączeniu, błędy jako klucze.

### Manual Testing Steps:

1. Czysty profil przeglądarki z językiem angielskim: `/sign-in` po angielsku; przełącz na PL — wszystko po polsku.
2. Zaloguj się (GitHub/Google) — język po powrocie z OAuth taki jak przed logowaniem.
3. `/new-match`: wybierz frakcję, przełącz język — nazwy/opisy zmieniają się, zaznaczenie zostaje; rozpocznij partię.
4. `/match`: przełącz język — etykiety, typy i teksty kart zmieniają się, ręka ta sama; odśwież — język zachowany.

## Performance Considerations

Jedno dodatkowe `GET` treści na każde przełączenie języka na `/new-match` i `/match`; słowniki UI są w bundlu (ok. 2 × 40 tekstów). Katalog w pamięci podwaja liczbę napisów (~60 → ~120) — pomijalne.

## Migration Notes

Brak danych do migracji (partie w pamięci). Wdrożenie restartuje serwer, więc trwające partie i tak znikają; nowe partie powstają z dwujęzycznego katalogu. Użytkownicy z polską przeglądarką nie widzą zmiany domyślnego języka.

## References

- Research: `context/changes/ui-in-english-and-polish/research.md`
- Lessons: `context/foundation/lessons.md` — endpointy pod `/api/**`
- Model i widok partii: `src/main/java/com/example/itcardbrawl/api/MatchView.java:20-66`, `src/main/java/com/example/itcardbrawl/api/MatchController.java:38-71`
- Walidacja katalogu: `src/main/java/com/example/itcardbrawl/game/CardCatalog.java:84-190`
- Wzorce frontu: `frontend/src/app/auth.service.ts`, `frontend/src/app/auth.interceptor.ts`, `frontend/src/app/app.config.ts:10`, `frontend/src/app/sign-in/sign-in.ts:14-26`
- Poprzednia zmiana: `context/archive/2026-11-19-match-setup-table/plan.md`
- Roadmapa S-06: `context/foundation/roadmap.md` (blok `### S-06`)

## Progress

> Convention: `- [ ]` pending, `- [x]` done. Append ` — <commit sha>` when a step lands. Do not rename step titles. See `references/progress-format.md`.

### Phase 1: Dwujęzyczny katalog i API zależne od języka

#### Automated

- [x] 1.1 Testy backendu przechodzą: `./mvnw -B verify` — 4c8a55a
- [x] 1.2 `CardCatalogTests` pokrywa brak/pustą wartość `pl` i `en` dla nazwy i opisu frakcji oraz nazwy i tekstu karty — 4c8a55a
- [x] 1.3 `MatchControllerTests` pokrywa domyślny polski, `Accept-Language: en`, dopasowanie `de, en;q=0.9` → angielski i tę samą partię w obu językach — 4c8a55a

#### Manual

- [x] 1.4 Przegląd angielskich placeholderów w `factions.json`: czytelne dla ludzi z IT, z zachowanym humorem — 4c8a55a
- [x] 1.5 Lokalnie po zalogowaniu `GET /api/factions` w przeglądarce zwraca polskie nazwy; z nagłówkiem `Accept-Language: en` (np. DevTools / curl z ciasteczkiem sesji) — angielskie — 4c8a55a

### Phase 2: Warstwa tłumaczeń i przełącznik języka

#### Automated

- [x] 2.1 Testy frontu przechodzą: `cd frontend && npx ng test --watch=false` — abae3b6
- [x] 2.2 Build produkcyjny przechodzi: `cd frontend && npm run build` — abae3b6

#### Manual

- [x] 2.3 Lokalnie: przełącznik PL | EN widoczny na `/sign-in` i w lobby; klik zmienia `<html lang>` i zaznaczenie, a wybór przeżywa odświeżenie strony i logowanie przez GitHub/Google — abae3b6
- [x] 2.4 W DevTools żądania `/api/*` mają `Accept-Language` zgodny z wybranym językiem — abae3b6

### Phase 3: Tłumaczenie ekranów

#### Automated

- [x] 3.1 Testy frontu przechodzą: `cd frontend && npx ng test --watch=false` — ddc1338
- [x] 3.2 Build produkcyjny przechodzi: `cd frontend && npm run build` — ddc1338
- [x] 3.3 Testy backendu dalej zielone: `./mvnw -B verify` — ddc1338

#### Manual

- [x] 3.4 Pełny przepływ po angielsku: logowanie → lobby → `/new-match` → start → stół; wszystkie teksty interfejsu, frakcje i karty po angielsku — ddc1338
- [x] 3.5 Przełączenie na `/match` w trakcie partii zmienia etykiety i teksty kart bez zmiany ręki; przełączenie na `/new-match` zachowuje wybraną frakcję — ddc1338
- [x] 3.6 Pierwsza wizyta w czystym profilu przeglądarki z językiem polskim → polski, z angielskim → angielski — ddc1338
- [x] 3.7 Brak polskich tekstów interfejsu po wybraniu EN (poza nazwą marki i treścią kart celowo identyczną w obu językach) — ddc1338
