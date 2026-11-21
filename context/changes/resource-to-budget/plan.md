# Zasób → budżet — plan implementacji

## Overview

Zmieniamy nazwę zasobu na zagrywanie kart (`resource`, „Zasób”) na budżet (`budget`, „Budżet”) w silniku, danych kart, kontrakcie JSON, tekstach pl/en, SPA, testach i PRD. Mechanika zostaje bez zmian: start 1/1 dla zaczynającego i 0/0 dla drugiej strony, +1 na własną turę do 10, koszt karty 1–10.

## Current State Analysis

- Stan: `MatchSide.resourceCurrent`, `resourceMax` (`game/MatchSide.java:10`), `MatchRules.RESOURCE_CAP = 10` (`game/MatchRules.java:20`, javadoc `:7`), start w `game/MatchFactory.java:65-66` (lokalna `resource`), `CardCatalog.MAX_CARD_COST = MatchRules.RESOURCE_CAP` (`game/CardCatalog.java:18`).
- Silnik (`game/MatchEngine.java`): `canPlay` `:83-84`, filtr efektów postaci `:115`, efekt `RESOURCE` `:139-142`, `RESOURCE_MAX` `:143-146`, kontrola kosztu z komunikatem „Za mało zasobu na zagranie karty” `:287-288`, wydatek `:305`, wzrost na turę `:447-448`, `SideState` `:568-585`.
- Dane: `EffectKind.RESOURCE`, `RESOURCE_MAX` (`game/EffectKind.java:15-16`, opisy `:77-79`); w `src/main/resources/game/factions.json` dokładnie 2 wpisy `kind` — `neu-coffee` (`:45`) i `neu-sprint-planning` (`:49`).
- Kontrakt: `MatchView.ResourceView(current, max)` (`api/MatchView.java:76-79`), pola `PlayerView`/`OpponentView.resource` (`:84,91,100,107`, także w `replay[*]`), `effects[].kind`, komunikat błędu przez `MatchController.java:108-110` (HTTP 400).
- Log (`MatchLogFormatter`, `MatchEvent`, `AbilityKeyword`) nie mówi o zasobie.
- SPA: typy `game/match.ts:35-36,77-80,138,151`, panel `match-table/match-table.html:21-23,176-178` (klasa `resource`, bez reguły CSS), i18n `matchTable.resource` (`i18n/messages.ts:37`, `pl.ts:36`, `en.ts:36`), specy `match-table.spec.ts` i `new-match/new-match.spec.ts`.
- PRD nie opisuje zasobu; `prd.md:119` „jedynym zasobem przegranej jest HP” ma inne znaczenie.

Szczegóły i pełne kotwice (w tym testy): `context/changes/resource-to-budget/research.md`.

## Desired End State

- JSON: `player|opponent.budget.{current,max}` (także w `replay[*]`), brak klucza `resource`; `effects[].kind` = `"BUDGET_GAIN"` / `"BUDGET_MAX"`; nielegalne zagranie bez środków zwraca „Za mało budżetu na zagranie karty”.
- UI: panel „Budżet” / „Budget” po obu stronach stołu z `current/max`; opisy kart „Zwiększ budżet o N” / „Increase budget by N” i „Zwiększ maksymalny budżet o N” / „Increase maximum budget by N”.
- PRD §Business Logic opisuje budżet (start 1/1 i 0/0, +1 na własną turę do 10, koszt karty).
- Grep z wykluczeniami (Testing Strategy) po `src frontend/src` zwraca pusty wynik (dziś 183 linie).

Weryfikacja: zielone `./mvnw -B verify`, `cd frontend && npx ng test --watch=false`, `cd frontend && npm run build`, pusty grep oraz ręczna partia na wdrożonej wersji.

### Key Discoveries:

- Brak logiki do odwrócenia — każde użycie to zmiana nazwy (`research.md` §Model, reguły i silnik).
- Grep `resource|zas[oó]b` łapie Spring (`ClassPathResource`, `CardCatalogLoader.java:6,22-32`, `GameConfig.java:8,18`) i „zasobow statycznych” (`web/SpaForwardController.java:16`) — kryterium musi je wykluczać.
- `budżet` jest rodzaju męskiego jak `zasób`, więc „maksymalny budżet”, „Budżet rośnie”, „Za mało budżetu” podstawiają się poprawnie; unikamy „pkt budżetu” i angielskiego „Gain N budget”.
- Lekcje z impl-review stress-to-hp (`context/archive/2026-11-21-stress-to-hp/reviews/impl-review.md`): asercje nowych pól w `replay[*]` (F1), stałe zamiast literałów w testach (F7), teksty z planu sprawdzone pod kątem brzmienia (F2/F3/F5).

## What We're NOT Doing

- Zmian mechaniki: startu, wzrostu, limitu 10, kosztów kart, sufitu efektów.
- Zmiany pola `cost` i stałych `MIN_CARD_COST`/`MAX_CARD_COST` (poza referencją do nowej stałej).
- Zmiany nazw i tekstów fabularnych kart („Kawa z automatu — Paliwo projektu”, „Planowanie sprintu”).
- Nowego zdarzenia logu o budżecie ani logowania wzrostu na turę.
- Edycji `shape-notes.md`, `roadmap.md`, `tech-stack.md` i czegokolwiek w `context/archive/`.
- Wersjonowania API ani zgodności wstecznej nazw (SPA wychodzi w tym samym obrazie).

## Implementation Approach

Jak w stress-to-hp: backend w jednej fazie, bo zmiana nazw w `game` łamie kompilację `api`; SPA w drugiej fazie, po której jest jedyny push (CI wdraża każdy push na `main`, `.github/workflows/ci.yml:3-8,56-58,86-88`); PRD i końcowy grep w trzeciej.

## Critical Implementation Details

- **Kolejność wdrożenia.** Nie pushuj po Fazie 1 — wdrożony backend wysyłałby `budget`, a stary SPA czytałby `resource` i panel byłby pusty. Jeden push po Fazie 2.

## Phase 1: Backend — budżet w silniku, danych, API i tekstach

### Overview

Zmiana nazw w modelu, regułach, silniku, enumach, danych kart i widoku JSON oraz nowe teksty pl/en i komunikat błędu. Testy backendu przepisane na nowe nazwy.

### Changes Required:

#### 1. Stan i reguły

**File**: `src/main/java/com/example/itcardbrawl/game/MatchSide.java`, `MatchRules.java`, `MatchFactory.java`, `CardCatalog.java`

**Intent**: Nazwy stanu i limitu mówią o budżecie.

