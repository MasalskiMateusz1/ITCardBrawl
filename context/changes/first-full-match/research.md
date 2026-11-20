---
date: 2026-11-20T07:57:13+01:00
researcher: Claude (Opus 5.5) for Mateusz Masalski
git_commit: 648ec6f150a9e432e0b72d5b036eb94f881ae713
branch: main
repository: ITCardBrawl
topic: "S-03 first-full-match — co istnieje w kodzie, na czym zbudować pełną partię postaciami przeciw komputerowi i jakie decyzje blokują plan"
tags: [research, codebase, game, match, MatchStore, MatchView, match-table, i18n]
status: complete
last_updated: 2026-11-20
last_updated_by: Claude (Opus 5.5)
last_updated_note: "Dopisano decyzje użytkownika (Open Questions 1–7) oraz odpowiedzi A–D; otwarte zostają szczegóły walki, zdolności i pełnego stołu."
---

# Research: S-03 first-full-match

**Date**: 2026-11-20T07:57:13+01:00
**Researcher**: Claude (Opus 5.5) for Mateusz Masalski
**Git Commit**: 648ec6f150a9e432e0b72d5b036eb94f881ae713
**Branch**: main
**Repository**: ITCardBrawl

## Research Question

Roadmapa S-03 (`context/foundation/roadmap.md:135-146`): gracz zagrywa karty postaci na stół, komputer rozgrywa własne tury, gracz widzi każde zagranie komputera i jego skutek, stres rośnie, a partia kończy się przy stresie 30 albo pustej talii — gracz widzi wynik i może od razu zagrać ponownie (FR-004, FR-006, FR-008, US-01).

Pytania badawcze:
1. Jaki model partii, zasady i API zostawiło S-02 i czego brakuje do pętli gry?
2. Gdzie we froncie podpiąć zagrania, turę komputera, log ruchów i ekran wyniku?
3. Jakie wcześniejsze decyzje i follow-upy (F3) wiążą plan?
4. Które decyzje produktowe muszą zapaść przed `/10x-plan`?

## Summary

- **Model partii jest gotowym punktem startu, ale nie ma w nim jeszcze pętli gry.** `Match` ma `id`, `turnNumber`, `activeSide` i dwie strony (`Match.java:8`). `MatchSide` ma `faction`, `deck`, `hand`, `stress`, `resourceCurrent` i `resourceMax` (`MatchSide.java:8-9`). Brakuje strefy stołu, statusu partii, zwycięzcy, przyczyny końca i logu zagrań.
- **Karta nie ma żadnej wartości efektu.** `Card` to `id, faction, name, type, cost, text` (`Card.java:6`), więc nie wiadomo, o ile postać podnosi stres. Ta wartość, czyli zasady postaci na stole, to jedyny blokujący Unknown S-03 w roadmapie (`roadmap.md:144`, Owner: user, Block: yes).
- **Zasady tury już istnieją jako stałe, ale nikt ich nie wykonuje.** Dobieranie 1 karty na turę, z wyjątkiem pierwszej tury zaczynającego, jest opisane w `MatchRules.java:6-10` jako „reguła wykonywana od S-03”. Zasób rośnie o 1 na własną turę do 10. Na starcie zaczynający ma zasób 1/1, druga strona 0/0 (`MatchFactory.java:63`).
- **Połowa każdej talii to wydarzenia, a S-03 obejmuje tylko postacie.** Każda talia ma 15 postaci i 15 wydarzeń (wyliczone z `factions.json:23-54, 57-99`, szczegóły niżej). Wydarzenia mają swój slice, S-04 (`roadmap.md:148-159`). Jeśli w S-03 nie da się ich zagrać, zalegają na ręce. To trzeba rozstrzygnąć w planie.
- **`MatchStore` wymaga atomowej aktualizacji (F3).** Ma tylko `save` i `findCurrent` (`MatchStore.java:19-25`). Follow-up F3 nakazuje dodać `update(playerKey, UnaryOperator<Match>)` na `computeIfPresent` i przepuszczać przez nią każdy ruch, razem z testem współbieżności (`context/archive/2026-11-19-match-setup-table/follow-ups/review-fixes.md:5-9`).
- **API i bezpieczeństwo mają gotowy wzór dla nowych POST-ów.** Endpointy muszą leżeć pod `/api/**` (lessons.md). CSRF działa w trybie SPA (`SecurityConfig.java:35`). Testy pobierają cookie `XSRF-TOKEN` i wysyłają nagłówek `X-XSRF-TOKEN` (`MatchControllerTests.java:62-74`).
- **Frontend wyświetla stół tylko do odczytu.** `match-table` pobiera `GET /api/matches/current` przy wejściu i przy każdej zmianie języka (`match-table.ts:28-32`). Nie ma przycisków, metod POST, strefy stołu, logu ani bloku wyniku. Refetch po zmianie języka sprawia, że log zagrań komputera musi być częścią stanu serwera (`MatchView`), a nie tylko odpowiedzią POST-a.

