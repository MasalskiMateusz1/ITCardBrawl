# Pełna partia postaciami przeciw komputerowi — plan implementacji

## Overview

Element roadmapy S-03 (north star, FR-004, FR-006, FR-008, US-01): gracz zagrywa karty postaci na stół, atakuje nimi postacie przeciwnika albo bezpośrednio jego stres, kończy turę, a komputer rozgrywa własną turę losowymi poprawnymi ruchami. Gracz widzi log każdego zagrania i jego skutek. Partia kończy się, gdy stres którejś strony osiągnie 30 albo gdy strona musi dobrać kartę z pustej talii. Gracz widzi wynik i wraca do wyboru frakcji.

Zasady postaci, wydarzeń i tury pochodzą z decyzji użytkownika zapisanych w `research.md` (sekcje Follow-up) i z sesji planowania (Key Decisions w `plan-brief.md`).

## Current State Analysis

- Model partii jest niemutowalny, ale nie ma pętli gry. `Match(id, turnNumber, activeSide, player, opponent)` (`Match.java:8`), `MatchSide(faction, deck, hand, stress, resourceCurrent, resourceMax)` (`MatchSide.java:8-9`). Brakuje stołu, cmentarza, aktywnych wydarzeń, logu, statusu i wyniku.
- `Card(id, faction, name, type, cost, text)` (`Card.java:6`) nie ma ataku, HP, zdolności ani czasu trwania. Walidacja katalogu jest zbiorcza i zatrzymuje start (`CardCatalog.java:37-45,100-124`). Każdy tekst wymaga `pl` i `en` (`CardCatalog.java:191-198`).
- Zasady tury istnieją jako stałe `MatchRules` (`MatchRules.java:6-21`), ale nikt ich nie wykonuje. Stan startowy oznacza początek tury 1 zaczynającego: zasób 1/1, druga strona 0/0 (`MatchFactory.java:42,63`).
- `MatchStore` ma tylko `save` i `findCurrent` (`MatchStore.java:19-25`). Follow-up F3 wymaga atomowego `update` (`context/archive/2026-11-19-match-setup-table/follow-ups/review-fixes.md`).
- API: `POST /api/matches` (zastępuje partię), `GET /api/matches/current` (`MatchController.java:48-69`). `MatchView` lokalizuje teksty per żądanie i ukrywa rękę przeciwnika (`MatchView.java:15-32`).
- Partia, w której zaczyna komputer, dziś stoi w miejscu (`context/archive/2026-11-19-match-setup-table/plan-brief.md:65`).
- Frontend `match-table` tylko wyświetla stan. Refetchuje `GET /current` po każdej zmianie języka (`match-table.ts:28-32`). Brak przycisków i metod POST (`game.service.ts:11-21`).

## Desired End State

- Gracz w `/match` zagrywa karty (postacie na stół, maks. 5; wydarzenia za koszt, bez efektu), atakuje gotowymi postaciami i kończy turę. Komputer od razu rozgrywa swoją turę. Wszystko widać w logu po polsku albo po angielsku, zależnie od języka.
- Partia kończy się wynikiem (wygrana/przegrana i przyczyna) i linkiem „Zagraj ponownie” do `/new-match`.
- Serwer jest jedynym źródłem prawdy: niepoprawny ruch dostaje 400, nieaktualne żądanie (inna partia albo inna tura) dostaje 409. Każdy ruch przechodzi przez atomowe `MatchStore.update`.
- Weryfikacja: `./mvnw -B verify`, `cd frontend && npx ng test --watch=false`, `cd frontend && npm run build` oraz ręczne rozegranie pełnej partii.

### Key Discoveries:

- Niemutowalne rekordy z `List.copyOf` (`MatchSide.java:11-14`) pasują do `UnaryOperator<Match>` w `ConcurrentHashMap.computeIfPresent`. Wyjątek rzucony w funkcji zostawia wpis bez zmian, co daje darmowy rollback odrzuconego ruchu.
- Testy domeny podstawiają deterministyczny `RandomGenerator` (`MatchFactoryTests.java:22-39`). Ten sam wzór obsłuży losowy komputer.
- Wzór testu POST z CSRF: `postMatches()` (`MatchControllerTests.java:62-74`), bez post-procesora `csrf()`.
- Wzór akcji we froncie z `pending`, `finalize` i komunikatem błędu z klucza: `new-match.ts:28-37,72-91`.
- Lessons: każdy endpoint pod `/api/**`. `BackendRoutesTests` sprawdza nowe trasy automatycznie.

## What We're NOT Doing

- Efekty wydarzeń. Wydarzenie płaci koszt, trafia na cmentarz albo wisi jako aktywne przez `duration` tur, ale niczego nie zmienia (S-04).
- Sensowna strategia komputera i „powód ruchu” (S-05). Komputer wybiera losowo spośród poprawnych ruchów.
- Docelowy balans i treść kart (S-06). Wartości z Fazy 1 są pierwszym szkicem.
- Zapis partii poza pamięcią procesu, PvP, historia partii, animacje i odtwarzanie tury komputera krok po kroku.
- Limit stresu jako sufit. Stres przechowuje rzeczywistą wartość (np. 33/30).
- Zmiany roadmapy poza synchronizacją statusu S-03 (np. przeniesienie modelu wydarzeń z S-04).

