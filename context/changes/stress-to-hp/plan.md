# Stres → HP gracza — plan implementacji

## Overview

Zastępujemy stres gracza (start 0, 30 = przegrana) przez HP gracza: start 30, obrażenia obniżają, leczenie podnosi maksymalnie do 30, HP ≤ 0 = przegrana. Zmiana obejmuje silnik, dane kart, kontrakt JSON, log, SPA i PRD. Decyzja pochodzi z triage impl-review event-cards (2026-11-21, F1): „stress can be confusing”.

## Current State Analysis

- Stan: `MatchSide.stress` (`game/MatchSide.java:9`), `MatchRules.STRESS_LIMIT = 30` (`game/MatchRules.java:17`), start `0` (`game/MatchFactory.java:66`), kopia robocza `SideState.stress` (`game/MatchEngine.java:566,579,585`).
- Jedno wąskie gardło: `State.addStress(affected, delta)` (`game/MatchEngine.java:526-537`) — stosuje deltę, loguje `StressChanged` przy zmianie, przy `>= STRESS_LIMIT` kończy partię wygraną `affected.opponent()` z `EndReason.STRESS_LIMIT`. Brak sufitu (33/30 legalne i testowane).
- 9 miejsc wywołania (`MatchEngine.java:131,166,174-177,207,216,337-340,342-344,370,400-408`); trzy leczenia mają clamp `-Math.min(amount, stress)` w miejscu wywołania (`:207`, `:216`, `:344`).
- Kontrakt zewnętrzny: `PlayerView`/`OpponentView.stress/stressLimit` (`api/MatchView.java:84-107`, także w `replay[*]`), `EndReason.STRESS_LIMIT`, `EffectKind.STRESS` (3 karty), `AbilityKeyword.ON_PLAY_STRESS` (5 kart) w `src/main/resources/game/factions.json`. Cel `"PLAYER"` jest neutralny.
- Teksty: `api/MatchLogFormatter.java:52-56,76-79,91-113,132-135`, `game/EffectKind.java:69-112`, `game/AbilityKeyword.java:9-53`; komentarze ze „stres” w `game/Move.java`, `api/MatchController.java`, `game/MatchEvent.java:34-35`.
- SPA tylko wyświetla: `match-table.html:17-20,40-57,172-198`, `match-table.css:109-111`, typy `game/match.ts:20-31,136-150`, 6 kluczy i18n, komentarze w `game/game.service.ts:54`, `match-table.ts:155,174`.
- Brak bazy (`MatchStore` w pamięci); SPA jest jedynym konsumentem i wychodzi w tym samym obrazie. CI wdraża przy każdym pushu na `main`.

Szczegóły i pełne kotwice: `context/changes/stress-to-hp/research.md`.

## Desired End State

- Każda strona zaczyna z 30/30 HP. Obrażenia (efekt `PLAYER_DAMAGE`, `DAMAGE` w gracza, `ENEMY_ALL`, zdolność `ON_PLAY_DAMAGE`, atak w gracza, Nadgodziny) obniżają HP; leczenie (`HEAL` w gracza, `OWN_ALL`, zdolność `HEAL`) podnosi je najwyżej do 30.
- HP przechowuje realną wartość poniżej zera (np. −3/30); partia kończy się w chwili, gdy HP strony ≤ 0, wygrywa przeciwnik z `EndReason.HP_DEPLETED`.
- JSON: `player|opponent.hp`, `maxHp` (także w `replay[*]`); `result.reason = "HP_DEPLETED"`; `effects[].kind = "PLAYER_DAMAGE"`, `ability.keyword = "ON_PLAY_DAMAGE"`.
- Log i UI mówią „HP” w obu językach (np. „Twoje HP: 24 → 16/30” / „Your HP: 24 → 16/30”); etykiety zdolności „Obrażenia po zagraniu” / „Damage on play” i „Leczenie po zagraniu” / „Heal on play”.
- `grep -rni "stres" src frontend/src` nie zwraca nic; PRD opisuje HP.

Weryfikacja: zielone `./mvnw -B verify`, `cd frontend && npx ng test --watch=false`, `cd frontend && npm run build`, pusty grep oraz ręczna partia na wdrożonej wersji.

