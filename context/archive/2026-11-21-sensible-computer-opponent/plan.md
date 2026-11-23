# Sensowny przeciwnik komputerowy — plan implementacji

## Overview

Zastępujemy losowego komputera (`RandomOpponent`) przeciwnikiem, który przelicza każdy poprawny ruch przez `MatchEngine` i ocenia stan stołu. Zawsze bierze ruch wygrywający partię, nigdy nie robi ruchów pogarszających swoją pozycję, a spośród pozostałych losuje z wagą zależną od oceny. Przy każdym ruchu, łącznie z końcem tury, gracz widzi krótki powód: jako wpis w logu i jako podpis nad stołem podczas odtwarzania tury komputera. Realizuje to S-05 z roadmapy (FR-003, FR-006, US-01, guardrail „przeciwnik gra sensownie — nie losowo”).

## Current State Analysis

- `RandomOpponent` (`src/main/java/com/example/itcardbrawl/game/RandomOpponent.java:44-80`) grupuje `engine.legalMoves` po akcji, losuje akcję, potem ruch, i gra, dopóki są ruchy; bezpiecznik `MAX_MOVES_PER_TURN = 100`. Jedyny użytkownik produkcyjny: `MatchService` (`MatchService.java:28-33, 41-50, 75-80`) przez `playTurnSteps`.
- `MatchEngine.apply` jest czystą funkcją `(Match, Side, Move) → Match` na roboczej kopii (`MatchEngine.java:25-40`), a `legalMoves` (`:46-81`) zwraca wszystkie zagrania z celami i ataki (bez `EndTurn`). Symulacja ruchu nie zmienia żadnego stanu.
- S-04 jest już `done`, więc strategia musi od razu obsłużyć wydarzenia, także te z celem po obu stronach stołu (`eventTargets`, `MatchEngine.java:92-112`). Losowy komputer może dziś leczyć gracza, ranić lub więzić własne postacie (`context/archive/2026-11-20-event-cards/plan.md:37`).
- Log to strukturalne `MatchEvent` (`MatchEvent.java`), zamieniane na zdania pl/en przez `MatchLogFormatter.format` (wyczerpujący `switch`, `api/MatchLogFormatter.java:276-316`). `LogEntryView(side, text)` (`api/MatchView.java:235-241`) nie odróżnia rodzajów wpisów.
- Odtwarzanie: `ReplayStepView.logSize` (`api/MatchView.java:49-58`), a SPA pokazuje `final.log.slice(0, step.logSize)` (`frontend/src/app/match-table/match-table.ts:308-318`). Wpis wstawiony do logu w tym samym kroku co ruch trafia więc do tego kroku automatycznie. Podczas odtwarzania SPA pokazuje tylko „Ruch przeciwnika…” (`match-table.html:155-157`).
- Stan publiczny: ręka i kolejność talii przeciwnika nie opuszczają serwera (`MatchView` javadoc, `:28-32`).
- Katalog (`src/main/resources/game/factions.json`) ma 31 kart: postacie z RUSH/GUARD/HEAL/ON_PLAY_DAMAGE/OVERTIME i wydarzenia DAMAGE/HEAL/DRAW/BUDGET_*/modyfikatory/TRAP.

## Desired End State

- `MatchService` korzysta z `ComputerOpponent`. Komputer:
  - zawsze wykonuje ruch, który natychmiast wygrywa partię;
  - nigdy nie wykonuje ruchu, który nie poprawia jego oceny pozycji (np. leczenie gracza, obrażenia lub uwięzienie własnej postaci, osłabienie własnego stołu);
  - wśród pozostałych ruchów losuje z wagą rosnącą wraz z oceną; kończy turę, gdy żaden ruch nie poprawia pozycji;
  - ocenia pozycję wyłącznie na podstawie informacji jawnych dla gracza.
- Każdy ruch komputera poprzedza w logu wpis z powodem (`MatchEvent.MoveExplained`), w języku żądania, np. „Przeciwnik: usuwa Senior — Twoją najsilniejszą postać” / „Opponent: removes Senior — your strongest character”.
- Podczas odtwarzania nad stołem widać ostatni powód z bieżącego kroku; w logu wpisy z powodem są wyróżnione.
- Weryfikacja: w 200 partiach z ziarnem (obie frakcje, oba warianty strony zaczynającej) przeciwko losowemu graczowi komputer wygrywa ≥ 80%; test działa w `./mvnw -B verify`.