## Implementation Approach

Kolejność: dane katalogu, potem czysta domena, potem orkiestracja, API i UI. Każda warstwa jest testowalna przed następną.

Jeden czysty silnik reguł (`MatchEngine`) przyjmuje `(Match, Side, Move)` i zwraca nowy `Match` albo rzuca `IllegalMoveException`. Komputer używa tego samego silnika (`legalMoves` + `apply`), więc gra poprawnie z konstrukcji. `MatchService` łączy silnik, komputer i `MatchStore.update`. Cały ruch gracza, a dla „końca tury” także cała tura komputera, wykonuje się w jednej atomowej aktualizacji. Log przechowuje strukturalne zdarzenia. `MatchView` składa z nich zdania w języku żądania.

## Critical Implementation Details

- **Kolejność końca tury:** (1) Overtime aktywnej strony, każda postać +1 stresu przeciwnikowi ze sprawdzeniem końca po każdym; (2) wyczyszczenie obrażeń wszystkich postaci obu stron; (3) zmiana aktywnej strony i `turnNumber + 1`; (4) początek tury nowej strony: tick aktywnych wydarzeń (`turnsLeft - 1`, przy 0 na cmentarz); dobranie (pusta talia → ta strona przegrywa `EMPTY_DECK`; pełna ręka 10 → karta spalona na cmentarz); zasób `max = min(max + 1, 10)`, `current = max`; postacie tej strony stają się gotowe do ataku.
- **Stan startowy to już początek tury 1.** Zaczynający nie dobiera w turze 1, bo `MatchFactory` tworzy stan po rozdaniu i nie uruchamia „początku tury”. Rutyna początku tury działa dopiero przy przejściu tur.
- **Koniec partii działa natychmiast.** Każda zmiana stresu (atak w stres, On play, Overtime) sprawdza `stress >= 30`. Partia przechodzi w `FINISHED` i dalsze kroki tury (np. reszta Overtime, ruchy komputera) się nie wykonują. Po `FINISHED` każdy ruch dostaje `IllegalMoveException`.
- **Podwójny klik „Zakończ turę”.** Każde żądanie ruchu niesie `matchId` i `turn`. Niezgodność z bieżącą partią daje 409 bez zmiany stanu. Bez tego drugie kliknięcie zakończyłoby turę gracza zaraz po turze komputera.
- **Ukryta informacja:** log nie ujawnia kart dobieranych przez przeciwnika („Przeciwnik dobiera kartę”). Zagrane i spalone karty przeciwnika są jawne.

---

## Phase 1: Statystyki i zdolności kart w katalogu

### Overview

Karty postaci dostają atak, HP i opcjonalną zdolność, a wydarzenia czas trwania. Katalog waliduje nowe pola, a `factions.json` dostaje zatwierdzone wartości. Bez zmian w partii.

### Changes Required:

#### 1. Model karty i zdolności

**File**: `src/main/java/com/example/itcardbrawl/game/Card.java`, nowe `Ability.java`, `AbilityKeyword.java`

**Intent**: Dać kartom liczby, na których oprze się silnik. Pola nieużywane przez dany typ są `null`.

**Contract**:
- `Card(String id, String faction, LocalizedText name, CardType type, int cost, LocalizedText text, Integer attack, Integer health, Ability ability, Integer duration)`.
- `Ability(AbilityKeyword keyword, int amount)`.
- `AbilityKeyword { ON_PLAY_STRESS, HEAL, GUARD, RUSH, OVERTIME }`. `ON_PLAY_STRESS` i `HEAL` wymagają `amount` w zakresie 1–10. `GUARD`, `RUSH` i `OVERTIME` mają `amount = 0` (w JSON pole można pominąć).
- JSON: `"attack": 4, "health": 6, "ability": { "keyword": "GUARD" }` dla postaci i `"duration": 2` dla wydarzenia.

#### 2. Walidacja katalogu

**File**: `src/main/java/com/example/itcardbrawl/game/CardCatalog.java`

**Intent**: Niepoprawne statystyki mają zatrzymać start z czytelnym, zbiorczym komunikatem (styl istniejących błędów, po polsku).

**Contract**: W `indexCards`:
- `CHARACTER` wymaga `attack` 1–10 i `health` 1–10. Nie może mieć `duration`. `ability` jest opcjonalne, z poprawnym `amount` dla słowa kluczowego.
- `EVENT` wymaga `duration` 0–5. Nie może mieć `attack`, `health` ani `ability`.
- Nowe stałe zakresów obok `MIN_CARD_COST`.

#### 3. Teksty zdolności pl/en

**File**: `src/main/java/com/example/itcardbrawl/game/AbilityKeyword.java` (lub osobny `AbilityTexts.java`)

**Intent**: Gracz musi widzieć, co robi zdolność. Tekst generuje się ze słowa kluczowego i liczby, więc katalog nie potrzebuje dodatkowych pól tekstowych.

