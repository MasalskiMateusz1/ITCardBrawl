<!-- IMPL-REVIEW-REPORT -->
# Implementation Review: Zasób → budżet

- **Plan**: context/changes/resource-to-budget/plan.md
- **Scope**: Full plan
- **Reviewed phases**: 1, 2, 3
- **Date**: 2026-11-21
- **Verdict**: APPROVED
- **Findings**: 0 critical, 1 warning, 4 observations

## Verdicts

| Dimension | Verdict |
|-----------|---------|
| Plan Adherence | WARNING |
| Scope Discipline | PASS |
| Safety & Quality | PASS |
| Architecture | PASS |
| Pattern Consistency | WARNING |
| Success Criteria | PASS |

Automated criteria re-run 2026-11-21: `./mvnw -B verify` PASS, `npx ng test --watch=false` PASS (93/93), `npm run build` PASS, grep z wykluczeniami po `src frontend/src` — 0 linii, `grep -niE "zas[oó]b|zasob" context/foundation/prd.md` pusty. Wszystkie wiersze Manual odhaczone przez użytkownika. Commit 6e6031f (teksty obrażeń zdolności) jest poza planem, ale na wyraźną prośbę użytkownika — spójny, bez resztek starych tekstów.

## Findings

### F1 — Opis BUDGET_GAIN myli się z BUDGET_MAX

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Pattern Consistency
- **Location**: src/main/java/com/example/itcardbrawl/game/EffectKind.java:77
- **Detail**: `BUDGET_GAIN` jednorazowo podnosi tylko bieżący budżet (może przekroczyć maksimum, do `BUDGET_CAP`, `MatchEngine.java:139-142`), a opis „Zwiększ budżet o N” / „Increase budget by N” brzmi prawie jak `BUDGET_MAX` „Zwiększ maksymalny budżet o N”. Stare „Zyskaj N pkt zasobu” lepiej mówiło, że to jednorazowy zysk. Tekst wybrany w planie.
- **Fix**: „Dodaj N do bieżącego budżetu” / „Add N to current budget” (+ asercje w MatchViewTests i match-table.spec.ts).
- **Decision**: FIXED (differently: „Dodaj N do budżetu w tej turze” / „Add N to your budget this turn”)

### F2 — Literał 10 zamiast BUDGET_CAP w dwóch testach

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Adherence
- **Location**: src/test/java/com/example/itcardbrawl/api/MatchControllerTests.java:552, src/test/java/com/example/itcardbrawl/api/MatchViewTests.java:221
- **Detail**: Plan: „stałe przez `MatchRules.BUDGET_CAP`, nie literał `10`”. `MatchEngineTests` przepisane, ale fixture `seededTable` i helper `side()` w `MatchViewTests` nadal mają `10, 10`.
- **Fix**: Zamienić `10, 10` na `MatchRules.BUDGET_CAP, MatchRules.BUDGET_CAP`.
- **Decision**: FIXED (MatchRules.BUDGET_CAP w obu fixture)

### F3 — Mieszany styl logu: zdolności „N obrażeń”, ataki „−N”

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Pattern Consistency
- **Location**: src/main/java/com/example/itcardbrawl/api/MatchLogFormatter.java:111-114
- **Detail**: Po 6e6031f zdolności logują „Tech Lead zadaje przeciwnikowi 4 obrażenia”, a atak w gracza nadal „Tech Lead atakuje przeciwnika: −8” / „atakuje Cię: −8”.
- **Fix**: Zostawić (atak to inne zdarzenie) albo ujednolicić: „%s atakuje przeciwnika i zadaje N obrażeń” / „%s attacks the opponent for N damage” (+ testy formatera i fixture SPA).
- **Decision**: FIXED (atak w gracza: „atakuje przeciwnika / Cię i zadaje N obrażeń” / „attacks the opponent / you for N damage”)

### F4 — Formy „obrażenie/obrażenia/obrażeń” powtórzone w 4 miejscach

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Pattern Consistency
- **Location**: src/main/java/com/example/itcardbrawl/game/EffectKind.java:71,94; AbilityKeyword.java:47; api/MatchLogFormatter.java:143
- **Detail**: Ten sam zestaw form rzeczownika wpisany cztery razy.
- **Fix**: Dodać `PolishNumberForms.damage(int)` i użyć go we wszystkich czterech miejscach.
- **Decision**: FIXED (PolishNumberForms.damage(int) w 4 miejscach)

### F5 — Kolejność importu i brak przypadku 5+ obrażeń w testach zdolności

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Pattern Consistency
- **Location**: src/main/java/com/example/itcardbrawl/api/MatchLogFormatter.java:7; src/test/java/com/example/itcardbrawl/game/CardCatalogTests.java:583
- **Detail**: Import `PolishNumberForms` wstawiony między `MatchEvent` a `MatchEvent.AbilityTriggered`. Testy nowych tekstów sprawdzają tylko 1 i 4 (formy „obrażenie”, „obrażenia”), nie „obrażeń”.
- **Fix**: Przenieść import za blok `MatchEvent.*`; dodać asercję `ON_PLAY_DAMAGE.describe(5)` → „Po zagraniu: zadaj przeciwnikowi 5 obrażeń”.
- **Decision**: FIXED (import przeniesiony, asercja describe(5) → „5 obrażeń”)