### Key Discoveries:

- `MatchEngine` jest bezstanowy i nie ma zależności, więc ocena 1 ruchu wprzód to wywołanie `engine.apply` na każdym ruchu z `legalMoves` (`MatchEngine.java:13-18`).
- `CharacterStats.of` (`CharacterStats.java:629-658`) liczy atak, zdrowie, słowa kluczowe i uwięzienie z aktywnymi wydarzeniami. Ocena stołu korzysta z niego, a nie z gołych pól karty.
- Testy deterministyczne opierają się na `SplittableRandom(seed)` i wstrzykniętym `RandomGenerator` (`RandomOpponentTests.java:36-57`, bean `GameConfig.gameRandom`).
- Pomocnik `playRandomPlayerTurn` (`RandomOpponentTests.java:159-167`) to gotowy wzorzec losowego gracza jako punktu odniesienia.
- Lekcja projektu (`context/foundation/lessons.md`): każdy endpoint pod `/api/**`. Ta zmiana nie dodaje endpointów.

## What We're NOT Doing

- Przeszukiwania wielu ruchów naprzód lub całej tury (kombinacje są nagradzane tylko tyle, ile ocena stanu po pojedynczym ruchu).
- Podglądania ręki gracza ani kolejności jakiejkolwiek talii.
- Poziomów trudności, wyboru przeciwnika ani ustawień gracza.
- Humorystycznych tekstów powodów: powody to rzeczowe szablony pl/en, humor zostaje na kartach.
- Zmian tempa odtwarzania (`REPLAY_STEP_MS` bez zmian) ani klikania, by przejść dalej.
- Zmian zasad, silnika, katalogu kart ani balansu talii (S-06).
- Ruchów gracza z powodem: powód dotyczy tylko komputera.

## Implementation Approach

Ocena 1 ruchu wprzód: dla każdego ruchu z `legalMoves` wykonujemy `engine.apply` na stanie bieżącym i oceniamy wynik funkcją `PositionEvaluator.score(Match, Side.OPPONENT)`, która składa się z nazwanych składników: HP obu stron, wartość stołów, liczby kart. Wybór:

1. Ruch, po którym partia jest `FINISHED` z wygraną komputera, jest brany od razu.
2. Ruchy z oceną nie wyższą niż ocena bieżącego stanu odpadają jako szkodliwe lub bezcelowe.
3. Spośród pozostałych losujemy z wagą `exp((score − best) / TEMPERATURE)`.
4. Gdy nic nie zostaje, komputer kończy turę.

Powód pochodzi z tych samych danych: rodzaju ruchu i składnika oceny o największym ważonym wkładzie, przy czym wygrana ma pierwszeństwo. Komputer zapisuje powód jako `MatchEvent.MoveExplained`, wstawiony do logu wyniku `apply` tuż przed zdarzeniami tego ruchu. Silnik nie wie nic o powodach, a powód trafia do tego samego kroku odtwarzania. API formatuje go jak każdy inny wpis i oznacza flagą `reason`, a SPA pokazuje ostatni taki wpis z widocznego wycinka logu jako podpis.

## Critical Implementation Details

- **Uczciwość a dobieranie.** `engine.apply` dla wydarzenia DRAW faktycznie dobiera wierzchnią kartę własnej talii. `PositionEvaluator` musi więc czytać rękę i talię każdej strony wyłącznie jako liczby kart (nigdy zawartość), bo inaczej ocena ruchu „dobierz” zdradziłaby komputerowi kolejność jego talii. Ta sama zasada chroni rękę gracza.
- **Kolejność logu.** `MoveExplained` musi stać w logu przed `CardPlayed`/`Attacked`/`TurnEnded` danego ruchu, czyli na pozycji `poprzedni log.size()` w logu wyniku `apply`. Wtedy `logSize` kroku obejmuje i powód, i skutki. Dla `EndTurn` powód stoi przed `TurnEnded` (i przed ewentualnym `AbilityTriggered` z OVERTIME).
- **Wydajność.** Jeden krok to najwyżej ~150 symulacji (`legalMoves` × kopia stanu z logiem). Bezpiecznik `MAX_MOVES_PER_TURN` zostaje. Symulacja 200 partii w teście powinna zmieścić się w kilkunastu sekundach. Jeśli nie, zmniejszamy koszt kopii logu w ocenie (np. ocena nie czyta logu), a nie liczbę partii.