**Contract**: `LocalizedText describe(int amount)` dla każdego słowa kluczowego, z obiema wersjami językowymi (przełącznik po enumie, kompletny przy kompilacji). Przykłady PL/EN:
- „Po zagraniu: +N stresu przeciwnika” / „On play: +N opponent stress”
- „Po zagraniu: −N własnego stresu” / „On play: −N own stress”
- „Ochrona: musi zostać zaatakowana pierwsza” / „Guard: must be attacked first”
- „Szarża: atakuje od razu” / „Rush: attacks immediately”
- „Nadgodziny: na koniec tury +1 stresu przeciwnika” / „Overtime: end of turn +1 opponent stress”

#### 4. Wartości kart

**File**: `src/main/resources/game/factions.json`

**Intent**: Wpisać zatwierdzone wartości (decyzja użytkownika: szkic agenta przyjęty bez zmian). Pozostałe pola i talie bez zmian.

**Contract**: Postacie (`attack/health`, zdolność):

| Karta | Atk/HP | Zdolność |
|---|---|---|
| dev-junior | 1/1 | RUSH |
| dev-fullstack | 3/3 | — |
| dev-backend | 4/6 | GUARD |
| dev-senior | 6/7 | HEAL 3 |
| dev-tech-lead | 8/8 | ON_PLAY_STRESS 4 |
| tst-intern | 1/2 | ON_PLAY_STRESS 1 |
| tst-manual | 2/4 | GUARD |
| tst-automation | 4/5 | OVERTIME |
| tst-performance | 7/5 | ON_PLAY_STRESS 3 |
| tst-qa-lead | 7/10 | GUARD |
| neu-rubber-duck | 1/3 | HEAL 2 |
| neu-scrum-master | 2/4 | GUARD |
| neu-devops | 4/4 | OVERTIME |
| neu-project-manager | 4/5 | ON_PLAY_STRESS 3 |
| neu-client | 8/9 | ON_PLAY_STRESS 5 |

Wydarzenia `duration`:
- **2:** `dev-friday-hotfix`, `dev-legacy-refactor`, `tst-regression`, `neu-sprint-planning`;
- **3:** `tst-flaky-test`;
- **0:** wszystkie pozostałe (`dev-stackoverflow`, `dev-works-on-my-machine`, `dev-force-push`, `tst-vague-bug-report`, `tst-edge-case`, `tst-production-bug`, `neu-coffee`, `neu-daily-standup`, `neu-meeting`, `neu-deadline`).

#### 5. Testy katalogu

**File**: `src/test/java/com/example/itcardbrawl/game/CardCatalogTests.java`

**Intent**: Pokryć każdą nową regułę walidacji po jednym przypadku odrzucenia, w stylu istniejących testów (`costBelowRangeIsRejected`).

**Contract**:
- Prawdziwy katalog ładuje się z wartościami z tabeli, np. sprawdzenie `dev-backend` = 4/6 GUARD i `tst-flaky-test` = 3.
- Odrzucenia:
  - postać bez `attack` albo `health`, lub z wartością spoza 1–10;
  - postać z `duration`;
  - wydarzenie bez `duration`, z `duration` spoza zakresu albo z `attack`;
  - `ON_PLAY_STRESS` z `amount` 0.
- `describe` zwraca niepusty tekst pl i en dla każdego słowa kluczowego.

### Success Criteria:

#### Automated Verification:

- Testy katalogu przechodzą: `./mvnw -B test -Dtest=CardCatalogTests`
- Pełny build backendu przechodzi: `./mvnw -B verify`

#### Manual Verification:

- Przegląd `factions.json`: wartości zgadzają się z tabelą z planu, a teksty zdolności pl/en czytają się naturalnie

**Implementation Note**: Po przejściu bramek automatycznych zatrzymaj się na ręczne potwierdzenie przed kolejną fazą.

---

## Phase 2: Silnik reguł partii

### Overview

Rozszerzenie stanu partii i czysty silnik ruchów bez Springa i HTTP: zagranie, atak, koniec tury, początek tury, zdolności, limit ręki, koniec partii i log zdarzeń.

### Changes Required:

#### 1. Stan partii

**File**: `game/Match.java`, `game/MatchSide.java`, nowe `BoardCharacter.java`, `ActiveEvent.java`, `MatchResult.java`

**Intent**: Pomieścić wszystko, co silnik zmienia i co widok pokazuje. Rekordy pozostają niemutowalne.

**Contract**:
- `MatchSide(..., List<BoardCharacter> board, List<CardInstance> graveyard, List<ActiveEvent> activeEvents)` z `List.copyOf` jak dziś.
- `BoardCharacter(CardInstance instance, int damage, boolean canAttack)`; pozostałe HP = `health - damage`.
- `ActiveEvent(CardInstance instance, int turnsLeft)`.
- `Match(..., Status status, MatchResult result, List<MatchEvent> log)`, gdzie `Status { IN_PROGRESS, FINISHED }`, a `MatchResult(Side winner, EndReason reason)` z `EndReason { STRESS_LIMIT, EMPTY_DECK }`. `result` jest `null`, dopóki partia trwa.
- `MatchFactory` tworzy puste stół, cmentarz, wydarzenia i log, status `IN_PROGRESS`, a w logu jedno `TurnStarted` dla zaczynającego.
- `MatchRules.HAND_LIMIT = 10`, `MatchRules.BOARD_LIMIT = 5`.

#### 2. Zdarzenia logu

**File**: nowe `game/MatchEvent.java` (sealed interface z rekordami)