### Key Discoveries:

- Logika „kto przegrywa” (`affected.opponent()` wygrywa) i przerywanie reszty efektów po `finished()` nie zależą od kierunku licznika — przenoszą się bez zmian (`MatchEngine.java:174-177,400-408`).
- Postacie mają już `health` (`api/MatchView.java:116,136`) i efekt `EffectKind.HEALTH` (modyfikator statystyki) — stąd `hp`/`maxHp` dla gracza.
- Polskie liczebniki: istnieje `game/PolishNumberForms.form(value, singular, plural, genitivePlural)` — używaj go dla „obrażenie/obrażenia/obrażeń”.
- `docs/reference/contract-surfaces.md` nie istnieje — z uzgodnionego zakresu „PRD + contract surfaces” zostaje sam PRD.

## What We're NOT Doing

- Zmian mechaniki poza kierunkiem licznika i sufitem leczenia: wartości kart, koszty, zasięgi, reguły Ochrony i celów zostają.
- Scalania `PLAYER_DAMAGE` z `DAMAGE` (nowy zasięg `OPPONENT` na `DAMAGE`) — tylko zmiana nazwy.
- Clampu HP na 0 — wartość ujemna jest pokazywana świadomie.
- Paska HP, kolorów progów, animacji — nadal tekst `hp/maxHp`.
- Edycji `shape-notes.md`, `roadmap.md`, `tech-stack.md` i czegokolwiek w `context/archive/` (historia zostaje).
- Wersjonowania API ani zgodności wstecznej nazw (jedyny konsument wychodzi w tym samym obrazie).

## Implementation Approach

Backend w jednej fazie, bo zmiana nazw w `game` łamie kompilację `api` — engine, dane, widoki, log i testy muszą przejść razem. SPA w drugiej fazie; push dopiero po niej, żeby żadne wdrożenie nie miało SPA czytającego nieistniejące pola. PRD i końcowa kontrola resztek w trzeciej.

## Critical Implementation Details

- **Sufit tylko przy dodatniej delcie.** `changeHp` ogranicza wynik do `MAX_HP` wyłącznie dla leczenia; obrażenia nie mają dolnego ograniczenia. Leczenie przy pełnym HP daje `from == to` — nie loguje `HpChanged` (jak dziś leczenie przy stresie 0).
- **Kolejność wdrożenia.** Nie pushuj po Fazie 1 — CI wdroży backend z nowymi polami i stary SPA pokaże puste HP. Jeden push po Fazie 2 (Faza 3 to tylko dokumenty i może iść osobnym pushem).

## Phase 1: Backend — HP w silniku, danych, API i logu

### Overview

Odwrócenie licznika w jednej funkcji, zmiana nazw w modelu, danych kart i kontrakcie JSON oraz nowe teksty logu/opisów. Wszystkie testy backendu przepisane na HP.

### Changes Required:

#### 1. Stan i reguły

**File**: `src/main/java/com/example/itcardbrawl/game/MatchRules.java`, `MatchSide.java`, `MatchFactory.java`

**Intent**: Zastąpić limit stresu maksymalnym HP i startować obie strony z pełnym HP.

**Contract**: `MatchRules.MAX_HP = 30` (usuwa `STRESS_LIMIT`); składnik rekordu `MatchSide.stress` → `hp`; `MatchFactory` tworzy strony z `MatchRules.MAX_HP` zamiast literału `0`.

#### 2. Zdarzenie i wynik

**File**: `src/main/java/com/example/itcardbrawl/game/MatchEvent.java`, `MatchResult.java`

**Intent**: Nazwy zdarzenia i przyczyny końca mówią o HP.

**Contract**: `StressChanged(side, affected, from, to)` → `HpChanged(side, affected, from, to)` (te same składniki); `EndReason.STRESS_LIMIT` → `HP_DEPLETED`; javadoc `Attacked` („target == null = atak w gracza”).

#### 3. Silnik

**File**: `src/main/java/com/example/itcardbrawl/game/MatchEngine.java`