## Detailed Findings

### Domena partii (backend `game/`)

- `Match(UUID id, int turnNumber, Side activeSide, MatchSide player, MatchSide opponent)` jest niemutowalnym rekordem z enumem `Side {PLAYER, OPPONENT}` (`src/main/java/com/example/itcardbrawl/game/Match.java:8-13`). Każdy ruch musi więc zwrócić nowy `Match`, co dobrze pasuje do `UnaryOperator<Match>` z F3.
- `MatchSide` kopiuje listy przez `List.copyOf` (`MatchSide.java:11-14`). `deck` jest uporządkowany od wierzchu (`MatchSide.java:6`).
- `CardInstance(int instanceId, Card card)` ma `instanceId` unikalny w obrębie partii (`CardInstance.java:3-6`). Ten numer może identyfikować kartę zagrywaną z ręki.
- `MatchRules` (`MatchRules.java:13-21`) definiuje `DECK_SIZE = 30`, `STARTING_HAND = 5`, `STRESS_LIMIT = 30`, `RESOURCE_CAP = 10` i `DRAW_PER_TURN = 1`. Javadoc (`MatchRules.java:6-10`) opisuje rosnący zasób i dobieranie „od S-03”.
- `MatchFactory.create`:
  - losuje zaczynającego przez `random.nextBoolean()` (`MatchFactory.java:42`);
  - najpierw tasuje, potem numeruje `instanceId` (`MatchFactory.java:50-56`), co już naprawia F1 z impl-review S-02;
  - rozdaje po 5 kart z wierzchu (`MatchFactory.java:60-65`).
  - Komputer dostaje „drugą grywalną frakcję” (`MatchFactory.java:34-37`).
- Losowość pochodzi z beana `RandomGenerator` opartego na `SecureRandom` (`GameConfig.java:22-25`). Testy domeny podstawiają własny `RandomGenerator` z ziarnem i ustalonym `nextBoolean` (`MatchFactoryTests.java:22-39`). Ten sam wzór pozwoli deterministycznie testować wybory komputera, jeśli będą losowe.
- **Brakuje** stołu (kart w grze), statusu partii (trwa / zakończona), zwycięzcy, przyczyny końca (limit stresu / pusta talia), logu zdarzeń i silnika ruchów: zagrania, końca tury, tury komputera. W sprawdzonym zakresie `game/` nie ma żadnej klasy wykonującej ruchy (pełna lista plików: 15 klas w `game/`).

### Katalog kart (`src/main/resources/game/factions.json`)

- Każda karta ma typ `CHARACTER` lub `EVENT` i koszt z zakresu 1–10 (walidacja: `CardCatalog.java:115-121`). Nie ma pola siły ani stresu.
- Postacie w talii `developers`, czyli 15 kart:
  - 5 postaci frakcyjnych po 2 kopie, koszty 1, 3, 5, 7, 10 (`factions.json:23,25,28,30,32` oraz `58-67`);
  - 5 postaci neutralnych po 1 kopii, koszty 2, 3, 5, 6, 10 (`factions.json:47,48,50,51,54` oraz `68-77`).
- Talia `testers` ma ten sam układ: postacie frakcyjne o kosztach 1, 3, 5, 7, 10 (`factions.json:34,36,39,41,43`) i te same neutralne.
- Pozostałe 15 kart każdej talii to wydarzenia.
- Teksty kart są humorystyczne i nie opisują żadnego efektu. Jeśli w S-03 postacie dostaną efekty liczbowe, tekst albo nowe pole musi je pokazać. PRD wymaga „widocznych skutków zagrań” (`context/foundation/prd.md`, §Business Logic).
- Nowe pola tekstowe kart muszą mieć wersję `pl` i `en`, bo `CardCatalog.requireAllLanguages` zatrzymuje start aplikacji (`CardCatalog.java:191-198`; AGENTS.md, Hard Rules). Nowe pole liczbowe, np. moc, wymaga dodania do rekordu `Card` i walidacji zakresu w `indexCards` (`CardCatalog.java:100-124`).