**Intent**: Strukturalny, niezależny od języka zapis każdego kroku. Lokalizuje go dopiero widok (Faza 4).

**Contract**: Każde zdarzenie ma `Side side`. Rekordy:
- `TurnStarted(turn)`, `CardDrawn(CardInstance)`, `CardBurned(CardInstance)`, `CardPlayed(CardInstance)`;
- `AbilityTriggered(CardInstance, AbilityKeyword, int amount)`;
- `Attacked(attacker, CardInstance target /* null = stres */, int damageDealt, int damageTaken)`;
- `StressChanged(Side affected, int from, int to)`, `CharacterDefeated(CardInstance)`, `EventExpired(CardInstance)`;
- `TurnEnded()`, `MatchEnded(MatchResult)`.

Log trzyma całą partię (decyzja użytkownika).

#### 3. Ruchy i silnik

**File**: nowe `game/Move.java`, `game/MatchEngine.java`, `game/IllegalMoveException.java`

**Intent**: Jedno miejsce wykonujące zasady. Zarówno gracz, jak i komputer przechodzą przez `apply`.

**Contract**:
- `sealed interface Move`: `PlayCard(int instanceId)`, `Attack(int attackerId, Integer targetId /* null = stres przeciwnika */)`, `EndTurn()`.
- `MatchEngine` (bez zależności, `@Component`): `Match apply(Match, Side, Move)` i `List<Move> legalMoves(Match, Side)`. Lista nie zawiera `EndTurn`, ale `EndTurn` jest zawsze poprawne dla aktywnej strony w trwającej partii.
- `IllegalMoveException(String reason)`, z polskim komunikatem.

Zasady (decyzje użytkownika i planu):
- **Każdy ruch** wymaga `status == IN_PROGRESS` i `side == activeSide`.
- **`PlayCard`:**
  - karta musi być na ręce i `cost <= resourceCurrent`; zasób maleje o koszt;
  - **postać:** wymaga `board.size() < 5`; wchodzi z `damage 0` i `canAttack = RUSH`; `ON_PLAY_STRESS` dodaje `amount` do stresu przeciwnika; `HEAL` odejmuje `amount` od własnego stresu, nie poniżej 0;
  - **wydarzenie:** `duration 0` trafia od razu na cmentarz, w przeciwnym razie do `activeEvents` z `turnsLeft = duration`; nie ma efektu.
- **`Attack`:**
  - atakujący musi być na własnym stole z `canAttack`;
  - jeśli przeciwnik ma postać z `GUARD`, celem musi być jedna z nich (stres i inne postacie odpadają); w przeciwnym razie celem może być dowolna postać przeciwnika albo stres;
  - **atak w postać:** obie strony zadają sobie atak jednocześnie; postać z `damage >= health` trafia na cmentarz;
  - **atak w stres:** stres przeciwnika rośnie o `attack`, bez sufitu;
  - atakujący dostaje `canAttack = false`.
- **`EndTurn`:** kolejność z sekcji Critical Implementation Details.
- **Koniec partii:**
  - `stress >= 30` po każdej zmianie stresu; przegrywa strona z takim stresem, przyczyna `STRESS_LIMIT`;
  - dobranie z pustej talii na początku tury; przegrywa ta strona, przyczyna `EMPTY_DECK`;
  - oba warunki dają `FINISHED`, `result` i `MatchEnded` w logu.

#### 4. Testy silnika

**File**: nowe `src/test/java/com/example/itcardbrawl/game/MatchEngineTests.java`

**Intent**: Każda reguła ma test na ręcznie zbudowanym stanie (pomocnicze budowniczki `Match` w teście), bez losowości.

**Contract**: Przypadki co najmniej:
- **Zagranie:** postać płaci koszt i wchodzi na stół; za mało zasobu daje odrzucenie; szósta postać daje odrzucenie; karta spoza ręki daje odrzucenie; ruch nie w swojej turze daje odrzucenie; wydarzenie z `duration 0` idzie na cmentarz, z `duration 2` do aktywnych.
- **Gotowość:** świeża postać nie atakuje, z `RUSH` atakuje; atak drugi raz w tej samej turze daje odrzucenie.
- **Walka:** obrażenia są wzajemne i śmierć obu jest możliwa; `GUARD` blokuje atak w stres i w postać bez Ochrony; atak w stres podnosi stres powyżej 30 (np. 28 + 5 = 33) i kończy partię `STRESS_LIMIT`.
- **Zdolności:** `ON_PLAY_STRESS` kończy partię przy przekroczeniu 30; `HEAL` nie schodzi poniżej 0; `OVERTIME` dodaje stres na koniec tury i może zakończyć partię, a dalsze kroki się nie wykonują.
- **Koniec tury:** obrażenia znikają na koniec tury; zasób rośnie do sufitu 10; wydarzenie wygasa po `duration` turach właściciela; dobranie przy pełnej ręce 10 pali kartę; pusta talia na początku tury daje `EMPTY_DECK`.
- **Po końcu:** po `FINISHED` każdy ruch daje odrzucenie.
- **`legalMoves`:** nie zawiera nieopłacalnych kart ani ataków niegotowych postaci i respektuje `GUARD`.
- **Log:** zawiera oczekiwane zdarzenia w kolejności dla jednego scenariusza zagranie → atak → koniec tury.