**Intent**: Jedna funkcja zmiany HP z sufitem leczenia i końcem przy ≤ 0; miejsca wywołania podają ujemną deltę dla obrażeń i dodatnią dla leczenia, bez własnych clampów.

**Contract**: `State.addStress` → `State.changeHp(Side affected, int delta)`; `SideState.stress` → `hp`. Niezmienniki: aktor w logu = `activeSide`; log tylko przy `from != to`; koniec `finish(affected.opponent(), EndReason.HP_DEPLETED, activeSide)` przy `hp <= 0`; wczesne wyjścia po `finished()` (`ENEMY_ALL`, Nadgodziny) bez zmian. Kształt funkcji:

```java
void changeHp(Side affected, int delta) {
	SideState side = side(affected);
	int from = side.hp;
	int to = delta > 0 ? Math.min(from + delta, MatchRules.MAX_HP) : from + delta;
	side.hp = to;
	if (to != from) log(new MatchEvent.HpChanged(activeSide, affected, from, to));
	if (to <= 0) finish(affected.opponent(), EndReason.HP_DEPLETED, activeSide);
}
```

Miejsca: efekt `PLAYER_DAMAGE` i zdolność `ON_PLAY_DAMAGE` → `changeHp(actor.opponent(), -amount)`; `DAMAGE` na `Target.Player` / `ENEMY_ALL`, atak w gracza, Nadgodziny → ujemna delta; `HEAL` na graczu / `OWN_ALL` / zdolność `HEAL` → `changeHp(side, +amount)`. Komentarze „stres” przepisane.

#### 4. Nazwy w danych i opisy

**File**: `src/main/java/com/example/itcardbrawl/game/EffectKind.java`, `AbilityKeyword.java`, `src/main/resources/game/factions.json`

**Intent**: Zmiana nazw enumów używanych w danych kart i opisów na słownictwo HP.

**Contract**: `EffectKind.STRESS` → `PLAYER_DAMAGE` (te same: natychmiastowy, 1–10, zasięg tylko `OPPONENT`); `AbilityKeyword.ON_PLAY_STRESS` → `ON_PLAY_DAMAGE`. `factions.json`: 3 karty `"kind": "STRESS"` (tst-vague-bug-report, neu-daily-standup, neu-deadline) i 5 kart `"keyword": "ON_PLAY_STRESS"` (dev-tech-lead, tst-intern, tst-performance, neu-project-manager, neu-client). Opisy (pl/en, liczebniki przez `PolishNumberForms`):
- `PLAYER_DAMAGE`: „Zadaj przeciwnikowi N obrażeń” / „Deal N damage to the opponent”;
- `DAMAGE ENEMY_ALL`: „… wrogim postaciom i przeciwnikowi” / „… to enemy characters and the opponent”;
- `HEAL TARGET`: „… wybranej postaci lub przywróć graczowi N HP” / „… to a chosen character or restore N HP to a player”; `HEAL OWN_ALL`: „… i przywróć sobie N HP” / „… and restore N HP to yourself”;
- zdolności: `ON_PLAY_DAMAGE` „Po zagraniu: −N HP przeciwnika” / „On play: −N opponent HP”; `HEAL` „Po zagraniu: +N własnego HP” / „On play: +N own HP”; `OVERTIME` „Nadgodziny: na koniec tury −1 HP przeciwnika” / „Overtime: end of turn −1 opponent HP”.

#### 5. Kontrakt API i log

**File**: `src/main/java/com/example/itcardbrawl/api/MatchView.java`, `MatchLogFormatter.java`, `MatchController.java`, `game/Move.java`

**Intent**: Pola JSON i zdania logu w słownictwie HP; komentarze bez „stres”.