### Magazyn partii i współbieżność

- `MatchStore` trzyma partie w `ConcurrentHashMap<String, Match>`. Ma tylko `save` (put) i `findCurrent` (`MatchStore.java:16-25`). Klucz gracza to `"<registrationId>:<principalName>"` (`MatchController.java:72-74`).
- F3 (`follow-ups/review-fixes.md:5-9`; `reviews/impl-review.md:46-54`) wymaga trzech rzeczy:
  - metody `update(playerKey, UnaryOperator<Match>)` na `computeIfPresent` albo compare-and-set po matchId/wersji;
  - braku sekwencji get-then-save dla ruchów;
  - testu współbieżnej aktualizacji.
- Wniosek, nie sprawdzony w kodzie: jeśli tura komputera ma się wykonać w tym samym żądaniu co „koniec tury” gracza, najprościej liczyć ją wewnątrz tej samej funkcji `update`. Wtedy gracz nie zobaczy stanu pośredniego, a dwa kliknięcia nie wykonają tury komputera dwa razy.

### API (`api/`)

- `MatchController` jest pod `@RequestMapping("/api")` (`MatchController.java:25`). Ma:
  - `GET /api/factions` (`:40-46`);
  - `POST /api/matches` z kodem 201, który zastępuje bieżącą partię przez `save` (`:48-61`);
  - `GET /api/matches/current`, który zwraca 404 przy braku partii (`:63-69`).
- Błędne wejście kończy się `ResponseStatusException(BAD_REQUEST, …)` z polskim komunikatem (`:56-58`). To wzór dla odrzuconego ruchu: nie twoja tura, za mało zasobu, karta nie na ręce, partia zakończona.
- `MatchView` lokalizuje teksty w chwili budowy widoku, na podstawie `Language.from(locale)` (`MatchView.java:15-32`). Ręka przeciwnika jest widoczna tylko jako licznik `handCount` (`MatchView.java:57-58`). Postacie przeciwnika na stole będą jawne, więc potrzebna jest nowa struktura widoku stołu dla obu stron.
- Ponieważ widok lokalizuje się przy każdym żądaniu, log zagrań w domenie powinien przechowywać identyfikatory (strona, karta lub `instanceId`, zmiana stresu), a nie gotowe zdania. Lokalizacja może wtedy powstać w `MatchView` albo we froncie. To wniosek z `MatchView.java:15-19` i z refetchu po zmianie języka we froncie.
- „Zagraj ponownie”: `POST /api/matches` już zastępuje partię (`MatchController.java:59`; test `secondStartReplacesMatch`, `MatchControllerTests.java:264-274`). Do restartu nie potrzeba więc nowego endpointu.

### Bezpieczeństwo i trasy

- `anyRequest().authenticated()` (`SecurityConfig.java:29`) i `csrf(c -> c.spa())` (`SecurityConfig.java:35`) obejmują każdy nowy POST pod `/api/matches/...` bez zmian w konfiguracji.
- Lessons (`context/foundation/lessons.md`): każdy endpoint backendu ma leżeć pod `/api/**`. Pilnuje tego `BackendRoutesTests`.
- Wzór testu POST z CSRF to `postMatches()`. Pobiera cookie przez `GET /api/factions`, a potem wysyła cookie i nagłówek (`MatchControllerTests.java:62-74`). Komentarz w tym miejscu zabrania post-procesora `csrf()` we współdzielonym kontekście. Test 403 bez CSRF: `MatchControllerTests.java:352-359`.
- Testy kontrolera izolują graczy przez `newPlayer()` z losowym `sub` (`MatchControllerTests.java:52-60`).

### Frontend (`frontend/src/app/`)

Ustalenia zebrał subagent Explore. Rejestrację interceptorów i sygnały `match-table.ts` sprawdziłem osobno.

- **Modele** (`game/match.ts`):
  - `HandCard {instanceId, cardId, name, type, cost, text, neutral}` (`:23-31`);
  - `PlayerView`/`OpponentView` (`:33-49`);
  - `MatchView {matchId, turn, player, opponent}` (`:51-56`).
  - Brak typów stołu, logu i wyniku.