## Phase 1: Ocena pozycji i wybór ruchu

### Overview

Nowa strategia komputera w domenie, podłączona do `MatchService` w miejsce `RandomOpponent`, z testami „bez głupich ruchów”, uczciwości i symulacji odsetka wygranych.

### Changes Required:

#### 1. Ocena pozycji

**File**: `src/main/java/com/example/itcardbrawl/game/PositionEvaluator.java` (nowy)

**Intent**: Liczbowa ocena stanu partii z perspektywy strony, rozbita na nazwane składniki, żeby wybór ruchu i powód korzystały z tych samych danych. Styl zrównoważony: kontrola stołu i nacisk na HP przeciwnika ważą podobnie. Gdy HP przeciwnika jest nisko (blisko wygranej), rośnie waga obrażeń w HP. Gdy własne HP jest nisko, rośnie waga własnego HP i obrony (leczenie, Ochrona, usuwanie zagrożeń).

**Contract**:
- `Score score(Match match, Side perspective)`; `Score` to rekord ze składnikami (np. `ownHp`, `enemyHp`, `ownBoard`, `enemyBoard`, `cards`) i `total()`.
- Partia zakończona daje skrajną wartość: wygrana `+∞` w praktyce (duża stała), przegrana `−∞`.
- Wartość postaci liczona z `CharacterStats` (atak, pozostałe zdrowie, słowa kluczowe, uwięzienie obniża wartość). Aktywne wydarzenia działają przez `CharacterStats`.
- Odczyt wyłącznie jawnych danych: HP, budżety, stoły, aktywne wydarzenia, `hand().size()`, `deck().size()`. Nigdy zawartość ręki ani talii.
- Wagi i progi („HP przeciwnika nisko”, „własne HP nisko”) jako nazwane stałe w klasie, do strojenia w S-06.

#### 2. Wybór ruchu komputera

**File**: `src/main/java/com/example/itcardbrawl/game/ComputerOpponent.java` (nowy)

**Intent**: Komponent Springa z tym samym kontraktem co dzisiejszy `RandomOpponent` (`playTurn`, `playTurnSteps`), grający według reguł z „Implementation Approach”.

**Contract**:
- `ComputerOpponent(MatchEngine engine, PositionEvaluator evaluator, RandomGenerator random)`; `Match playTurn(Match)`, `List<Match> playTurnSteps(Match)`, z tą samą semantyką kroków i pustej listy poza turą komputera co `RandomOpponent.java:30-56`.
- Bezpiecznik `MAX_MOVES_PER_TURN = 100` przeniesiony bez zmian.
- Wybór: ruch wygrywający od razu; odrzucenie ruchów z `score(after) ≤ score(current)`; losowanie ważone `exp((score − best) / TEMPERATURE)` wśród pozostałych przez wstrzyknięty `RandomGenerator`; pusta pula → `EndTurn`.
- `PositionEvaluator` jest beanem albo zwykłym polem tworzonym w konstruktorze. Implementujący wybiera prostszy wariant, zgodny z `GameConfig`.

#### 3. Podłączenie i usunięcie losowego komputera

**File**: `src/main/java/com/example/itcardbrawl/game/MatchService.java`, `src/main/java/com/example/itcardbrawl/game/RandomOpponent.java` (usunięty), `src/test/java/com/example/itcardbrawl/game/RandomOpponentTests.java` (usunięty lub przemianowany)

**Intent**: `MatchService` wstrzykuje `ComputerOpponent` zamiast `RandomOpponent`. Losowa strategia znika z kodu produkcyjnego i zostaje tylko jako punkt odniesienia w testach.

**Contract**: Konstruktor `MatchService(MatchFactory, MatchEngine, ComputerOpponent, MatchStore)`; zachowanie `start`/`move` i `replay` bez zmian. `MatchServiceTests` (`:48`) przechodzi na `ComputerOpponent` z ziarnem.

#### 4. Testy strategii

**File**: `src/test/java/com/example/itcardbrawl/game/ComputerOpponentTests.java` (nowy), `src/test/java/com/example/itcardbrawl/game/RandomPlayer.java` (nowy pomocnik testowy), `src/test/java/com/example/itcardbrawl/game/PositionEvaluatorTests.java` (nowy)

