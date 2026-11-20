# Karty wydarzeń o przewidywalnych efektach (event-cards) — plan implementacji

## Overview

Wydarzenia dostają prawdziwe efekty. Natychmiastowe (stres, obrażenia, leczenie, dobieranie, zasób) działają raz, przy zagraniu. Trwające (atak/HP, Ochrona/Szarża, pułapka) działają przez `duration` tur. Gracz wybiera cel karty, a komputer zagrywa te same karty przez ten sam silnik. Opis efektu jest generowany po polsku i po angielsku z danych karty, więc gracz wie, co karta zrobi, zanim ją zagra (PRD FR-005, roadmapa S-04).

## Current State Analysis

Model wydarzeń istnieje od S-03, ale efektów brak. Zagranie płaci koszt, loguje `CardPlayed` i odkłada kartę na cmentarz albo do `activeEvents`; stan gry się nie zmienia (`MatchEngine.java:109-117`). Każda z 2 talii ma 15 wydarzeń na 30 kart. Pełne ustalenia, kotwice i decyzje D1–D18: `context/changes/event-cards/research.md`.

- Zdolności postaci to gotowy wzorzec: słowo kluczowe + liczba, walidacja w katalogu, tekst `describe()` pl/en, `AbilityView` z gotowym tekstem (`AbilityKeyword.java:7-57`, `CardCatalog.java:150-163`, `MatchView.java:148-155`).
- Statystyki postaci pochodzą wyłącznie z karty: `BoardCharacter.attack()`, `remainingHealth()`, `has()` (`BoardCharacter.java:9-21`).
- `BoardCharacter.canAttack` łączy dwa stany: „zagrana w tej turze” i „już atakowała” (`MatchEngine.java:120-121,156,170,252`).
- Widoki API powstają bez silnika (`MatchView.java:36-47`). SPA powiela zasady zagrania i ataku: `canPlay` (`match-table.ts:105-114`), `opponentGuarded` i `isGuard` czytające tylko zdolność karty.
- Dobieranie jest wpisane w `startTurn` (`MatchEngine.java:235-248`).
- Komputer losuje jednostajnie z `legalMoves` (`RandomOpponent.java:44-57`).

## Desired End State

- Każde wydarzenie w `factions.json` ma listę `effects`; katalog odrzuca niepoprawne kombinacje przy starcie.
- Gracz klika „Zagraj” przy wydarzeniu: bez celu karta działa od razu; z celem SPA podświetla tylko dozwolone cele (przysłane przez serwer), a gracz wybiera postać albo gracza.
- Efekty natychmiastowe zmieniają stres, obrażenia, rękę i zasób; trwające zmieniają atak, HP i słowa kluczowe postaci albo je więżą, dopóki wydarzenie jest aktywne.
- Każdy efekt ma wpis w logu, a każda strona ma miejsce „Ostatnio zagrane” z nazwą i opisem efektów ostatniego wydarzenia — także w odtwarzaniu tury komputera.
- Komputer zagrywa wydarzenia z celami (najpierw akcja, potem cel).
- Weryfikacja: `./mvnw -B verify`, `cd frontend && npx ng test --watch=false`, `cd frontend && npm run build` oraz ręczne rozegranie partii obiema frakcjami.

### Key Discoveries:

- Kolejność końca i początku tury (`MatchEngine.java:189-253`): czyszczenie obrażeń obu stron na koniec tury, potem tick wydarzeń nowej strony, dobranie, zasób, gotowość. Wygaśnięcie wzmocnienia HP następuje przy `damage = 0`, więc samo wygaśnięcie nie pokona postaci o bazowym HP ≥ 1.
- `addStress` od razu kończy partię przy `>= 30` (`MatchEngine.java:310-321`); efekty muszą sprawdzać `state.finished()` między krokami, jak Overtime (`:198-200`).
- `MatchLogFormatter` ma wyczerpujący `switch` po `MatchEvent` (`MatchLogFormatter.java:31-70`), więc nowy rekord logu nie skompiluje się bez zdania; `MatchLogFormatterTests.everyEvent()` trzeba uzupełnić ręcznie.
- Talie mają dokładnie 30 kart, a neutralne karty wchodzą po 1 kopii (`CardCatalog.java:246-247`, `factions.json` sekcja `decks`).
- Lessons: każdy endpoint pod `/api/**` — plan nie dodaje tras, zmienia tylko ciało `POST /api/matches/{matchId}/play`.

## What We're NOT Doing

- Sensowna strategia komputera (S-05). Komputer nadal gra losowo i może leczyć gracza, ranić własne postacie albo więzić własną postać.
- Docelowy balans i ostateczna treść kart (S-06). Wartości z Fazy 1 są szkicem do zatwierdzenia.
- Efekty natychmiastowe powtarzane co turę na kartach trwających (wybrano „rozdzielone wg rodzaju”).
- Osobny cel dla każdego efektu karty — jeden cel na kartę.
- Pułapka na kartę w ręce — pułapka celuje tylko w postać na stole.
- Nadawanie przez wydarzenia słów kluczowych innych niż Ochrona i Szarża.
- Zmiana resetu obrażeń na koniec tury (S-03) — leczenie postaci działa tylko w bieżącej turze.
- Animacje, podgląd efektu przed potwierdzeniem celu, cofanie wyboru celu po wysłaniu.