- **`GameService`** ma tylko `GET /api/factions`, `POST /api/matches` i `GET /api/matches/current` (`game/game.service.ts:11-21`).
- **`match-table.ts`**:
  - sygnały `match` i `error: 'loadMatch' | null` (`:21-23`);
  - `effect` refetchuje stół po zmianie języka i anuluje poprzednie żądanie (`:28-32`);
  - 404 przekierowuje na `/new-match`.
  - Brak metod akcji, flagi `pending` i `computed`.
- **`match-table.html`**:
  - ręka to zwykłe `<li class="card">` w `@for … track card.instanceId` (`:45-55`), bez przycisków;
  - pasek tury jest w `p.turn` (`:24`);
  - stres to `stress/stressLimit` (`:5-8`, `:29-32`).
- **Wzór akcji z `pending` i `finalize`** jest w `new-match.ts:72-91`. Komunikat błędu jest wyliczany przez `computed` z klucza (`new-match.ts:28-37`).
- **i18n**:
  - `Messages` z grupą `matchTable` (`i18n/messages.ts:31-46`);
  - teksty z parametrami to funkcje, np. `turn(n, mine)` (`:40`);
  - `pl.ts`/`en.ts` są typowane jako `Messages`, więc kompilator wymusza oba języki;
  - `languageInterceptor` jest zarejestrowany w `app.config.ts` (`withInterceptors([languageInterceptor, authInterceptor])`) i wysyła `Accept-Language`.
- **Testy** (`match-table.spec.ts`):
  - `HttpTestingController.expectOne(...).flush(...)` (`:105-110`);
  - polski locale przez `LANGUAGE_STORAGE_KEY` przed TestBed (`:84-85`);
  - `afterEach` z `verify()` i `localStorage.clear()` (`:94-97`);
  - `vi.spyOn(Router, 'navigateByUrl')` (`:169`).
- **Miejsca podpięcia**:
  - przycisk „zagraj” w `li.card`, nieaktywny poza turą gracza, przy `cost > resource.current`, w trakcie żądania i po końcu partii;
  - „zakończ turę” przy `p.turn`;
  - log ruchów komputera między strefą przeciwnika a paskiem tury;
  - strefy stołu w obu sekcjach;
  - blok wyniku z „zagraj ponownie” w `match-table`, bo trasy to tylko `''`, `new-match`, `match` i `sign-in` (`app.routes.ts:8-14`).

## Code References

- `src/main/java/com/example/itcardbrawl/game/Match.java:8-13` — rekord partii, brak stołu, statusu i logu
- `src/main/java/com/example/itcardbrawl/game/MatchSide.java:8-14` — stan strony
- `src/main/java/com/example/itcardbrawl/game/MatchRules.java:6-21` — stałe i opis zasad tury „od S-03”
- `src/main/java/com/example/itcardbrawl/game/MatchFactory.java:30-65` — stan startowy, losowanie zaczynającego, tasowanie
- `src/main/java/com/example/itcardbrawl/game/MatchStore.java:16-25` — `save` i `findCurrent`, miejsce na `update` (F3)
- `src/main/java/com/example/itcardbrawl/game/Card.java:6` — karta bez wartości efektu
- `src/main/java/com/example/itcardbrawl/game/CardCatalog.java:100-124,191-198` — walidacja kart i obowiązek pl/en
- `src/main/java/com/example/itcardbrawl/game/GameConfig.java:22-25` — wstrzykiwany `RandomGenerator`
- `src/main/java/com/example/itcardbrawl/api/MatchController.java:48-74` — start, current, klucz gracza, wzór 400
- `src/main/java/com/example/itcardbrawl/api/MatchView.java:15-70` — widok z perspektywy gracza, lokalizacja per żądanie
- `src/main/java/com/example/itcardbrawl/config/SecurityConfig.java:29,35` — auth i CSRF SPA
- `src/main/resources/game/factions.json:23-99` — karty i talie (15 postaci i 15 wydarzeń na talię)
- `src/test/java/com/example/itcardbrawl/api/MatchControllerTests.java:52-74,264-274,352-359` — wzory testów (gracz, CSRF, zastąpienie partii)
- `src/test/java/com/example/itcardbrawl/game/MatchFactoryTests.java:22-39` — deterministyczny `RandomGenerator` w testach
- `frontend/src/app/game/match.ts:23-56` — modele TS
- `frontend/src/app/game/game.service.ts:11-21` — wywołania HTTP
- `frontend/src/app/match-table/match-table.ts:21-32` — sygnały i refetch po zmianie języka
- `frontend/src/app/match-table/match-table.html:24,45-55` — pasek tury i ręka
- `frontend/src/app/new-match/new-match.ts:28-37,72-91` — wzór `pending` i komunikatu błędu
- `frontend/src/app/i18n/messages.ts:31-46` — klucze `matchTable`