### Success Criteria:

#### Automated Verification:

- Testy silnika przechodzą: `./mvnw -B test -Dtest=MatchEngineTests`
- Testy fabryki nadal przechodzą: `./mvnw -B test -Dtest=MatchFactoryTests`
- Pełny build backendu przechodzi: `./mvnw -B verify`

**Implementation Note**: Po przejściu bramek automatycznych zatrzymaj się na ręczne potwierdzenie przed kolejną fazą.

---

## Phase 3: Komputer i atomowy magazyn partii

### Overview

Losowy przeciwnik grający przez silnik, atomowa aktualizacja `MatchStore` (F3) i `MatchService` spinający start partii oraz ruchy gracza z turą komputera.

### Changes Required:

#### 1. Losowy przeciwnik

**File**: nowe `game/RandomOpponent.java`

**Intent**: Komputer wykonuje losowe poprawne ruchy, dopóki jakieś są, potem kończy turę (decyzja użytkownika). Sensowna strategia to S-05.

**Contract**: `Match playTurn(Match)`, wstrzyknięty `RandomGenerator` (`GameConfig.java:22-25`) i `MatchEngine`. Pętla: dopóki `IN_PROGRESS` i aktywny jest `OPPONENT`, losuje jednostajnie z `legalMoves(OPPONENT)`; gdy lista jest pusta, wykonuje `EndTurn`. Bezpiecznik: maks. 100 ruchów, potem wymuszone `EndTurn`.

#### 2. Atomowa aktualizacja magazynu

**File**: `game/MatchStore.java`

**Intent**: Zamknąć F3: żaden ruch nie używa get-then-save.

**Contract**: `Optional<Match> update(String playerKey, UnaryOperator<Match> change)` na `computeIfPresent`. Wyjątek z `change` zostawia poprzednią partię bez zmian i przechodzi do wywołującego. `save` zostaje dla startu partii.

#### 3. Serwis partii

**File**: nowe `game/MatchService.java`, nowe `game/StaleMoveException.java`

**Intent**: Jedno miejsce orkiestracji używane przez kontroler. Kontroler w Fazie 4 nie dotyka bezpośrednio silnika ani magazynu.

**Contract**:
- `Match start(String playerKey, String factionId)` tworzy partię; jeśli zaczyna `OPPONENT`, wywołuje `playTurn`; zapisuje przez `save`.
- `Match move(String playerKey, UUID matchId, int turn, Move move)` działa w `update`:
  - niezgodny `matchId` albo `turn` daje `StaleMoveException`;
  - w przeciwnym razie wykonuje `engine.apply(m, PLAYER, move)`; po `EndTurn` w trwającej partii także `opponent.playTurn`;
  - brak partii daje `Optional.empty`, a kontroler zwraca 404.
- `Optional<Match> current(String playerKey)`.

#### 4. Testy

**File**: nowe `src/test/java/com/example/itcardbrawl/game/RandomOpponentTests.java`, `MatchStoreTests.java`, `MatchServiceTests.java`

**Intent**: Deterministycznie sprawdzić komputer i współbieżność.

**Contract**:
- `RandomOpponent` z ustalonym `RandomGenerator`: kończy z aktywnym `PLAYER` albo `FINISHED`; każdy ruch w logu jest poprawny (rozgrywa 50 pełnych partii komputer kontra komputer na różnych ziarnach i kończy bez wyjątku z `result != null`); nie zostawia nieopłacalnych zagrań przy dostępnych.
- `MatchStore`: 2 wątki × N równoległych `update` inkrementujących `turnNumber` nie gubią aktualizacji; wyjątek w `change` zostawia stan bez zmian.
- `MatchService`:
  - start z zaczynającym komputerem zwraca partię z aktywnym `PLAYER` w turze 2;
  - nieaktualny `turn` albo `matchId` daje `StaleMoveException` bez zmiany stanu;
  - `EndTurn` uruchamia turę komputera;
  - podwójny `EndTurn` z tym samym `turn` daje `StaleMoveException` za drugim razem.

### Success Criteria:

#### Automated Verification:

- Testy komputera, magazynu i serwisu przechodzą: `./mvnw -B test -Dtest="RandomOpponentTests,MatchStoreTests,MatchServiceTests"`
- Pełny build backendu przechodzi: `./mvnw -B verify`

**Implementation Note**: Po przejściu bramek automatycznych zatrzymaj się na ręczne potwierdzenie przed kolejną fazą.

---

## Phase 4: API ruchów i rozszerzony widok

### Overview

Trzy endpointy ruchów, mapowanie błędów na 400, 404 i 409 oraz `MatchView` z widokiem stołu, wydarzeń, wyniku i zlokalizowanego logu.

### Changes Required:

#### 1. Endpointy ruchów

**File**: `src/main/java/com/example/itcardbrawl/api/MatchController.java`

**Intent**: Wystawić ruchy gracza. Start i odczyt przechodzą przez `MatchService`.