## Implementation Approach

Kolejność warstw jak w S-03: dane katalogu → domena (natychmiastowe) → domena (trwające) → API → UI. Każda warstwa jest testowalna przed następną.

**Model efektu.** `Effect(kind, scope, amount)` na karcie wydarzenia, lista `effects`. Rodzaje dzielą się na natychmiastowe i modyfikatory. Zasięg określa, w co efekt trafia, względem właściciela wydarzenia. Dozwolone kombinacje:

| Rodzaj | Typ | Dozwolone zasięgi | `amount` |
|---|---|---|---|
| `STRESS` | natychmiastowy | `OPPONENT` | 1–10 |
| `DAMAGE` | natychmiastowy | `TARGET` (postać lub gracz), `ENEMY_BOARD`, `ENEMY_ALL` (postacie + stres przeciwnika), `ALL_BOARDS` | 1–10 |
| `HEAL` | natychmiastowy | `TARGET` (postać lub gracz), `OWN_BOARD`, `OWN_ALL` (postacie + własny stres), `ALL_BOARDS` | 1–10 |
| `DRAW` | natychmiastowy | `SELF` | 1–5 |
| `RESOURCE` | natychmiastowy | `SELF` | 1–10 |
| `RESOURCE_MAX` | natychmiastowy | `SELF` | 1–10 |
| `ATTACK` | modyfikator | `TARGET` (postać), `OWN_BOARD`, `ENEMY_BOARD`, `ALL_BOARDS` | −10…10, ≠ 0 |
| `HEALTH` | modyfikator | `TARGET` (postać), `OWN_BOARD`, `ENEMY_BOARD`, `ALL_BOARDS` | −10…10, ≠ 0 |
| `GUARD`, `RUSH` | modyfikator | `TARGET` (postać), `OWN_BOARD`, `ENEMY_BOARD`, `ALL_BOARDS` | 0 |
| `TRAP` | modyfikator | `TARGET` (postać dowolnej strony) | 0 |

Skutki rodzajów: obrażenia gracza = +N stresu; leczenie gracza = −N stresu, nie poniżej 0; leczenie postaci = `damage − N`, nie poniżej 0; `RESOURCE` = +N bieżącego zasobu (maks. `RESOURCE_CAP`); `RESOURCE_MAX` = +N maksymalnego zasobu (maks. `RESOURCE_CAP`), bez zmiany bieżącego.

**Reguły karty.** Co najmniej jeden efekt. `duration == 0` ⇒ tylko efekty natychmiastowe. `duration > 0` ⇒ co najmniej jeden modyfikator (natychmiastowe działają raz przy zagraniu, modyfikatory przez czas trwania). Jeden cel na kartę: dozwolone cele to część wspólna celów wszystkich efektów `TARGET` (np. `DAMAGE` + `TRAP` ⇒ tylko postacie). Uwięziona postać nigdy nie jest dozwolonym celem. Brak dozwolonego celu ⇒ karta niegrywalna.

**Modyfikatory jako aura.** Statystyki efektywne liczy jedna funkcja domenowa z aktywnych wydarzeń obu stron: modyfikatory `OWN_BOARD`/`ENEMY_BOARD`/`ALL_BOARDS` obejmują też postacie zagrane później; `TARGET` wiąże się z `instanceId` postaci zapisanym w `ActiveEvent`. Modyfikatory sumują się. Atak efektywny nie spada poniżej 0. Postać z efektywnym HP − obrażenia ≤ 0 jest pokonana od razu.

**Uwięzienie.** Postać nie atakuje, nie jest celem ataku ani wydarzenia, a jej Ochrona (własna lub nadana) nie działa. Efekty obszarowe trafiają ją normalnie.

**Legalność z serwera.** Widok wysyła dla każdej karty na ręce `playable` i `targets`, a dla każdej postaci gracza `attackTargets`, wyliczone z `engine.legalMoves`. SPA przestaje liczyć zasady sama.

## Critical Implementation Details

- **Gotowość do ataku.** `BoardCharacter.canAttack` trzeba rozdzielić na „weszła w tej turze” i „już atakowała”, bo nadana Szarża ma pozwolić zaatakować postaci zagranej w tej turze, ale nie tej, która już atakowała. Efektywne `canAttack` = nie atakowała ∧ (nie weszła w tej turze ∨ ma efektywną Szarżę) ∧ nie jest uwięziona.
- **Kolejność efektów karty.** Efekty wykonują się w kolejności listy. Po każdym: przegląd pokonanych postaci (najpierw strona przeciwnika zagrywającego, potem własna, w kolejności stołu), potem sprawdzenie `finished()` — koniec partii przerywa resztę efektów. Efekt celowany, którego cel zginął wcześniej w tej samej karcie, jest pomijany bez wpisu w logu. Ten sam przegląd pokonanych uruchamia się po rozwiązaniu zdolności ON_PLAY nowo zagranej postaci (aura −HP może ją od razu pokonać) i po wygaśnięciu wydarzenia. Zdolność ON_PLAY_STRESS/HEAL rozwiązuje się po wejściu postaci na stół, przed tym przeglądem; dodaj test tej kolejności.
- **Dobieranie przez efekt.** Wydzielone dobieranie jednej karty, wspólne dla `startTurn` i `DRAW`: pusta talia ⇒ `finish(EMPTY_DECK)` dobierającego i stop; pełna ręka ⇒ `CardBurned`. Wpisy `CardDrawn` przeciwnika nie ujawniają karty (`MatchLogFormatter.java:36-38`).