## Architecture Insights

- **Domena jest niemutowalna i ma czyste funkcje.** Rekordy z `List.copyOf` i fabryka bez Springa (`MatchFactoryTests` tworzy ją ręcznie) sugerują silnik ruchów jako czystą klasę w `game/`, np. `(Match, Move) -> Match`, testowaną bez MockMvc. Taki silnik dobrze pasuje do `MatchStore.update(key, UnaryOperator<Match>)`.
- **Serwer jest jedynym źródłem prawdy, a SPA jest cienkie.** AGENTS.md: „Game rules and the opponent live in Java (`game/`)”. Walidacja ruchu (tura, zasób, karta na ręce, typ `CHARACTER`) i tura komputera należą do backendu. Front tylko wyłącza przyciski dla wygody.
- **Widok jest lokalizowany per żądanie, a domena nie zależy od języka.** Log zdarzeń w domenie powinien być strukturalny.
- **Ukryte informacje zostają na serwerze.** `MatchView` nie ujawnia ręki ani kolejności talii przeciwnika (`MatchView.java:15-17`). Log ruchów komputera może pokazać zagraną kartę, bo staje się jawna na stole, ale nie dobierane karty.

## Historical Context (from prior changes)

- `context/archive/2026-11-19-match-setup-table/plan.md:7,51,92` ustala zasady startu: ręka 5, losowy zaczynający, zasób od 1 do 10 rosnący co turę, dobieranie 1 karty z wyjątkiem pierwszej tury zaczynającego, „reguła wykonywana od S-03”. **Potwierdzone** w `MatchRules.java:6-21` i `MatchFactory.java:42,63`.
- `match-setup-table/plan.md:38-39` przesuwa do S-03 zagrywanie kart, wydawanie zasobu, dobieranie, turę komputera, koniec partii i ekran wyniku. Wartości efektów kart przesuwa do „S-03/S-04”. **Potwierdzone**, bo w kodzie żadnej z tych rzeczy nie ma (sprawdzone pliki `game/` i `api/`).
- `match-setup-table/plan-brief.md:65`: „Gdy zaczyna komputer, stół pokazuje »tura przeciwnika« i nic się nie dzieje — tura komputera przychodzi w S-03.” Dziś partia z zaczynającym komputerem stoi w miejscu. S-03 musi rozegrać pierwszą turę komputera przy starcie partii (w `POST /api/matches`) albo przy pierwszym odczycie.
- `match-setup-table/reviews/impl-review.md:26-34` (F1, przewidywalny `instanceId`). **Naprawione w kodzie**: tasowanie przed numeracją (`MatchFactory.java:50`). To ważne, bo w S-03 `instanceId` kart przeciwnika trafią na stół i do logu.
- `match-setup-table/reviews/impl-review.md:46-54` i `follow-ups/review-fixes.md` (F3). **Otwarte**, wejście do planu S-03.
- `roadmap.md:48` pokazuje S-03 jako `blocked`. Prerequisite S-02 ma status `done` (`roadmap.md:47`, §Done). Jedyną pozostałą blokadą jest Unknown z `Block: yes` o zasadach postaci (`roadmap.md:144`). Open Roadmap Question 3 (`roadmap.md:207`) o spisaniu zasad gry nadal dotyczy S-03.

## Related Research

- Nie dotyczy: archiwalne zmiany (`spa-api-walking-skeleton`, `external-login-gate`, `deploy-pipeline-skeleton`, `match-setup-table`) mają plany, ale żadna nie ma `research.md` o pętli gry.

## Open Questions

Decyzje produktowe (Owner: user) potrzebne przed `/10x-plan`. Pierwsza blokuje plan według roadmapy.