**Intent**: Sprawdzić kryterium akceptacji: brak głupich ruchów, uczciwość informacji, przewagę nad graczem losowym i to, że każda partia się kończy.

**Contract**:
- `RandomPlayer` to pomocnik testowy grający stroną `PLAYER`: wybiera akcję jednostajnie, potem jej wariant (logika z dzisiejszego `RandomOpponent.chooseMove`), aż brak ruchów.
- Testy „bez głupich ruchów” na ręcznie zbudowanych stanach (wzorzec `opponentTurn`/`side` z `RandomOpponentTests.java:169+`):
  - bierze ruch wygrywający (atak lub wydarzenie w HP gracza przy niskim HP), także gdy są inne dobre ruchy;
  - nie leczy gracza (HEAL TARGET), nie zadaje obrażeń własnej postaci (DAMAGE TARGET), nie więzi własnej postaci (TRAP);
  - nie zagrywa wydarzenia ALL_BOARDS, które niszczy głównie własny stół;
  - kończy turę, gdy jedyne poprawne ruchy są szkodliwe;
  - przy każdym z wielu ziaren wynik jest ten sam co do wymienionych gwarancji.
- Test uczciwości: dwa stany różniące się tylko zawartością ręki gracza i kolejnością obu talii (przy tych samych liczbach kart) dają tę samą ocenę `PositionEvaluator` i, przy tym samym ziarnie, ten sam ruch.
- Symulacja: 200 partii z ziarnem (`MatchFactory` z ziarnem, obie frakcje, oba warianty strony zaczynającej), `ComputerOpponent` przeciwko `RandomPlayer`. Wymagane ≥ 80% wygranych komputera, a każda partia kończy się przed limitem tur (zastępuje `fiftyComputerVersusComputerMatchesFinishWithoutErrors`).
- `PositionEvaluatorTests`: wygrana/przegrana dają wartości skrajne; postać uwięziona jest warta mniej niż wolna; niższe HP przeciwnika podnosi ocenę; progi zmieniają wagę HP.

### Success Criteria:

#### Automated Verification:

- Testy strategii przechodzą: `./mvnw -B test -Dtest='ComputerOpponentTests,PositionEvaluatorTests,MatchServiceTests'`
- Symulacja 200 partii z ziarnem daje ≥ 80% wygranych komputera przeciwko losowemu graczowi (w `ComputerOpponentTests`)
- `RandomOpponent` nie występuje w `src/main`: `grep -rn RandomOpponent src/main` zwraca pusty wynik
- Pełny zestaw testów backendu przechodzi: `./mvnw -B verify`

#### Manual Verification:

- W lokalnej partii komputer zagrywa postacie, wymienia się korzystnie i nie leczy gracza ani nie rani własnych postaci (kilka tur obserwacji)

**Implementation Note**: Po tej fazie i zielonej weryfikacji automatycznej zatrzymaj się na ręczne potwierdzenie przed fazą 2.

---

## Phase 2: Powód ruchu w logu

### Overview

Strukturalne zdarzenie z powodem przed każdym ruchem komputera, przetłumaczone na zdania pl/en i oznaczone w widoku logu.

### Changes Required:

#### 1. Zdarzenie i rodzaje powodów

**File**: `src/main/java/com/example/itcardbrawl/game/MatchEvent.java`, `src/main/java/com/example/itcardbrawl/game/MoveReason.java` (nowy)

**Intent**: Niezależny od języka zapis powodu ruchu komputera, tak jak reszta logu.