---

## Faza 1: Katalog efektów wydarzeń

### Overview

Karta wydarzenia dostaje listę efektów, katalog je waliduje, a opis efektu jest generowany pl/en. `factions.json` dostaje efekty 16 wydarzeń (z nową kartą neutralną) i zmienione talie. Silnik jeszcze z efektów nie korzysta.

### Changes Required:

#### 1. Model efektu

**File**: nowe `src/main/java/com/example/itcardbrawl/game/Effect.java`, `EffectKind.java`, `EffectScope.java`; `Card.java`

**Intent**: Opisać efekt jako dane (rodzaj, zasięg, liczba) wzorem `Ability`/`AbilityKeyword`, z tekstem dla gracza generowanym z danych. Nazwy bez słowa „Event”, żeby nie mylić z `MatchEvent`.

**Contract**: `record Effect(EffectKind kind, EffectScope scope, int amount)` z `LocalizedText describe()`; `EffectKind` z flagą natychmiastowy/modyfikator i zakresem `amount` z tabeli w Implementation Approach; `EffectScope { TARGET, OPPONENT, SELF, OWN_BOARD, ENEMY_BOARD, OWN_ALL, ENEMY_ALL, ALL_BOARDS }`. `Card` dostaje ostatnie pole `List<Effect> effects` (`null` dla postaci). JSON: `"effects": [{ "kind": "DAMAGE", "scope": "TARGET", "amount": 3 }]`; pominięte `amount` = 0 jak w `Ability.fromJson`. `describe()` zwraca zdania typu „Zadaj 3 obrażenia wybranej postaci lub graczowi” / „Deal 3 damage to a chosen character or player”, „Postacie przeciwnika: −1 ataku” / „Enemy characters: −1 attack”. Polski opis dobiera poprawną formę liczebnika (np. 1/3/5) przez wspólny helper testowany osobno.

#### 2. Walidacja katalogu

**File**: `src/main/java/com/example/itcardbrawl/game/CardCatalog.java`

**Intent**: Niepoprawna karta zatrzymuje start aplikacji, jak dotąd.

**Contract**: `validateEvent` dodaje błędy dla: braku/pustej listy `effects`; efektu bez `kind`; zasięgu spoza tabeli; `amount` spoza zakresu; modyfikatora przy `duration 0`; braku modyfikatora przy `duration > 0`. `validateCharacter` odrzuca pole `effects`. Komunikaty po polsku, w stylu istniejących.

#### 3. Treść kart i talie

**File**: `src/main/resources/game/factions.json`

**Intent**: Dać efekty wszystkim wydarzeniom (szkic do zatwierdzenia; balans w S-06) i dodać kartę z PRD. Koszty i czasy trwania istniejących kart bez zmian, poza nową kartą.

**Contract**:

| Karta | Koszt | Czas | Efekty |
|---|---|---|---|
| `dev-stackoverflow` | 2 | 0 | `DRAW SELF 2` |
| `dev-works-on-my-machine` | 3 | 0 | `HEAL TARGET 3` |
| `dev-friday-hotfix` | 4 | 2 | `ATTACK OWN_BOARD +2` |
| `dev-force-push` | 6 | 0 | `DAMAGE TARGET 5` |
| `dev-legacy-refactor` | 8 | 2 | `DAMAGE ENEMY_BOARD 2`, `ATTACK ENEMY_BOARD −2` |
| `tst-vague-bug-report` | 2 | 0 | `STRESS OPPONENT 2` |
| `tst-edge-case` | 3 | 0 | `DAMAGE TARGET 3` |
| `tst-regression` | 4 | 2 | `HEALTH OWN_BOARD +2` |
| `tst-flaky-test` | 6 | 3 | `TRAP TARGET` |
| `tst-production-bug` | 8 | 0 | `DAMAGE ENEMY_ALL 3` |
| `neu-coffee` | 1 | 0 | `RESOURCE SELF 2` |
| `neu-daily-standup` | 2 | 0 | `STRESS OPPONENT 1`, `DRAW SELF 1` |
| `neu-sprint-planning` | 4 | 2 | `RESOURCE_MAX SELF 1`, `RUSH OWN_BOARD` |
| `neu-meeting` | 7 | 0 | `DAMAGE ALL_BOARDS 4` |
| `neu-deadline` | 9 | 0 | `STRESS OPPONENT 6` |
| `neu-requirements-change` (nowa) | 5 | 2 | `ATTACK ENEMY_BOARD −2`, `GUARD OWN_BOARD` |

Nowa karta: nazwa „Nagła zmiana założeń projektowych” / „Sudden change of project assumptions”, tekst „Klient w końcu przeczytał maila z zeszłego kwartału.” / „The client finally read last quarter's email.” W obu taliach `neu-requirements-change` (1 kopia) zastępuje `neu-daily-standup` (karta zostaje w katalogu poza taliami), więc każda talia nadal ma 30 kart, w tym 15 wydarzeń.