**Contract** (wszystkie pod `/api`, z CSRF jak `POST /api/matches`, odpowiedź `200 MatchView`):
- `POST /api/matches/{matchId}/play` z body `{ "turn": n, "instanceId": i }`.
- `POST /api/matches/{matchId}/attack` z body `{ "turn": n, "attackerId": a, "targetId": t | null }`.
- `POST /api/matches/{matchId}/end-turn` z body `{ "turn": n }`.
- Błędy:
  - brak bieżącej partii daje 404;
  - `StaleMoveException` daje 409;
  - `IllegalMoveException` daje 400 z komunikatem;
  - brak lub błędne body daje 400.
- `POST /api/matches` i `GET /api/matches/current` zachowują obecny kontrakt (rozszerzony widok).

#### 2. Rozszerzony widok

**File**: `src/main/java/com/example/itcardbrawl/api/MatchView.java`

**Intent**: Dać SPA wszystko do narysowania stołu i wyniku bez logiki gry po stronie przeglądarki.

**Contract**:
- `MatchView` dostaje `status` (`IN_PROGRESS` / `FINISHED`), `result: { winner, reason } | null` i `log: [ { side, text } ]`.
- `PlayerView` i `OpponentView` dostają:
  - `board: [ { instanceId, cardId, name, attack, health, damage, canAttack, ability: { keyword, text } | null } ]`;
  - `activeEvents: [ { instanceId, cardId, name, turnsLeft } ]`;
  - `graveyardCount`.
- `HandCardView` dostaje `attack`, `health`, `ability` (jak wyżej) i `duration` (`null` dla postaci).
- Ręka i kolejność talii przeciwnika nadal nie opuszczają serwera.

#### 3. Lokalizacja logu

**File**: nowe `src/main/java/com/example/itcardbrawl/api/MatchLogFormatter.java`

**Intent**: Zdania logu po polsku i angielsku po stronie backendu (decyzja użytkownika), z perspektywy gracza.

**Contract**: `String format(MatchEvent, Language)`, przełącznik po sealed interface (kompletny przy kompilacji). Strona gracza to „Ty” / „You”, strona komputera to „Przeciwnik” / „Opponent”. Nazwy kart pochodzą z katalogu w języku żądania. `CardDrawn` przeciwnika nie ujawnia karty. Przykłady:
- „Przeciwnik zagrywa: Tech Lead” / „Opponent plays: Tech Lead”
- „Tech Lead atakuje Twój stres: +8 (24/30)” / „Tech Lead attacks your stress: +8 (24/30)”

#### 4. Testy

**File**: `src/test/java/com/example/itcardbrawl/api/MatchControllerTests.java`, nowe `MatchLogFormatterTests.java`

**Intent**: Pokryć kontrakt HTTP i język logu.

**Contract**:
- **Ruchy:** `play` poprawnej karty z ręki zwraca 200 z kartą na `board` albo w `activeEvents`/cmentarzu; nieopłacalna karta daje 400; zły `turn` daje 409; obcy `matchId` daje 409; ruch bez partii daje 404; ruch bez CSRF daje 403; `end-turn` zwraca widok z aktywnym `PLAYER` i wpisami przeciwnika w logu (albo `FINISHED`).
- **Widok:** nie zawiera ręki przeciwnika ani kart dobieranych przez przeciwnika; ten sam stan czytany z `Accept-Language: en` i `pl` daje log w obu językach.
- **Format logu:** dla każdego typu zdarzenia daje niepusty tekst w pl i en.
- Sterowanie stanem w testach kontrolera: wzór `newPlayer()` + `postMatches()` (`MatchControllerTests.java:52-74`). Scenariusze zależne od konkretnej ręki sprawdzają się na rzeczywistej odpowiedzi (wybór karty z `hand` o `cost <= resource.current`, gdy aktywny jest `PLAYER`).

### Success Criteria:

#### Automated Verification:

- Testy API i logu przechodzą: `./mvnw -B test -Dtest="MatchControllerTests,MatchLogFormatterTests"`
- Trasy backendu są pod `/api`: `./mvnw -B test -Dtest=BackendRoutesTests`
- Pełny build backendu przechodzi: `./mvnw -B verify`

**Implementation Note**: Po przejściu bramek automatycznych zatrzymaj się na ręczne potwierdzenie przed kolejną fazą.

---

## Phase 5: Pętla gry we froncie

### Overview

Stół w `/match` staje się grywalny: przyciski zagrania, ataku i końca tury, strefy stołu i aktywnych wydarzeń, log i blok wyniku z powrotem do wyboru frakcji. Wszystkie teksty idą przez i18n.

### Changes Required:

#### 1. Modele i serwis

**File**: `frontend/src/app/game/match.ts`, `frontend/src/app/game/game.service.ts`

**Intent**: Odwzorować rozszerzony kontrakt z Fazy 4 i dodać wywołania ruchów.

**Contract**:
- Typy `BoardCharacter`, `ActiveEvent`, `AbilityView`, `LogEntry`, `MatchResult`, `MatchStatus`, `AbilityKeyword`. Nowe pola w `HandCard`, `PlayerView`, `OpponentView` i `MatchView` zgodnie z Fazą 4.
- Nowe metody serwisu:
  - `playCard(matchId, turn, instanceId)`;
  - `attack(matchId, turn, attackerId, targetId | null)`;
  - `endTurn(matchId, turn)`.

#### 2. Komponent stołu