**Contract**:
- `record MoveExplained(Match.Side side, MoveReason reason, CardInstance card, CardInstance target, int amount) implements MatchEvent`. `card` to zagrywana karta albo atakująca postać (`null` dla końca tury), `target` to postać docelowa (`null`, gdy celem jest gracz lub brak celu), `amount` to liczba do szablonu (np. obrażenia w HP, leczenie) albo 0.
- `enum MoveReason`: `LETHAL` (ruch wygrywa partię), `REMOVE_THREAT` (pokonuje postać gracza), `FAVORABLE_TRADE` (atak w postać, który osłabia stół gracza bardziej niż własny), `OPEN_GUARD` (atak w postać z Ochroną), `PRESSURE` (obrażenia w HP gracza), `DEVELOP_BOARD` (zagranie postaci), `STRENGTHEN` (wzmocnienie własnych postaci), `WEAKEN` (osłabienie lub uwięzienie postaci gracza), `DEFEND` (leczenie lub Ochrona przy niskim własnym HP), `DRAW_CARDS`, `GAIN_BUDGET`, `END_NO_GAIN` (żaden ruch nie poprawia pozycji), `END_NO_MOVES` (brak poprawnych ruchów). Implementujący może scalić lub dodać rodzaj, jeśli katalog tego wymaga; każdy rodzaj musi mieć test formatera.

#### 2. Wyprowadzenie i wstawienie powodu

**File**: `src/main/java/com/example/itcardbrawl/game/ComputerOpponent.java`, `src/main/java/com/example/itcardbrawl/game/Match.java`

**Intent**: Komputer wyznacza powód z wybranego ruchu i różnicy składników `Score` (przed/po), a potem wstawia `MoveExplained` do logu wyniku `apply` przed zdarzeniami tego ruchu.

**Contract**:
- Reguła wyboru powodu: wygrana → `LETHAL`; koniec tury → `END_NO_MOVES` albo `END_NO_GAIN`; zagranie postaci → `DEVELOP_BOARD` (albo `PRESSURE`/`DEFEND`, gdy ON_PLAY_DAMAGE/HEAL daje największy wkład); w pozostałych przypadkach decyduje składnik o największym ważonym wkładzie.
- Mała metoda pomocnicza na `Match` (np. `withEventAt(int index, MatchEvent event)`) zwraca kopię z wpisem wstawionym w podanym miejscu. Silnik pozostaje jedynym miejscem zmiany stanu gry, bo log nie jest stanem zasad.
- Powód odwołuje się wyłącznie do jawnych faktów: zagrana karta, cel, liczby.

#### 3. Zdania i widok logu

**File**: `src/main/java/com/example/itcardbrawl/api/MatchLogFormatter.java`, `src/main/java/com/example/itcardbrawl/api/MatchView.java`

**Intent**: Rzeczowe szablony pl/en dla każdego `MoveReason`, z prefiksem „Przeciwnik:” / „Opponent:”, oraz flaga odróżniająca wpis z powodem.

**Contract**:
- Nowa gałąź `case MoveExplained e` w `format`. Przykłady: `LETHAL` → „Przeciwnik: kończy partię — %s zadaje Ci %d obrażeń” / „Opponent: finishes the match — %s deals you %d damage”; `END_NO_GAIN` → „Przeciwnik: kończy turę — żaden ruch nie poprawia jego sytuacji” / „Opponent: ends the turn — no move improves the position”. Liczebniki przez `PolishNumberForms`.
- `LogEntryView(Match.Side side, String text, boolean reason)`; `reason == true` tylko dla `MoveExplained`.

#### 4. Testy

**File**: `src/test/java/com/example/itcardbrawl/game/ComputerOpponentTests.java`, `src/test/java/com/example/itcardbrawl/api/MatchLogFormatterTests.java`, `src/test/java/com/example/itcardbrawl/api/MatchViewTests.java`, `src/test/java/com/example/itcardbrawl/api/MatchControllerTests.java`

**Intent**: Sprawdzić obecność, kolejność, treść i serializację powodów.

**Contract**:
- Każdy ruch komputera w turze ma dokładnie jeden `MoveExplained`, stojący bezpośrednio przed pierwszym zdarzeniem tego ruchu. Dotyczy to także końca tury.
- Scenariusze z fazy 1 dają oczekiwane rodzaje powodów (wygrana → `LETHAL`, zabicie postaci → `REMOVE_THREAT`, brak dobrych ruchów → `END_NO_GAIN`).
- `logSize` każdego kroku odtwarzania obejmuje powód swojego ruchu.
- Formater: każdy `MoveReason` ma niepuste zdanie pl i en, a zdanie nie zawiera nazw kart spoza zdarzenia.
- JSON: wpisy logu mają pole `reason`, równe `true` tylko dla powodów.

### Success Criteria:

#### Automated Verification:

- Testy powodów i formatera przechodzą: `./mvnw -B test -Dtest='ComputerOpponentTests,MatchLogFormatterTests,MatchViewTests,MatchControllerTests'`
- Pełny zestaw testów backendu przechodzi: `./mvnw -B verify`

#### Manual Verification:

- W lokalnej partii log pokazuje przed każdym ruchem komputera zrozumiały powód po polsku i po angielsku (przełącznik języka)

**Implementation Note**: Po tej fazie i zielonej weryfikacji automatycznej zatrzymaj się na ręczne potwierdzenie przed fazą 3.

---

## Phase 3: Podpis powodu w SPA

### Overview

Podczas odtwarzania tury komputera nad stołem widać ostatni powód z bieżącego kroku, a wpisy z powodem są wyróżnione w logu.

### Changes Required:

#### 1. Typy i podpis

**File**: `frontend/src/app/game/match.ts`, `frontend/src/app/match-table/match-table.ts`, `frontend/src/app/match-table/match-table.html`, `frontend/src/app/match-table/match-table.css`

**Intent**: `LogEntry` dostaje `reason: boolean`. W trakcie odtwarzania obok lub zamiast „Ruch przeciwnika…” pojawia się podpis z tekstem ostatniego wpisu `reason` z widocznego wycinka logu. Wpisy `reason` w logu mają osobną klasę CSS (np. kursywa lub przygaszony kolor).

**Contract**:
- `LogEntry = { side: Side; text: string; reason: boolean }`.
- Podpis to computed z bieżącego stanu, który przy odtwarzaniu ma już log przycięty do `logSize` (`match-table.ts:308-318`). Widoczny tylko, gdy `replaying()` i istnieje wpis `reason`; ma `role="status"`. Po końcu odtwarzania znika.
- Tekst powodu pochodzi z backendu. Etykieta podpisu, jeśli jest (np. „Dlaczego:” / „Why:”), trafia do `messages.ts`, `pl.ts` i `en.ts` (AGENTS.md: nigdy nie wpisuj tekstu UI na sztywno).
- `REPLAY_STEP_MS` bez zmian.

#### 2. Testy SPA

**File**: `frontend/src/app/match-table/match-table.spec.ts`

**Intent**: Sprawdzić, że podpis pokazuje powód właściwego kroku i znika po odtwarzaniu.

**Contract**: Test z odtwarzaniem 2 kroków z różnymi powodami: po kroku 1 podpis ma powód 1, po kroku 2 powód 2, po końcu podpisu nie ma. Wpis `reason` w logu ma klasę wyróżnienia. Używa istniejącego `REPLAY_STEP_MS` z testowej wartości.

### Success Criteria:

#### Automated Verification:

- Testy SPA przechodzą: `cd frontend && npx ng test --watch=false`
- Build produkcyjny SPA przechodzi: `cd frontend && npm run build`

#### Manual Verification:

- Podczas tury komputera nad stołem widać przy każdym kroku jego powód, w tempie pozwalającym go przeczytać lub doczytać w logu
- Wpisy z powodem są w logu wizualnie odróżnione od skutków ruchów
- Po zagraniu kilku partii komputer sprawia wrażenie grającego sensownie, a partia mieści się w ≤ 15 minutach

---

## Testing Strategy

### Unit Tests:

- `PositionEvaluator`: wartości skrajne dla końca partii, wpływ uwięzienia i modyfikatorów, progi HP, niezależność od zawartości ręki i talii.
- `ComputerOpponent`: wygrana zawsze brana; brak samookaleczeń wydarzeniami z celem i ALL_BOARDS; koniec tury, gdy nic nie poprawia pozycji; bezpiecznik ruchów; determinizm przy tym samym ziarnie; powód przed każdym ruchem.
- `MatchLogFormatter`: szablon pl/en dla każdego `MoveReason`.

### Integration Tests:

- Symulacja 200 partii z ziarnem `ComputerOpponent` przeciwko `RandomPlayer`: ≥ 80% wygranych, każda partia się kończy.
- `MatchServiceTests`/`MatchControllerTests`: `replay` po końcu tury gracza zawiera kroki z powodami, a `log[*].reason` jest serializowane.
- `match-table.spec.ts`: podpis powodu w odtwarzaniu.

### Manual Testing Steps:

1. Uruchom backend i SPA lokalnie, rozpocznij partię każdą frakcją.
2. Obserwuj kilka tur komputera: każdy krok ma podpis z powodem, a log ma wyróżniony wpis przed skutkami.
3. Doprowadź HP komputera do niskiego poziomu i sprawdź, czy powody zmieniają się na obronne. Doprowadź własne HP do niskiego poziomu i sprawdź, czy komputer kończy partię, gdy może.
4. Przełącz język i sprawdź powody po angielsku.

## Performance Considerations

Ocena 1 ruchu wprzód kosztuje jedną kopię stanu na każdy poprawny ruch w każdym kroku. Przy limitach ręki (10) i stołu (5) to rząd ~150 kopii na krok, czyli milisekundy w pojedynczym żądaniu. Główny koszt to test symulacji. Jeśli przekroczy kilkanaście sekund, w pierwszej kolejności tańszy staje się odczyt stanu w ocenie, bez zmiany liczby partii.

## Migration Notes

Brak danych do migracji: partie żyją w pamięci (`MatchStore`). Po wdrożeniu partie rozpoczęte przed restartem i tak znikają. Pole JSON `log[*].reason` jest dodatkowe; SPA i API wdrażane są razem w jednym obrazie.

## References

- Roadmapa: `context/foundation/roadmap.md` (S-05)
- PRD: `context/foundation/prd.md` (FR-003, FR-006, Guardrails)
- Losowy komputer: `src/main/java/com/example/itcardbrawl/game/RandomOpponent.java:44-80`
- Silnik: `src/main/java/com/example/itcardbrawl/game/MatchEngine.java:25-112`
- Odtwarzanie w SPA: `frontend/src/app/match-table/match-table.ts:241-318`
- Wcześniejsze decyzje: `context/archive/2026-11-20-first-full-match/plan-brief.md:32`, `context/archive/2026-11-20-event-cards/plan.md:37`

## Progress

> Convention: `- [ ]` pending, `- [x]` done. Append ` — <commit sha>` when a step lands. Do not rename step titles. See `references/progress-format.md`.

### Phase 1: Ocena pozycji i wybór ruchu

#### Automated

- [x] 1.1 Testy strategii przechodzą: `./mvnw -B test -Dtest='ComputerOpponentTests,PositionEvaluatorTests,MatchServiceTests'` — 5c1c75d
- [x] 1.2 Symulacja 200 partii z ziarnem daje ≥ 80% wygranych komputera przeciwko losowemu graczowi (w `ComputerOpponentTests`) — 5c1c75d
- [x] 1.3 `RandomOpponent` nie występuje w `src/main`: `grep -rn RandomOpponent src/main` zwraca pusty wynik — 5c1c75d
- [x] 1.4 Pełny zestaw testów backendu przechodzi: `./mvnw -B verify` — 5c1c75d

#### Manual

- [x] 1.5 W lokalnej partii komputer zagrywa postacie, wymienia się korzystnie i nie leczy gracza ani nie rani własnych postaci (kilka tur obserwacji) — 5c1c75d

### Phase 2: Powód ruchu w logu

#### Automated

- [x] 2.1 Testy powodów i formatera przechodzą: `./mvnw -B test -Dtest='ComputerOpponentTests,MatchLogFormatterTests,MatchViewTests,MatchControllerTests'` — f8d9c9c
- [x] 2.2 Pełny zestaw testów backendu przechodzi: `./mvnw -B verify` — f8d9c9c

#### Manual

- [x] 2.3 W lokalnej partii log pokazuje przed każdym ruchem komputera zrozumiały powód po polsku i po angielsku (przełącznik języka) — f8d9c9c

### Phase 3: Podpis powodu w SPA

#### Automated

- [x] 3.1 Testy SPA przechodzą: `cd frontend && npx ng test --watch=false` — 433ccb0
- [x] 3.2 Build produkcyjny SPA przechodzi: `cd frontend && npm run build` — 433ccb0

#### Manual

- [x] 3.3 Podczas tury komputera nad stołem widać przy każdym kroku jego powód, w tempie pozwalającym go przeczytać lub doczytać w logu — 433ccb0
- [x] 3.4 Wpisy z powodem są w logu wizualnie odróżnione od skutków ruchów — 433ccb0
- [x] 3.5 Po zagraniu kilku partii komputer sprawia wrażenie grającego sensownie, a partia mieści się w ≤ 15 minutach — 433ccb0