#### 4. Testy katalogu

**File**: `src/test/java/com/example/itcardbrawl/game/CardCatalogTests.java` (+ istniejące testy budujące `Card` — nowe pole w konstruktorze)

**Intent**: Pokryć każdą nową regułę walidacji i prawdziwy katalog.

**Contract**: przypadki: brak `effects`, zły zasięg, `amount` spoza zakresu (także 0 dla `ATTACK`), modyfikator przy `duration 0`, brak modyfikatora przy `duration > 0`, `effects` na postaci; na prawdziwym katalogu: każde wydarzenie ma efekty, obie talie po 30 kart. Testy `describe()` dla każdego rodzaju w pl i en (niepuste i różne); polskie formy liczebników dla 1/3/5.

### Success Criteria:

#### Automated Verification:

- Testy backendu przechodzą: `./mvnw -B verify`
- `CardCatalogTests` pokrywa każdą nową regułę walidacji efektów i prawdziwy katalog (16 wydarzeń z efektami, talie po 30 kart)

#### Manual Verification:

- Przegląd tabeli efektów 16 wydarzeń: wartości do przyjęcia jako szkic, opisy generowane pl/en czytelne

**Implementation Note**: Po fazie i zielonej weryfikacji automatycznej zatrzymaj się na ręczne potwierdzenie przed Fazą 2.

---

## Faza 2: Efekty natychmiastowe i cele w silniku

### Overview

Zagranie wydarzenia wykonuje efekty natychmiastowe z wybranym celem. `legalMoves` wylicza cele, log ma wpis na efekt, komputer wybiera najpierw akcję, potem cel.

### Changes Required:

#### 1. Ruch z celem

**File**: `src/main/java/com/example/itcardbrawl/game/Move.java`, nowe `Target.java`

**Intent**: Cel wydarzenia musi rozróżniać postać od gracza (leczenie celuje też we własny stres).

**Contract**: `sealed interface Target { record Character(int instanceId); record Player(Match.Side side); }`; `Move.PlayCard(int instanceId, Target target)` — `target == null` dla kart bez efektu `TARGET` i dla postaci. `Attack` bez zmian.

#### 2. Wykonanie efektów i legalność

**File**: `src/main/java/com/example/itcardbrawl/game/MatchEngine.java`

**Intent**: Gałąź wydarzenia w `playCard` wykonuje efekty natychmiastowe; `legalMoves` emituje jeden `PlayCard` na dozwolony cel; dobieranie i przegląd pokonanych są wspólne.

**Contract**:
- `playCard`: dla wydarzenia z celem — `IllegalMoveException`, gdy cel brakuje albo jest niedozwolony; dla karty bez celu — gdy cel podano. Po opłacie i `CardPlayed`: efekty natychmiastowe w kolejności listy (Critical Implementation Details), potem karta na cmentarz (`duration 0`) albo do `activeEvents`.
- Cele `TARGET`: `DAMAGE`/`HEAL` — każda postać na obu stołach i obaj gracze; modyfikatory (`ATTACK`, `HEALTH`, `GUARD`, `RUSH`, `TRAP`) — każda postać na obu stołach; część wspólna dla karty z kilkoma efektami `TARGET`; Ochrona nie ogranicza. Modyfikatory w tej fazie tylko zapisują wydarzenie w `activeEvents` (działanie i wykluczanie uwięzionych to Faza 3).
- `legalMoves`: wydarzenie bez celu — jeden `PlayCard(id, null)`; z celem — po jednym na dozwolony cel; brak celów — brak ruchu.
- Wydzielone dobieranie jednej karty używane przez `startTurn` i `DRAW`.
- Wydzielony przegląd pokonanych postaci, używany też przez `attack` zamiast obecnego kodu inline (`MatchEngine.java:177-186`), bez zmiany kolejności wpisów w logu ataku.

#### 3. Log efektów

**File**: `src/main/java/com/example/itcardbrawl/game/MatchEvent.java`, `src/main/java/com/example/itcardbrawl/api/MatchLogFormatter.java`

**Intent**: Każdy efekt ma czytelny wpis, osobno od wpisów skutków (`StressChanged`, `CharacterDefeated`, `CardDrawn`).

**Contract**: `record EffectApplied(Match.Side side, CardInstance card, Effect effect, CardInstance targetCharacter, Match.Side targetPlayer)` (jedno z pól celu albo oba `null`). Zdania pl/en z perspektywy gracza: „Termin nie do przesunięcia: +6 stresu przeciwnika”, „Przypadek brzegowy: 3 obrażeń — Senior”, „Twój stres −3”. `everyEvent()` w `MatchLogFormatterTests` uzupełnione.

#### 4. Komputer: akcja, potem cel

**File**: `src/main/java/com/example/itcardbrawl/game/RandomOpponent.java`

**Intent**: Karta z wieloma celami nie może być zagrywana częściej tylko dlatego, że ma wiele pozycji w `legalMoves`.

**Contract**: grupuje `legalMoves` po akcji (`PlayCard.instanceId` / `Attack.attackerId`), losuje akcję jednostajnie, potem ruch w niej jednostajnie. To celowo zmienia rozkład ataków względem S-03: każdy atakujący dostaje równą szansę niezależnie od liczby celów; zaktualizuj deterministyczne oczekiwania w `RandomOpponentTests`. Bezpiecznik `MAX_MOVES_PER_TURN` bez zmian.