**Contract**: `PlayerView`/`OpponentView`: `stress` → `hp`, `stressLimit` → `maxHp` (= `MatchRules.MAX_HP`); `ResultView.reason` wysyła `"HP_DEPLETED"`. Zdania (pl / en):
- `HpChanged`: „Twoje HP: 24 → 16/30” / „Your HP: 24 → 16/30”; „HP przeciwnika: …” / „Opponent HP: …”;
- sufiks celu efektu: „ — Twoje HP” / „ — your HP”, „ — HP przeciwnika” / „ — opponent HP”;
- `ON_PLAY_DAMAGE`, `OVERTIME`: „%s zadaje przeciwnikowi obrażenia: −N” / „%s deals the opponent damage: −N”; „%s zadaje Ci obrażenia: −N” / „%s deals you damage: −N”;
- `HEAL`: „%s leczy Twoje HP: +N” / „%s heals your HP: +N”; „%s leczy HP przeciwnika: +N” / „%s heals opponent HP: +N”;
- atak w gracza: „%s atakuje przeciwnika: −N” / „%s attacks the opponent: −N”; „%s atakuje Cię: −N” / „%s attacks you: −N”;
- koniec: „Wygrywasz: HP przeciwnika spadło do 0” / „You win: opponent's HP dropped to 0”; „Przegrywasz: Twoje HP spadło do 0” / „You lose: your HP dropped to 0”.

#### 6. Testy backendu

**File**: `src/test/java/com/example/itcardbrawl/game/MatchEngineTests.java`, `CardCatalogTests.java`, `MatchFactoryTests.java`, `api/MatchControllerTests.java`, `api/MatchLogFormatterTests.java`

**Intent**: Przepisać asercje na HP (helper `SideBuilder.stress(int)` → `hp(int)`, wartości odwrócone: stres s → HP 30 − s) i dodać testy nowych granic.

**Contract**: Nazwy testów bez „stress” (np. `attackOnStressCanExceedLimitAndEndsMatch` → `attackCanTakeHpBelowZeroAndEndsMatch`: HP 2, atak 5 → −3, `HP_DEPLETED`; `onPlayStressEndsMatchWhenLimitReached` → HP 3 − 3 = 0 kończy partię; `healDoesNotGoBelowZero` → `healDoesNotExceedMaxHp`). Nowe:
- `healIsCappedAtMaxHp` — HP 28, leczenie 5 → `HpChanged(…, 28, 30)`, wynik 30;
- `healAtMaxHpLogsNoHpChange` — HP 30, leczenie → brak `HpChanged`;
- `factoryStartsBothSidesAtMaxHp` (w `MatchFactoryTests`) — obie strony `hp == 30`;
- kontroler: `$.player.hp == 30`, `$.player.maxHp == 30`, brak `$.player.stress`.

### Success Criteria:

#### Automated Verification:

- Testy backendu przechodzą: `./mvnw -B verify`
- Brak „stres” w backendzie: `grep -rni "stres" src` zwraca pusty wynik
- Kontrola celowego uszkodzenia: usunięcie sufitu w `changeHp` łamie `healIsCappedAtMaxHp` (przywrócone przed commitem)

#### Manual Verification:

- Przegląd diffu `factions.json`: zmieniono wyłącznie 3 `kind` i 5 `keyword`, żadnych innych pól

**Implementation Note**: Po zielonych bramkach commit lokalny, bez pusha (SPA jeszcze czyta `stress`).

---

## Phase 2: SPA — HP w typach, stole i i18n

### Overview

Front czyta nowe pola i pokazuje „HP”; specy przepisane na wartości HP. Po tej fazie jeden push i obserwacja wdrożenia.

### Changes Required:

#### 1. Typy i serwis

**File**: `frontend/src/app/game/match.ts`, `frontend/src/app/game/game.service.ts`

**Intent**: Typy odpowiadają nowemu JSON.

**Contract**: `PlayerView`/`OpponentView`: `stress` → `hp`, `stressLimit` → `maxHp`; `EndReason` `'STRESS_LIMIT'` → `'HP_DEPLETED'`; `AbilityKeyword` `'ON_PLAY_STRESS'` → `'ON_PLAY_DAMAGE'`; `EffectKind` `'STRESS'` → `'PLAYER_DAMAGE'`; komentarz `targetId === null` „attacks the opponent player”.

#### 2. Stół

**File**: `frontend/src/app/match-table/match-table.html`, `match-table.css`, `match-table.ts`

**Intent**: Panel gracza i przeciwnika pokazuje `hp/maxHp`; klasy i komentarze bez „stress”.