**Contract**: składniki `MatchSide.resourceCurrent/resourceMax` → `budgetCurrent/budgetMax`; `MatchRules.RESOURCE_CAP` → `BUDGET_CAP` (javadoc: „zaczynający ma budżet 1/1 … Budżet rośnie o 1 na własną turę do {@link #BUDGET_CAP}”); lokalna `resource` w `MatchFactory` → `budget`; `CardCatalog.MAX_CARD_COST = MatchRules.BUDGET_CAP`.

#### 2. Silnik

**File**: `src/main/java/com/example/itcardbrawl/game/MatchEngine.java`

**Intent**: Wszystkie użycia zasobu w silniku na nazwy budżetu, bez zmiany logiki.

**Contract**: `SideState.resourceCurrent/resourceMax` → `budgetCurrent/budgetMax`; `case RESOURCE`/`RESOURCE_MAX` → `BUDGET_GAIN`/`BUDGET_MAX`; komunikat `IllegalMoveException` → „Za mało budżetu na zagranie karty”.

#### 3. Enumy efektów i dane kart

**File**: `src/main/java/com/example/itcardbrawl/game/EffectKind.java`, `src/main/resources/game/factions.json`

**Intent**: Opisowe nazwy efektów budżetu i naturalne teksty.

**Contract**: `EffectKind.RESOURCE` → `BUDGET_GAIN`, `RESOURCE_MAX` → `BUDGET_MAX` (te same parametry: natychmiastowe, 1–10, zasięg `SELF`). Opisy: `BUDGET_GAIN` „Zwiększ budżet o %d” / „Increase budget by %d”; `BUDGET_MAX` „Zwiększ maksymalny budżet o %d” / „Increase maximum budget by %d”. `factions.json`: `neu-coffee` `"kind": "RESOURCE"` → `"BUDGET_GAIN"`, `neu-sprint-planning` `"kind": "RESOURCE_MAX"` → `"BUDGET_MAX"`; nic poza tymi 2 polami.

#### 4. Kontrakt API

**File**: `src/main/java/com/example/itcardbrawl/api/MatchView.java`

**Intent**: Pole JSON mówi o budżecie.

**Contract**: `ResourceView` → `BudgetView(int current, int max)`; składniki `PlayerView.resource`/`OpponentView.resource` → `budget` (JSON `budget.current`, `budget.max`, także w `ReplayStepView`).

#### 5. Testy backendu

**File**: `src/test/java/com/example/itcardbrawl/game/MatchEngineTests.java`, `MatchFactoryTests.java`, `RandomOpponentTests.java`, `MatchServiceTests.java`, `src/test/java/com/example/itcardbrawl/api/MatchControllerTests.java`, `MatchViewTests.java`

**Intent**: Testy na nowe nazwy, bez „resource”/„zasób” w nazwach, helperach i komentarzach; dodatkowe asercje kontraktu.

**Contract**:
- Nazwy: `notEnoughResourceIsRejected` → `notEnoughBudgetIsRejected`, `endTurnSwitchesSideDrawsAndRaisesResource` → `…RaisesBudget`, `resourceStopsAtCap` → `budgetStopsAtCap`, `resourceEffectsStopAtCap` → `budgetEffectsStopAtCap`, `…StartsWithOneResource…` → `…StartsWithOneBudget…`; `SideBuilder.resource(int)` → `budget(int)`; stałe przez `MatchRules.BUDGET_CAP`, nie literał `10`.
- `notEnoughBudgetIsRejected` sprawdza też komunikat „Za mało budżetu na zagranie karty”.
- `MatchControllerTests`: `$.player.budget.current/max`, `$.opponent.budget.max`; `containsOnlyKeys` z `budget` zamiast `resource`; w każdym kroku `replay[*]` `player` i `opponent` mają `budget` z kluczami `current`, `max` i nie mają `resource`.
- `MatchViewTests`: tekst en efektu `neu-coffee` zawiera „budget”; JSON widoku ma `player.budget.max` i brak `player.resource`.

### Success Criteria:

#### Automated Verification:

- Testy backendu przechodzą: `./mvnw -B verify`
- Brak nazw zasobu w backendzie: grep z wykluczeniami (Testing Strategy) ograniczony do `src` zwraca pusty wynik
- Kontrola celowego uszkodzenia: przywrócenie nazwy składnika `PlayerView.budget` na `resource` łamie asercje `budget` w `MatchControllerTests` (przywrócone przed commitem)

#### Manual Verification:

- Przegląd diffu `factions.json`: zmieniono wyłącznie 2 pola `kind`, żadnych innych pól

**Implementation Note**: Po zielonych bramkach commit lokalny, bez pusha (SPA jeszcze czyta `resource`).

---

## Phase 2: SPA — budżet w typach, stole i i18n

### Overview

Front czyta `budget` i pokazuje „Budżet” / „Budget”; specy przepisane. Po tej fazie jeden push i obserwacja wdrożenia.

### Changes Required:

#### 1. Typy

**File**: `frontend/src/app/game/match.ts`

**Intent**: Typy odpowiadają nowemu JSON.

**Contract**: `EffectKind` `'RESOURCE' | 'RESOURCE_MAX'` → `'BUDGET_GAIN' | 'BUDGET_MAX'`; `ResourceView` → `BudgetView`; `PlayerView`/`OpponentView.resource` → `budget`.

#### 2. Stół

**File**: `frontend/src/app/match-table/match-table.html`

**Intent**: Panel obu stron pokazuje budżet.

**Contract**: `div.resource` → `div.budget` z `{{ texts().matchTable.budget }}` i `{{ m.<side>.budget.current }}/{{ m.<side>.budget.max }}`.

#### 3. i18n

**File**: `frontend/src/app/i18n/messages.ts`, `pl.ts`, `en.ts`

**Intent**: Etykieta budżetu w obu językach.

**Contract**: `matchTable.resource` → `matchTable.budget` („Budżet” / „Budget”).

#### 4. Specy

**File**: `frontend/src/app/match-table/match-table.spec.ts`, `frontend/src/app/new-match/new-match.spec.ts`

**Intent**: Fixture i asercje w słownictwie budżetu, bez „resource”/„zasób”.

**Contract**: fixture `resource: {…}` → `budget: {…}`; `kind: 'RESOURCE'` → `'BUDGET_GAIN'`; tekst `'Zyskaj 2 pkt zasobu'` → `'Zwiększ budżet o 2'`; helper `hand(resource)` → `hand(budget)`; selektor `.resource dd`/`dt` → `.budget dd`/`dt`; etykieta `'Resource'` → `'Budget'` oraz nowa asercja pl `'Budżet'`; komunikat błędu `'Za mało zasobu'` → `'Za mało budżetu'`; nazwa testu „…and resources” → „…and budgets”.

### Success Criteria:

#### Automated Verification:

- Testy frontu przechodzą: `cd frontend && npx ng test --watch=false`
- Build produkcyjny przechodzi: `cd frontend && npm run build`
- Testy backendu dalej zielone: `./mvnw -B verify`
- Brak nazw zasobu we froncie: grep z wykluczeniami (Testing Strategy) ograniczony do `frontend/src` zwraca pusty wynik

#### Manual Verification:

- Po pushu workflow CI (test → image → deploy ze smoke testem) kończy się sukcesem
- Na wdrożonej wersji panel „Budżet” po obu stronach pokazuje 1/1 i 0/0 na starcie, rośnie co turę, zagranie karty obniża bieżący budżet, a „Kawa z automatu” pokazuje „Zwiększ budżet o 2” (pl i en)

---

## Phase 3: PRD i kontrola resztek

### Overview

PRD opisuje budżet; końcowe potwierdzenie, że kod nie zawiera słownictwa zasobu.

### Changes Required:

#### 1. PRD

**File**: `context/foundation/prd.md`

**Intent**: Reguła biznesowa opisuje budżet na zagrywanie kart, a słowo „zasób” znika z PRD.

**Contract**: §Business Logic (`:111-119`): nowe zdanie — zagranie karty kosztuje budżet (koszt 1–10); zaczynający ma budżet 1/1, druga strona 0/0; budżet rośnie o 1 na własną turę do 10 i odnawia się do maksimum. `:119` „jedynym zasobem przegranej jest HP” → „jedynym licznikiem przegranej jest HP”.

### Success Criteria:

#### Automated Verification:

- Brak „zasób” w PRD: `grep -niE "zas[oó]b|zasob" context/foundation/prd.md` zwraca pusty wynik
- Brak nazw zasobu w kodzie: grep z wykluczeniami (Testing Strategy) po `src frontend/src` zwraca pusty wynik

#### Manual Verification:

- Przegląd PRD: opis budżetu odpowiada zachowaniu silnika (start 1/1 i 0/0, +1 na własną turę do 10, koszt karty)

---

## Testing Strategy

### Grep z wykluczeniami

Dziś zwraca 183 linie; po zmianie ma zwrócić pusty wynik (Faza 1: `src`, Faza 2: `frontend/src`, Faza 3: oba):

```
grep -rniE 'resource|zas[oó]b|zasob' src frontend/src \
 | grep -vE 'ClassPathResource|ByteArrayResource|core\.io\.Resource|Resource resource\)|resource\.(getInputStream|getDescription)|zasobow statycznych|var resource = new ByteArrayResource|build\(\), resource\)'
```

### Unit Tests:

- `MatchEngineTests`: odrzucenie zagrania bez budżetu z komunikatem, wzrost na turę do `BUDGET_CAP`, efekty `BUDGET_GAIN`/`BUDGET_MAX` z sufitem.
- `MatchFactoryTests`: start 1/1 i 0/0.
- `MatchViewTests`: opis efektu en z „budget”, JSON `budget` bez `resource`.

### Integration Tests:

- `MatchControllerTests`: `budget` w `current` i w każdym kroku `replay[*]`, brak `resource`.
- `match-table.spec.ts`: panel `.budget` (`0/0`, `1/1`), etykiety pl/en, opis efektu, komunikat błędu.

### Manual Testing Steps:

1. Nowa partia: panel „Budżet” 1/1 u zaczynającego, 0/0 u drugiej strony.
2. Kolejne tury: maksimum rośnie o 1, bieżący odnawia się; zagranie karty obniża bieżący.
3. „Kawa z automatu” i „Planowanie sprintu”: opisy budżetu i efekt w panelu.
4. Przełączenie języka na en: „Budget”, „Increase budget by 2”.

## Performance Considerations

Brak — czysta zmiana nazw.

## Migration Notes

Brak bazy; partie w pamięci giną przy wdrożeniu jak dotąd. Kontrakt zmienia się atomowo z SPA w jednym obrazie — pod warunkiem jednego pusha po Fazie 2.

## References

- Research: `context/changes/resource-to-budget/research.md`
- Szablon: `context/archive/2026-11-21-stress-to-hp/plan.md`, `reviews/impl-review.md`
- Wprowadzenie mechaniki: `context/archive/2026-11-19-match-setup-table/plan.md:7,25,51,92,146`

## Progress

> Convention: `- [ ]` pending, `- [x]` done. Append ` — <commit sha>` when a step lands. Do not rename step titles. See `references/progress-format.md`.

### Phase 1: Backend — budżet w silniku, danych, API i tekstach

#### Automated

- [x] 1.1 Testy backendu przechodzą: `./mvnw -B verify` — 92e0db7
- [x] 1.2 Brak nazw zasobu w backendzie: grep z wykluczeniami (Testing Strategy) ograniczony do `src` zwraca pusty wynik — 92e0db7
- [x] 1.3 Kontrola celowego uszkodzenia: przywrócenie nazwy składnika `PlayerView.budget` na `resource` łamie asercje `budget` w `MatchControllerTests` (przywrócone przed commitem) — 92e0db7

#### Manual

- [x] 1.4 Przegląd diffu `factions.json`: zmieniono wyłącznie 2 pola `kind`, żadnych innych pól — 92e0db7

### Phase 2: SPA — budżet w typach, stole i i18n

#### Automated

- [x] 2.1 Testy frontu przechodzą: `cd frontend && npx ng test --watch=false`
- [x] 2.2 Build produkcyjny przechodzi: `cd frontend && npm run build`
- [x] 2.3 Testy backendu dalej zielone: `./mvnw -B verify`
- [x] 2.4 Brak nazw zasobu we froncie: grep z wykluczeniami (Testing Strategy) ograniczony do `frontend/src` zwraca pusty wynik

#### Manual

- [x] 2.5 Po pushu workflow CI (test → image → deploy ze smoke testem) kończy się sukcesem — f6bbfca
- [x] 2.6 Na wdrożonej wersji panel „Budżet” po obu stronach pokazuje 1/1 i 0/0 na starcie, rośnie co turę, zagranie karty obniża bieżący budżet, a „Kawa z automatu” pokazuje „Zwiększ budżet o 2” (pl i en) — f6bbfca

### Phase 3: PRD i kontrola resztek

#### Automated

- [ ] 3.1 Brak „zasób” w PRD: `grep -niE "zas[oó]b|zasob" context/foundation/prd.md` zwraca pusty wynik
- [ ] 3.2 Brak nazw zasobu w kodzie: grep z wykluczeniami (Testing Strategy) po `src frontend/src` zwraca pusty wynik

#### Manual

- [ ] 3.3 Przegląd PRD: opis budżetu odpowiada zachowaniu silnika (start 1/1 i 0/0, +1 na własną turę do 10, koszt karty)