#### 5. Testy domeny

**File**: `src/test/java/com/example/itcardbrawl/game/MatchEngineTests.java`, `RandomOpponentTests.java`, `MatchServiceTests.java`

**Contract**: każdy rodzaj natychmiastowy i każdy zasięg; obrażenia własnego gracza do 30 = przegrana zagrywającego; leczenie stresu i obrażeń nie poniżej 0; `DRAW` z pustej talii = przegrana `EMPTY_DECK` i przerwanie reszty efektów; `DRAW` przy pełnej ręce = spalenie; `RESOURCE`/`RESOURCE_MAX` z limitem 10; pokonanie postaci efektem obszarowym; zły/brakujący/zbędny cel = `IllegalMoveException`; `legalMoves` z jednym ruchem na cel i bez ruchu przy braku celów; komputer losuje akcję, potem cel (deterministyczny `RandomGenerator`); testy aktualizują oczekiwane ataki po zmianie rozkładu.

### Success Criteria:

#### Automated Verification:

- Testy backendu przechodzą: `./mvnw -B verify`
- `MatchEngineTests` pokrywa każdy rodzaj i zasięg efektu natychmiastowego, przegraną przez efekt (stres własny, dobieranie z pustej talii) i walidację celu
- `RandomOpponentTests` pokrywa wybór najpierw akcji, potem celu

#### Manual Verification:

Brak kryteriów ręcznych — zmiana niewidoczna w UI do Fazy 4 i 5.

**Implementation Note**: Po fazie i zielonej weryfikacji automatycznej zatrzymaj się na ręczne potwierdzenie przed Fazą 3.

---

## Faza 3: Efekty trwające

### Overview

Modyfikatory ataku i HP, nadana Ochrona i Szarża oraz pułapka działają, dopóki wydarzenie jest aktywne. Zasady ataku i Ochrony używają wartości efektywnych.

### Changes Required:

#### 1. Aktywne wydarzenie z celem

**File**: `src/main/java/com/example/itcardbrawl/game/ActiveEvent.java`

**Intent**: Modyfikator `TARGET` musi pamiętać, w którą postać trafił.

**Contract**: `ActiveEvent(CardInstance instance, int turnsLeft, Integer targetId)` — `instanceId` postaci-celu albo `null`. Po zniknięciu postaci ze stołu modyfikator nie działa, a wydarzenie wygasa normalnie.

#### 2. Statystyki efektywne

**File**: nowe `src/main/java/com/example/itcardbrawl/game/CharacterStats.java` (nazwa do decyzji implementującego), `BoardCharacter.java`

**Intent**: Jedno miejsce w domenie liczy atak, HP, słowa kluczowe i uwięzienie postaci z aktywnych wydarzeń obu stron; korzysta z niego silnik i widok.

**Contract**: sygnatura tylko na współdzielonych rekordach, żeby silnik (prywatny `SideState`) i widok (`MatchSide`) wołały tę samą funkcję: `CharacterStats.of(BoardCharacter character, Match.Side owner, List<ActiveEvent> playerEvents, List<ActiveEvent> opponentEvents)`. Dla postaci właściciela `owner` zwraca `attack` (≥ 0), `health`, zbiór słów kluczowych (zdolność karty ∪ nadane `GUARD`/`RUSH`), `trapped`. Zasięg modyfikatora liczony względem właściciela wydarzenia (`OWN_BOARD` wydarzenia przeciwnika = stół przeciwnika). `BoardCharacter`: `canAttack` zastąpione przez `enteredThisTurn` i `attacked` (Critical Implementation Details); `has()`/`attack()`/`remainingHealth()` nie mogą już służyć zasadom bez kontekstu wydarzeń.

#### 3. Zasady z wartościami efektywnymi

**File**: `src/main/java/com/example/itcardbrawl/game/MatchEngine.java`

**Intent**: Atak, Ochrona, cele i pokonanie używają wartości efektywnych; pułapka wyłącza postać z walki.

**Contract**:
- `attack`: obrażenia = efektywny atak; uwięziony atakujący albo cel ⇒ `IllegalMoveException`; Ochrona tylko u nieuwięzionych postaci z efektywną Ochroną.
- `legalMoves`: atakujący z efektywnym `canAttack`; cele ataku bez uwięzionych; cele wydarzeń bez uwięzionych; `TRAP` — każda nieuwięziona postać obu stron.
- `playCard` z modyfikatorami: `ActiveEvent` z `targetId` dla `TARGET`; po zagraniu wydarzenia lub postaci i po wygaśnięciu — przegląd pokonanych (HP efektywne − obrażenia ≤ 0).
- Overtime na koniec tury działa z efektywnymi słowami kluczowymi; `GUARD`/`RUSH` nadawane, `OVERTIME` nie.

#### 4. Testy domeny

**File**: `src/test/java/com/example/itcardbrawl/game/MatchEngineTests.java`