1. **Zasady postaci na stole** (`roadmap.md:144`, Block: yes):
   - Czy postać podnosi stres przy zagraniu, co turę, czy oba?
   - O ile? Potrzebne nowe pole w katalogu albo reguła wyprowadzona z kosztu.
   - Czy postacie wchodzą w interakcje (blokują, atakują)?
   - Czy i kiedy schodzą ze stołu?
   - Czy jest limit miejsc?
2. **Wydarzenia w S-03.** 15 z 30 kart talii to wydarzenia (S-04). Możliwe opcje:
   - nie da się ich zagrać (zalegają na ręce);
   - można je zagrać bez efektu albo odrzucić;
   - S-03 tymczasowo dostaje talie z samych postaci.

   Wybór wpływa na tempo partii i limit ≤ 15 min z NFR.
3. **Dostarczanie tury komputera.** Możliwe opcje:
   - cała tura liczona synchronicznie w odpowiedzi na „zakończ turę”, z logiem kroków w `MatchView`;
   - kroki odtwarzane po kolei we froncie (tylko animacja z gotowego logu).
4. **Strategia komputera w S-03.** Roadmapa mówi „tylko poprawne ruchy” (`roadmap.md:145`), a „sensowność” to S-05. Do wyboru: prosta deterministyczna reguła (np. zagraj najdroższą postać, na którą stać) albo losowy poprawny ruch przez wstrzyknięty `RandomGenerator`.
5. **Moment sprawdzania końca partii.** Stres ≥ 30 sprawdzany zaraz po każdym zagraniu albo dopiero na koniec tury. Przegrana „musi dobrać z pustej talii” sprawdzana na początku tury przy dobieraniu.
6. **„Zagraj ponownie”.** Ta sama frakcja od razu (`POST /api/matches` z `player.faction.id`) czy powrót do `/new-match`. PRD FR-008 mówi „od razu zagrać ponownie”.
7. **Lokalizacja logu.** Zdania budowane w `MatchView` po stronie backendu z tekstami pl/en czy we froncie przez funkcje w `Messages` ze strukturalnych zdarzeń.

## Follow-up 2026-11-20: decyzje użytkownika

Odpowiedzi użytkownika na Open Questions 1–7 (cytowane z rozmowy, w tłumaczeniu). Każda zamyka pytanie w podanym zakresie; poniżej wpływ na kod i pytania szczegółowe, które zostają dla `/10x-plan`.

| # | Decyzja | Wpływ na kod (wniosek z Detailed Findings) |
|---|---------|---------------------------------------------|
| 1 | Postać ma **atak i HP**; niektóre mają **zdolność specjalną**; obrażenia postaci są **czyszczone na koniec rundy**; **maks. 5 postaci** na stole na stronę. | `Card` (`Card.java:6`) potrzebuje pól ataku, HP i zdolności; walidacja zakresów w `CardCatalog.indexCards` (`CardCatalog.java:100-124`); tekst zdolności po pl/en (`CardCatalog.java:191-198`). `MatchSide` dostaje stół (≤ 5 postaci z bieżącymi obrażeniami). `factions.json` dostaje wartości dla 15 unikalnych postaci w katalogu (po 5 frakcyjnych na każdą z 2 frakcji + 5 neutralnych; `factions.json:23-54`). |
| 2 | Wydarzenie ma **koszt i czas trwania**; bez czasu trwania efekt jest **natychmiastowy**, a karta trafia na **cmentarz**. | Nowe pole czasu trwania wydarzenia w `Card`; `MatchSide` dostaje cmentarz i listę aktywnych efektów z licznikiem tur. Zmienia zakres: wydarzenia wchodzą do S-03 (zob. pytanie A). |
| 3 | Komputer rozgrywa swoją turę, a potem kończy turę (zob. pytanie B). | Tura komputera liczona po stronie serwera; log kroków w stanie partii (Summary, refetch po zmianie języka). |
| 4 | Komputer na razie wybiera **losowy poprawny ruch**. | Generator poprawnych ruchów + wybór przez wstrzyknięty `RandomGenerator` (`GameConfig.java:22-25`), deterministyczny w testach jak w `MatchFactoryTests.java:22-39`. Ten sam generator przyda się do walidacji ruchu gracza i do S-05. |
| 5 | Koniec partii: **gdy tylko stres strony osiągnie 30**, albo **na początku tury**, gdy strona nie może dobrać karty. | Sprawdzenie limitu po każdej zmianie stresu (także w środku tury komputera — reszta tury przerwana); sprawdzenie pustej talii przy dobieraniu na początku tury (`MatchRules.java:6-10`). Pierwsza tura zaczynającego pomija dobieranie, więc tam nie przegrywa. |
| 6 | „Zagraj ponownie” → **powrót do wyboru frakcji**. | Link/nawigacja do `/new-match` (`app.routes.ts:10`); bez nowego endpointu. |
| 7 | Teksty logu **po stronie backendu, pl i en**. | Domena trzyma strukturalne zdarzenia; `MatchView` składa zdania w języku żądania (`MatchView.java:15-19`). Szablony zdań muszą mieć obie wersje — miejsce (kod Java vs zasoby) do ustalenia w planie. |