**File**: `frontend/src/app/match-table/match-table.ts`, `.html`, `.css`

**Intent**: Gracz wykonuje ruchy. Przycisk jest nieaktywny tylko dla wygody, bo serwer i tak waliduje.

**Contract**:
- **Sygnały:** `pending` (wszystkie przyciski nieaktywne w trakcie żądania), `selectedAttacker: number | null`, `error` rozszerzony o `'moveRejected'` (400) i `'moveFailed'` (inne). Błąd 409 odświeża partię przez `loadMatch()` bez komunikatu. Odpowiedź ruchu ustawia `match`.
- **Ręka:** każda karta ma przycisk „Zagraj”. Jest aktywny, gdy tura gracza, `cost <= resource.current`, partia trwa i, dla postaci, stół ma mniej niż 5 kart. Karta pokazuje atak/HP, tekst zdolności albo czas trwania wydarzenia.
- **Stół gracza:** postacie z atakiem i HP (`health - damage`), tekstem zdolności i przyciskiem „Atakuj” dla `canAttack`. Kliknięcie wybiera atakującego.
- **Stół przeciwnika:** po wybraniu atakującego postacie przeciwnika mają przycisk „Cel”, a strefa przeciwnika przycisk „Atakuj stres”. Jeśli przeciwnik ma postać z `GUARD`, aktywne są tylko cele z Ochroną.
- **Aktywne wydarzenia:** lista obu stron z `turnsLeft`. Cmentarz jako licznik.
- **Pasek tury:** przycisk „Zakończ turę” przy `p.turn`.
- **Log:** lista `log` w kolejności chronologicznej, przewijana. Stres pokazuje rzeczywistą wartość (np. 33/30).
- **Wynik:** gdy `status === 'FINISHED'`, blok wyniku („Wygrana” / „Przegrana” i przyczyna: limit stresu albo pusta talia) z linkiem „Zagraj ponownie” → `/new-match`. Przyciski ruchów są ukryte.

#### 3. Teksty UI

**File**: `frontend/src/app/i18n/messages.ts`, `pl.ts`, `en.ts`

**Intent**: Zasada repo: żadnego tekstu UI w kodzie.

**Contract**: Nowe klucze w `matchTable`:
- przyciski: `play`, `attack`, `target`, `attackStress`, `endTurn`, `cancelAttack`;
- etykiety: `board`, `activeEvents`, `graveyard`, `log`, `attackHealth(a, h)`, `duration(n)`, `turnsLeft(n)`;
- błędy: `moveRejected`, `moveFailed`;
- wynik: `resultWin`, `resultLose`, `reason: Record<EndReason, string>`, `playAgain`.

Obie wersje językowe są kompletne, co wymusza typ `Messages`.

#### 4. Testy

**File**: `frontend/src/app/match-table/match-table.spec.ts`, `frontend/src/app/game/` (spec serwisu, jeśli istnieje wzór)

**Intent**: Pokryć interakcje w istniejącej uprzęży (`HttpTestingController`, polski locale, `verify()` w `afterEach`).

**Contract**: Przypadki:
- Klik „Zagraj” wysyła `POST /api/matches/{id}/play` z `{turn, instanceId}` i renderuje odpowiedź (karta na stole).
- „Zagraj” nieaktywne dla za drogiej karty i w turze przeciwnika.
- Atak: wybór atakującego, potem „Atakuj stres” wysyła `targetId: null`; przy `GUARD` u przeciwnika przycisk stresu jest nieaktywny.
- „Zakończ turę” wysyła `POST .../end-turn`.
- 409 wywołuje ponowne `GET /api/matches/current`; 400 pokazuje `moveRejected`.
- `FINISHED` pokazuje wynik i link `/new-match`, bez przycisków ruchów.
- Log renderuje wpisy z odpowiedzi.
- W trakcie żądania przyciski są nieaktywne.
- Wynik w języku angielskim po przełączeniu.

Fikstury rozszerzone o nowe pola.

### Success Criteria:

#### Automated Verification:

- Testy frontendu przechodzą: `cd frontend && npx ng test --watch=false`
- Build produkcyjny frontendu przechodzi: `cd frontend && npm run build`
- Pełny build backendu przechodzi: `./mvnw -B verify`

#### Manual Verification:

- Pełna partia od wyboru frakcji do wyniku (`npm start` + `./mvnw spring-boot:run`): zagranie postaci, atak w postać i w stres, koniec tury, widoczne ruchy komputera w logu, wynik i „Zagraj ponownie” → `/new-match`
- Partia, w której zaczyna komputer, otwiera się z jego ruchami w logu i turą gracza
- Ochrona przeciwnika blokuje atak w stres; Szarża atakuje od razu; obrażenia znikają po turze
- Przełączenie PL/EN w trakcie partii zmienia język logu i etykiet bez utraty stanu
- Podwójny klik „Zakończ turę” nie zabiera graczowi kolejnej tury
- Partia mieści się mniej więcej w 15 minutach (NFR), z uwagą o balansie do S-06

**Implementation Note**: Po przejściu bramek automatycznych zatrzymaj się na ręczne potwierdzenie przed commitem.

---

## Testing Strategy

### Unit Tests:

- Katalog: każda nowa reguła walidacji i wartości prawdziwego katalogu (Faza 1).
- Silnik: każda reguła na ręcznie zbudowanym stanie, w tym przypadki brzegowe 28 + 5 → 33, szósta postać, pełna ręka, pusta talia, Ochrona, Overtime kończący partię (Faza 2).
- Komputer: 50 partii komputer kontra komputer na ziarnach kończy się wynikiem bez wyjątku (Faza 3).
- Magazyn: współbieżne `update` bez utraty zmian (Faza 3).

### Integration Tests:

- MockMvc: ruchy z CSRF, kody 400, 403, 404 i 409, ukrycie ręki przeciwnika, log w obu językach (Faza 4).
- Vitest: interakcje stołu i wynik (Faza 5).

### Manual Testing Steps:

1. Zaloguj się, wybierz frakcję, rozegraj partię do wyniku.
2. Sprawdź partię z zaczynającym komputerem.
3. Przełącz język w trakcie partii.
4. Kliknij dwa razy szybko „Zakończ turę”.
5. Zagraj ponownie przez link wyniku.

## Performance Considerations

Log całej partii rośnie przez całą grę, szacunkowo do kilkuset wpisów przy talii 30 kart, i jest wysyłany przy każdej odpowiedzi. To akceptowalne w skali `small` z roadmapy. Tura komputera wykonuje się wewnątrz `computeIfPresent`. Jest czysta i ograniczona bezpiecznikiem 100 ruchów, więc nie blokuje mapy na długo.

## Migration Notes

Partie trzymane są tylko w pamięci, więc stare partie znikają przy restarcie po wdrożeniu. Nie trzeba migracji danych.

## References

- Research: `context/changes/first-full-match/research.md`
- Plan S-02: `context/archive/2026-11-19-match-setup-table/plan.md`
- F3: `context/archive/2026-11-19-match-setup-table/follow-ups/review-fixes.md`
- Wzorce: `MatchFactory.java:30-65`, `MatchFactoryTests.java:22-39`, `MatchControllerTests.java:52-74`, `new-match.ts:28-37,72-91`

## Progress

> Convention: `- [ ]` pending, `- [x]` done. Append ` — <commit sha>` when a step lands. Do not rename step titles. See `references/progress-format.md`.

### Phase 1: Statystyki i zdolności kart w katalogu

#### Automated

- [x] 1.1 Testy katalogu przechodzą: `./mvnw -B test -Dtest=CardCatalogTests` — 2a7305d
- [x] 1.2 Pełny build backendu przechodzi: `./mvnw -B verify` — 2a7305d

#### Manual

- [x] 1.3 Przegląd `factions.json`: wartości zgadzają się z tabelą z planu, a teksty zdolności pl/en czytają się naturalnie — 2a7305d

### Phase 2: Silnik reguł partii

#### Automated

- [x] 2.1 Testy silnika przechodzą: `./mvnw -B test -Dtest=MatchEngineTests` — 0e6830f
- [x] 2.2 Testy fabryki nadal przechodzą: `./mvnw -B test -Dtest=MatchFactoryTests` — 0e6830f
- [x] 2.3 Pełny build backendu przechodzi: `./mvnw -B verify` — 0e6830f

### Phase 3: Komputer i atomowy magazyn partii

#### Automated

- [x] 3.1 Testy komputera, magazynu i serwisu przechodzą: `./mvnw -B test -Dtest="RandomOpponentTests,MatchStoreTests,MatchServiceTests"`
- [x] 3.2 Pełny build backendu przechodzi: `./mvnw -B verify`

### Phase 4: API ruchów i rozszerzony widok

#### Automated

- [ ] 4.1 Testy API i logu przechodzą: `./mvnw -B test -Dtest="MatchControllerTests,MatchLogFormatterTests"`
- [ ] 4.2 Trasy backendu są pod `/api`: `./mvnw -B test -Dtest=BackendRoutesTests`
- [ ] 4.3 Pełny build backendu przechodzi: `./mvnw -B verify`

### Phase 5: Pętla gry we froncie

#### Automated

- [ ] 5.1 Testy frontendu przechodzą: `cd frontend && npx ng test --watch=false`
- [ ] 5.2 Build produkcyjny frontendu przechodzi: `cd frontend && npm run build`
- [ ] 5.3 Pełny build backendu przechodzi: `./mvnw -B verify`

#### Manual

- [ ] 5.4 Pełna partia od wyboru frakcji do wyniku (`npm start` + `./mvnw spring-boot:run`): zagranie postaci, atak w postać i w stres, koniec tury, widoczne ruchy komputera w logu, wynik i „Zagraj ponownie” → `/new-match`
- [ ] 5.5 Partia, w której zaczyna komputer, otwiera się z jego ruchami w logu i turą gracza
- [ ] 5.6 Ochrona przeciwnika blokuje atak w stres; Szarża atakuje od razu; obrażenia znikają po turze
- [ ] 5.7 Przełączenie PL/EN w trakcie partii zmienia język logu i etykiet bez utraty stanu
- [ ] 5.8 Podwójny klik „Zakończ turę” nie zabiera graczowi kolejnej tury
- [ ] 5.9 Partia mieści się mniej więcej w 15 minutach (NFR), z uwagą o balansie do S-06