**Contract**: aura obejmuje postać zagraną później; modyfikatory się sumują; atak nie spada poniżej 0; −HP pokonuje od razu (także postać zagraną pod aurę); wygaśnięcie przywraca wartości po `duration` turach właściciela; nadana Szarża pozwala zaatakować postaci z tej tury, ale nie tej, która już atakowała; nadana Ochrona wymusza cel; uwięziona postać nie atakuje, nie jest celem ataku/wydarzenia, jej Ochrona nie działa, a obrażenia obszarowe ją trafiają; mieszana karta (natychmiastowy + modyfikator) z jednym celem.

### Success Criteria:

#### Automated Verification:

- Testy backendu przechodzą: `./mvnw -B verify`
- `MatchEngineTests` pokrywa aurę, sumowanie, dolny limit ataku, pokonanie przez −HP, wygaśnięcie, nadaną Szarżę i Ochronę oraz każdą regułę pułapki

#### Manual Verification:

Brak kryteriów ręcznych — zmiana niewidoczna w UI do Fazy 4 i 5.

**Implementation Note**: Po fazie i zielonej weryfikacji automatycznej zatrzymaj się na ręczne potwierdzenie przed Fazą 4.

---

## Faza 4: API — cele, efekty i legalność w widoku

### Overview

Endpoint zagrania przyjmuje cel. Widok wysyła opisy efektów, statystyki efektywne, uwięzienie, dozwolone cele i ostatnio zagrane wydarzenie każdej strony.

### Changes Required:

#### 1. Żądanie zagrania

**File**: `src/main/java/com/example/itcardbrawl/api/MatchController.java`

**Intent**: Przenieść wybór celu z SPA do `Move.PlayCard`.

**Contract**: `PlayRequest(int turn, int instanceId, TargetRequest target)`; `TargetRequest(String kind, Integer instanceId, Match.Side side)` — `{ "kind": "CHARACTER", "instanceId": 7 }` albo `{ "kind": "PLAYER", "side": "OPPONENT" }`; `null` = brak celu. Niepoprawny kształt celu = 400 jak inne niedozwolone ruchy. Trasa bez zmian.

#### 2. Widok

**File**: `src/main/java/com/example/itcardbrawl/api/MatchView.java`

**Intent**: SPA dostaje wszystko, czego potrzebuje do wyświetlenia i legalności, bez liczenia zasad.

**Contract**:
- `MatchView.of` dostaje legalne ruchy gracza (`engine.legalMoves(match, PLAYER)`); kroki odtwarzania dostają pustą listę (tura komputera). `MatchController` wstrzykuje `MatchEngine` i przekazuje te ruchy w `MatchController.java:116`.
- `PlayerView.of`, `OpponentView.of` i `ReplayStepView.of` dostają cały `Match` (aktywne wydarzenia obu stron dla `CharacterStats.of`), a nie pojedynczy `MatchSide`.
- `TargetView(String kind, Integer instanceId, Match.Side side)` — ten sam kształt co `TargetRequest`.
- `HandCardView` + `effects: List<EffectView(kind, scope, amount, text)>`, `requiresTarget`, `playable`, `targets: List<TargetView>`.
- `BoardCharacterView`: `attack`, `health` efektywne, `baseAttack`, `baseHealth`, `keywords` (efektywne), `trapped`, `canAttack` efektywne, `attackTargets: List<TargetView>` (dla postaci gracza; pusta u przeciwnika).
- `ActiveEventView` + `effects` (teksty) i `targetName` (nazwa postaci-celu albo `null`).
- `PlayerView`/`OpponentView` + `lastEvent: PlayedEventView(instanceId, name, effects)` — ostatni `CardPlayed` wydarzenia tej strony w logu stanu (także w krokach odtwarzania), `null` gdy brak.

#### 3. Testy API

**File**: `src/test/java/com/example/itcardbrawl/api/MatchControllerTests.java`, `MatchViewTests.java`, `MatchLogFormatterTests.java`

**Contract**: zagranie z celem `CHARACTER` i `PLAYER`; brak wymaganego celu i zbędny cel = 400; `targets`/`playable`/`attackTargets` zgodne z `legalMoves`; statystyki efektywne i `trapped` w widoku; `lastEvent` obu stron i w kroku odtwarzania; opisy efektów po `Accept-Language: en`; każde nowe zdanie logu pl i en.

### Success Criteria:

#### Automated Verification:

- Testy backendu przechodzą: `./mvnw -B verify`
- `MatchControllerTests` pokrywa zagranie z celem postaci i gracza, błędy celu (400) oraz `targets`/`playable`/`attackTargets` w odpowiedzi
- `MatchViewTests` pokrywa statystyki efektywne, `trapped`, `lastEvent` (także w kroku odtwarzania) i opisy efektów w pl i en

#### Manual Verification:

- Lokalnie po zalogowaniu odpowiedź `GET /api/matches/current` zawiera `effects`, `targets` i `lastEvent` w języku z `Accept-Language`

**Implementation Note**: Po fazie i zielonej weryfikacji automatycznej zatrzymaj się na ręczne potwierdzenie przed Fazą 5.

---

## Faza 5: Stół w SPA — wybór celu, ostatnio zagrane, opisy efektów

### Overview

Gracz wybiera cel wydarzenia na stole, widzi opis efektów na kartach i w aktywnych wydarzeniach, zmodyfikowane statystyki i uwięzienie postaci, a ostatnio zagrane wydarzenie każdej strony ma własne miejsce.