### Pytania szczegółowe, które zostają dla planu (Owner: user)

- **A. Zakres wydarzeń.** Decyzja 2 opisuje mechanikę wydarzeń, a roadmapa ma je w S-04 (`roadmap.md:148-159`). Czy S-03 obejmuje zagrywanie wydarzeń (z efektami), czy tylko model (koszt/czas trwania/cmentarz), a efekty zostają w S-04? Jeśli w S-03 — jakie rodzaje efektów (stres, obrażenia postaci, dobieranie)?
- **B. Interpretacja decyzji 3.** (a) Komputer wykonuje całą turę i sam ją kończy, a gracz od razu widzi log i swoją turę; czy (b) gracz po obejrzeniu ruchów komputera klika przycisk, żeby przejść do swojej tury?
- **C. Walka postaci.** Jak atak i HP przekładają się na stres: czy postać atakuje stres przeciwnika bezpośrednio, czy postacie przeciwnika blokują / mogą być celem ataku? Czy postać może atakować w turze zagrania? Ile razy na turę? Co się dzieje przy HP ≤ 0 (cmentarz)?
- **D. „Runda”.** Czy obrażenia czyszczą się na koniec każdej tury, czy po pełnej rundzie (tura gracza + tura komputera)?
- **E. Zdolności specjalne.** Jaki katalog zdolności w v1 (np. „przy zagraniu: +2 stresu przeciwnikowi”) i które postacie je mają? Wartości ataku/HP i zdolności to treść autorska (roadmapa S-06: „agent nie wymyśli za autora”) — czy w S-03 wystarczą tymczasowe wartości, np. wyprowadzone z kosztu?
- **F. Pełny stół.** Przy 5 postaciach: czy zagranie szóstej postaci jest zabronione (ruch niepoprawny)?
- **G. Roadmapa.** Decyzje 1–7 zamykają blokujący Unknown S-03 (`roadmap.md:144`) i część Open Roadmap Question 3 (`roadmap.md:207`); status S-03 `blocked` → `ready` oraz przeniesienie wydarzeń zależą od odpowiedzi A.

### Odpowiedzi na pytania szczegółowe (2026-11-20)

- **A → model wydarzeń w S-03, efekty w S-04.** S-03 dodaje do modelu koszt, czas trwania i cmentarz; wydarzenia nie mają jeszcze efektów (sposób ich obsługi w ręce — niegrywalne czy zagrywane bez efektu — do ustalenia w planie). Roadmapa S-04 zostaje bez zmiany zakresu.
- **B → (a).** Komputer rozgrywa całą turę na serwerze i sam ją kończy; gracz od razu widzi log ruchów komputera i swoją turę.
- **C → postać atakuje postać przeciwnika albo bezpośrednio jego stres.** Obrażenia zmniejszają HP celu; postać z HP ≤ 0 trafia na cmentarz. Postacie przeciwnika nie wymuszają blokowania.
- **D → koniec każdej tury.** Obrażenia postaci są czyszczone po turze każdej strony.

Nadal otwarte dla `/10x-plan` (propozycje domyślne do potwierdzenia w planie):
- Czy postać atakuje w turze zagrania i ile razy na turę (propozycja: nie w turze zagrania, raz na turę)? Czy obrońca zadaje obrażenia atakującemu?
- Katalog zdolności specjalnych v1 i wartości atak/HP dla 15 postaci — treść autorska; czy w S-03 wystarczą wartości tymczasowe?
- Pełny stół (5 postaci): zagranie szóstej postaci jako ruch niepoprawny (propozycja: tak).