**Contract**: `div.stress` → `div.hp` z `{{ m.<side>.hp }}/{{ m.<side>.maxHp }}`; klasy `.attack-stress` → `.attack-player`, `.target-stress` → `.target-player` (CSS `:109-111` analogicznie); klucze `texts().matchTable.hp`, `.attackPlayer`.

#### 3. i18n

**File**: `frontend/src/app/i18n/messages.ts`, `pl.ts`, `en.ts`

**Intent**: Teksty HP w obu językach.

**Contract**: `matchTable.stress` → `hp` („HP” / „HP”); `attackStress` → `attackPlayer` („Atakuj gracza” / „Attack player”); `targetPlayer` („Cel: Twoje HP” / „Target: your HP”), `targetOpponent` („Cel: HP przeciwnika” / „Target: opponent's HP”); `keyword.ON_PLAY_DAMAGE` („Obrażenia po zagraniu” / „Damage on play”), `keyword.HEAL` („Leczenie po zagraniu” / „Heal on play”); `reason.HP_DEPLETED` („Przyczyna: HP spadło do 0” / „Reason: HP dropped to 0”).

#### 4. Specy

**File**: `frontend/src/app/match-table/match-table.spec.ts`, `frontend/src/app/new-match/new-match.spec.ts`

**Intent**: Fixture i asercje w HP.

**Contract**: selektor `.stress dd` → `.hp dd`; `'0/30'` → `'30/30'`, `'33/30'` → `'-3/30'`, odtwarzanie `'0/30'` → `'4/30'` staje się `'30/30'` → `'26/30'`; zdania logu w fixture według nowych tekstów backendu (np. „Tech Lead atakuje Cię: −8 (22/30)”); etykiety pl/en według i18n.

### Success Criteria:

#### Automated Verification:

- Testy frontu przechodzą: `cd frontend && npx ng test --watch=false`
- Build produkcyjny przechodzi: `cd frontend && npm run build`
- Testy backendu dalej zielone: `./mvnw -B verify`
- Brak „stres” we froncie: `grep -rni "stres" frontend/src` zwraca pusty wynik

#### Manual Verification:

- Po pushu workflow CI (test → image → deploy ze smoke testem) kończy się sukcesem
- Na wdrożonej wersji partia startuje z 30/30 po obu stronach, atak i wydarzenie obniżają HP, leczenie nie przekracza 30, a spadek do 0 lub poniżej kończy partię komunikatem o HP (pl i en)

---

## Phase 3: PRD i kontrola resztek

### Overview

PRD opisuje regułę HP; końcowe potwierdzenie, że kod nie zawiera słownictwa stresu.

### Changes Required:

#### 1. PRD

**File**: `context/foundation/prd.md`

**Intent**: Reguła biznesowa i wymagania mówią o HP gracza zamiast stresu.

**Contract**: Scenariusz sukcesu `:42-46` (karty zadają obrażenia HP przeciwnika; przegrywa ten, czyje HP spadnie do 0, albo kto nie może dobrać karty), US-01 `:66-70`, FR-007 `:98` („Gracz może w każdej chwili widzieć HP swoje i przeciwnika”), reguła `:113-119` (start 30 HP, obrażenia obniżają, leczenie do 30, HP ≤ 0 = przegrana; zdanie „Stres zastępuje klasyczne życie” usunięte). Jedna linia w changelogu/notatce PRD, jeśli taki ma, wskazująca tę zmianę.

### Success Criteria:

#### Automated Verification:

- Brak „stres” w PRD: `grep -ni "stres" context/foundation/prd.md` zwraca pusty wynik
- Brak „stres” w kodzie: `grep -rni "stres" src frontend/src` zwraca pusty wynik

#### Manual Verification:

- Przegląd PRD: reguła wygranej/przegranej odpowiada zachowaniu silnika (start 30, sufit 30, HP ≤ 0, wartość ujemna widoczna)

---

## Testing Strategy

### Unit Tests:

- `MatchEngineTests`: każde istniejące źródło stresu jako ujemna delta HP; sufit leczenia (28 + 5 → 30), brak logu przy pełnym HP, HP poniżej zera kończy partię, przerwanie reszty efektów po końcu (`ENEMY_ALL`, Nadgodziny), przegrana „na własne życzenie” przez `DAMAGE` w siebie.
- `MatchLogFormatterTests`: dokładne zdania pl/en dla `HpChanged`, ataku, zdolności, sufiksu celu i końca.
- `CardCatalogTests`: walidacja i opisy z nowymi nazwami.

### Integration Tests:

- `MatchControllerTests`: `hp`/`maxHp` w `current` i w `replay[*]`, `reason = "HP_DEPLETED"`.
- `match-table.spec.ts`: wyświetlanie `30/30`, `-3/30`, przejście w odtwarzaniu, etykiety pl/en.

### Manual Testing Steps:

1. Nowa partia obiema frakcjami: obie strony 30/30.
2. Atak w gracza i wydarzenie `PLAYER_DAMAGE` obniżają HP przeciwnika; log pokazuje „HP przeciwnika: 30 → …/30”.
3. Leczenie przy prawie pełnym HP zatrzymuje się na 30.
4. Doprowadzenie HP do 0 lub poniżej: koniec partii z „HP spadło do 0”; przełączenie języka na en.

## Performance Considerations

Brak — ta sama liczba operacji.

## Migration Notes

Brak bazy; partie w pamięci giną przy wdrożeniu jak dotąd. Kontrakt zmienia się atomowo z SPA w jednym obrazie, więc nie ma okresu przejściowego — pod warunkiem jednego pusha po Fazie 2.

## References

- Research: `context/changes/stress-to-hp/research.md`
- Decyzja: `context/archive/2026-11-20-event-cards/follow-ups/review-fixes.md:3-7`
- Wcześniejsza decyzja o realnej wartości ponad limit: `context/archive/2026-11-20-first-full-match/plan.md:42`
- Wąskie gardło: `src/main/java/com/example/itcardbrawl/game/MatchEngine.java:526-537`
- **Prerequisite**: `/10x-archive event-cards` przed Fazą 1 — wykonane (52d5efa)

## Progress

> Convention: `- [ ]` pending, `- [x]` done. Append ` — <commit sha>` when a step lands. Do not rename step titles. See `references/progress-format.md`.

### Phase 1: Backend — HP w silniku, danych, API i logu

#### Automated

- [x] 1.1 Testy backendu przechodzą: `./mvnw -B verify` — bf1bc14
- [x] 1.2 Brak „stres” w backendzie: `grep -rni "stres" src` zwraca pusty wynik — bf1bc14
- [x] 1.3 Kontrola celowego uszkodzenia: usunięcie sufitu w `changeHp` łamie `healIsCappedAtMaxHp` (przywrócone przed commitem) — bf1bc14

#### Manual

- [x] 1.4 Przegląd diffu `factions.json`: zmieniono wyłącznie 3 `kind` i 5 `keyword`, żadnych innych pól — bf1bc14

### Phase 2: SPA — HP w typach, stole i i18n

#### Automated

- [x] 2.1 Testy frontu przechodzą: `cd frontend && npx ng test --watch=false`
- [x] 2.2 Build produkcyjny przechodzi: `cd frontend && npm run build`
- [x] 2.3 Testy backendu dalej zielone: `./mvnw -B verify`
- [x] 2.4 Brak „stres” we froncie: `grep -rni "stres" frontend/src` zwraca pusty wynik

#### Manual

- [ ] 2.5 Po pushu workflow CI (test → image → deploy ze smoke testem) kończy się sukcesem
- [ ] 2.6 Na wdrożonej wersji partia startuje z 30/30 po obu stronach, atak i wydarzenie obniżają HP, leczenie nie przekracza 30, a spadek do 0 lub poniżej kończy partię komunikatem o HP (pl i en)

### Phase 3: PRD i kontrola resztek

#### Automated

- [ ] 3.1 Brak „stres” w PRD: `grep -ni "stres" context/foundation/prd.md` zwraca pusty wynik
- [ ] 3.2 Brak „stres” w kodzie: `grep -rni "stres" src frontend/src` zwraca pusty wynik

#### Manual

- [ ] 3.3 Przegląd PRD: reguła wygranej/przegranej odpowiada zachowaniu silnika (start 30, sufit 30, HP ≤ 0, wartość ujemna widoczna)