### Changes Required:

#### 1. Kontrakt i serwis

**File**: `frontend/src/app/game/match.ts`, `frontend/src/app/game/game.service.ts`

**Intent**: Lustro nowego kontraktu JSON.

**Contract**: typy `Target`, `EffectView`, `PlayedEvent`; pola z Fazy 4 w `HandCard`, `BoardCharacter`, `ActiveEvent`, `PlayerView`, `OpponentView`. `playCard(matchId, turn, instanceId, target: Target | null)` wysyła `{ turn, instanceId, target }`.

#### 2. Tryb wyboru celu i legalność

**File**: `frontend/src/app/match-table/match-table.ts`, `.html`, `.css`

**Intent**: Wybór celu wydarzenia działa jak wybór celu ataku, a dostępność przycisków pochodzi z serwera.

**Contract**:
- Sygnał wybranej karty-wydarzenia (wzajemnie wykluczający się z `selectedAttacker`). „Zagraj” przy karcie z `requiresTarget` włącza tryb; bez celu — wysyła od razu.
- W trybie: przycisk „Cel” przy każdej postaci obu stołów i przy stresie obu graczy, aktywny tylko, gdy cel jest w `card.targets`; „Anuluj” wyłącza tryb.
- Atak: cele i „Atakuj stres” aktywne wg `attackTargets` wybranej postaci; `canPlay` = `!busy() && card.playable`; przyciski „Cel” i „Anuluj” są wyłączone podczas `busy()`; usunąć `BOARD_LIMIT`, `opponentGuarded`, `isGuard`, `canTarget`.
- Karta na ręce: lista `effects[].text`; „Natychmiastowe” przy `duration 0`, „Czas trwania (tury): n” przy `duration > 0`.
- Postać: efektywny atak/HP z wyróżnieniem, gdy różnią się od bazowych; znacznik uwięzienia; nadane słowa kluczowe.
- Aktywne wydarzenie: opisy efektów i nazwa celu.
- Miejsce „Ostatnio zagrane” w strefie każdej strony z `lastEvent` (nazwa + opisy efektów); działa także w krokach odtwarzania.

#### 3. Teksty

**File**: `frontend/src/app/i18n/messages.ts`, `pl.ts`, `en.ts`

**Contract**: nowe klucze w `matchTable`: `instant`, `chooseTarget`, `cancelTarget`, `targetPlayer`/`targetOpponent` (etykiety celu-gracza), `trapped`, `lastPlayed`, `nothingPlayed`, oraz nazwy nadanych słów kluczowych, jeśli SPA je wyświetla; zawsze w obu językach.

#### 4. Testy frontu

**File**: `frontend/src/app/match-table/match-table.spec.ts`

**Contract**: fixture z nowymi polami; karta bez celu wysyła `target: null`; karta z celem włącza tryb, aktywne są tylko cele z `targets`, wybór wysyła `{ kind, instanceId }` / `{ kind, side }`, „Anuluj” wychodzi z trybu; `playable: false` blokuje „Zagraj”; `busy()` blokuje „Zagraj”, „Cel” i „Anuluj”; cele ataku wg `attackTargets`; „Natychmiastowe”; uwięzienie i efektywne statystyki; „Ostatnio zagrane” w stanie i w kroku odtwarzania; teksty PL (z `LANGUAGE_STORAGE_KEY`) i EN.

### Success Criteria:

#### Automated Verification:

- Testy frontu przechodzą: `cd frontend && npx ng test --watch=false`
- Build produkcyjny przechodzi: `cd frontend && npm run build`
- Testy backendu dalej zielone: `./mvnw -B verify`

#### Manual Verification:

- Lokalnie rozegranie partii każdą frakcją: zagranie wydarzenia bez celu, z celem-postacią obu stron i z celem-graczem; efekty zgodne z opisem na karcie
- Pułapka: uwięziona postać nie atakuje i nie jest celem; efekt obszarowy ją trafia; jej Ochrona nie blokuje ataku
- Aura: postać zagrana w czasie działania wzmocnienia dostaje bonus; po wygaśnięciu wartości wracają
- „Ostatnio zagrane” i wpisy logu pokazują wydarzenie gracza i komputera (także w odtwarzaniu tury komputera); `duration 0` ma „Natychmiastowe”
- Po przełączeniu na EN opisy efektów, log i nowe etykiety są po angielsku

**Implementation Note**: Po fazie i zielonej weryfikacji automatycznej zatrzymaj się na ręczne potwierdzenie.

---

## Testing Strategy

### Unit Tests:

- Katalog: każda reguła walidacji efektów; `describe()` dla każdego rodzaju i zasięgu pl/en.
- Silnik: każdy rodzaj × zasięg; koniec partii w środku listy efektów; cele i ich walidacja; aura, sumowanie, pokonanie przez −HP, wygaśnięcie, nadane słowa kluczowe, pułapka.
- Komputer: wybór akcji, potem celu.

### Integration Tests:

- `MatchControllerTests`: zagranie z celem przez HTTP, błędy celu, legalność w odpowiedzi, `Accept-Language`.
- `match-table.spec.ts`: przepływ wyboru celu i odtwarzanie z `lastEvent`.

### Manual Testing Steps:

1. Rozegraj partię deweloperami i testerami; zagraj każde wydarzenie z ręki co najmniej raz.
2. Zagraj „Niestabilny test” na postać przeciwnika z Ochroną i sprawdź, że można atakować inne cele.
3. Zagraj „Hotfix w piątek” i potem postać — sprawdź +2 ataku i powrót wartości po 2 turach.
4. Obejrzyj odtwarzanie tury komputera, gdy zagrywa wydarzenie z celem.

## Performance Considerations

`legalMoves` rośnie o (liczba kart celowanych × liczba celów) ≤ 10 × 12 ruchów na turę — pomijalne. Statystyki efektywne liczone na żądanie z ≤ 2 × `MAX_DURATION`-ograniczonej listy aktywnych wydarzeń.

## Migration Notes

Partie trzymane w pamięci (`MatchStore`) nie przeżywają restartu, więc zmiana rekordów `Card`/`ActiveEvent`/`BoardCharacter` nie wymaga migracji. Wdrożenie po merge do `main` zaczyna od pustego magazynu.

## References

- Research: `context/changes/event-cards/research.md` (decyzje D1–D18)
- Wzorzec zdolności: `src/main/java/com/example/itcardbrawl/game/AbilityKeyword.java:7-57`
- Wzorzec celu ataku: `src/main/java/com/example/itcardbrawl/api/MatchController.java:81-86,133-135`, `frontend/src/app/match-table/match-table.ts:128-144`
- Poprzednia zmiana: `context/archive/2026-11-20-first-full-match/plan.md`
- Odroczone F4: `context/archive/2026-11-20-first-full-match/follow-ups/review-fixes.md:3-7`

## Progress

> Convention: `- [ ]` pending, `- [x]` done. Append ` — <commit sha>` when a step lands. Do not rename step titles. See `references/progress-format.md`.

### Phase 1: Katalog efektów wydarzeń

#### Automated

- [x] 1.1 Testy backendu przechodzą: `./mvnw -B verify` — b9b821e
- [x] 1.2 `CardCatalogTests` pokrywa każdą nową regułę walidacji efektów i prawdziwy katalog (16 wydarzeń z efektami, talie po 30 kart) — b9b821e

#### Manual

- [x] 1.3 Przegląd tabeli efektów 16 wydarzeń: wartości do przyjęcia jako szkic, opisy generowane pl/en czytelne — b9b821e

### Phase 2: Efekty natychmiastowe i cele w silniku

#### Automated

- [x] 2.1 Testy backendu przechodzą: `./mvnw -B verify`
- [x] 2.2 `MatchEngineTests` pokrywa każdy rodzaj i zasięg efektu natychmiastowego, przegraną przez efekt (stres własny, dobieranie z pustej talii) i walidację celu
- [x] 2.3 `RandomOpponentTests` pokrywa wybór najpierw akcji, potem celu

### Phase 3: Efekty trwające

#### Automated

- [ ] 3.1 Testy backendu przechodzą: `./mvnw -B verify`
- [ ] 3.2 `MatchEngineTests` pokrywa aurę, sumowanie, dolny limit ataku, pokonanie przez −HP, wygaśnięcie, nadaną Szarżę i Ochronę oraz każdą regułę pułapki

### Phase 4: API — cele, efekty i legalność w widoku

#### Automated

- [ ] 4.1 Testy backendu przechodzą: `./mvnw -B verify`
- [ ] 4.2 `MatchControllerTests` pokrywa zagranie z celem postaci i gracza, błędy celu (400) oraz `targets`/`playable`/`attackTargets` w odpowiedzi
- [ ] 4.3 `MatchViewTests` pokrywa statystyki efektywne, `trapped`, `lastEvent` (także w kroku odtwarzania) i opisy efektów w pl i en

#### Manual

- [ ] 4.4 Lokalnie po zalogowaniu odpowiedź `GET /api/matches/current` zawiera `effects`, `targets` i `lastEvent` w języku z `Accept-Language`

### Phase 5: Stół w SPA — wybór celu, ostatnio zagrane, opisy efektów

#### Automated

- [ ] 5.1 Testy frontu przechodzą: `cd frontend && npx ng test --watch=false`
- [ ] 5.2 Build produkcyjny przechodzi: `cd frontend && npm run build`
- [ ] 5.3 Testy backendu dalej zielone: `./mvnw -B verify`

#### Manual

- [ ] 5.4 Lokalnie rozegranie partii każdą frakcją: zagranie wydarzenia bez celu, z celem-postacią obu stron i z celem-graczem; efekty zgodne z opisem na karcie
- [ ] 5.5 Pułapka: uwięziona postać nie atakuje i nie jest celem; efekt obszarowy ją trafia; jej Ochrona nie blokuje ataku
- [ ] 5.6 Aura: postać zagrana w czasie działania wzmocnienia dostaje bonus; po wygaśnięciu wartości wracają
- [ ] 5.7 „Ostatnio zagrane” i wpisy logu pokazują wydarzenie gracza i komputera (także w odtwarzaniu tury komputera); `duration 0` ma „Natychmiastowe”
- [ ] 5.8 Po przełączeniu na EN opisy efektów, log i nowe etykiety są po angielsku
