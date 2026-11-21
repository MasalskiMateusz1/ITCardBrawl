<!-- IMPL-REVIEW-REPORT -->
# Implementation Review: Stres → HP gracza

- **Plan**: context/changes/stress-to-hp/plan.md
- **Scope**: Full plan
- **Reviewed phases**: 1, 2, 3
- **Date**: 2026-11-21
- **Verdict**: APPROVED
- **Findings**: 0 critical, 1 warning, 6 observations

## Verdicts

| Dimension | Verdict |
|-----------|---------|
| Plan Adherence | PASS |
| Scope Discipline | PASS |
| Safety & Quality | PASS |
| Architecture | PASS |
| Pattern Consistency | WARNING |
| Success Criteria | WARNING |

Automated criteria re-run 2026-11-21: `./mvnw -B verify` PASS, `npx ng test --watch=false` PASS (93/93), `npm run build` PASS, `grep -rni "stres" src frontend/src context/foundation/prd.md` empty. All Manual rows checked by user.

## Findings

### F1 — Brak testów kontrolera dla replay[*].hp/maxHp i reason HP_DEPLETED

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Success Criteria
- **Location**: src/test/java/com/example/itcardbrawl/api/MatchControllerTests.java:790-808
- **Detail**: Testing Strategy (Integration Tests) wymaga `hp`/`maxHp` w `current` i w `replay[*]` oraz `reason = "HP_DEPLETED"`. `endTurnReturnsReplayStepsWithGrowingLogSize` sprawdza tylko istnienie kluczy `player`/`opponent` w krokach replay; żaden test kontrolera nie sprawdza `result.reason`. HP_DEPLETED pokryte tylko w silniku, formaterze i specu SPA. Ryzyko niskie (te same rekordy widoku).
- **Fix**: Dodać asercje `replay[*].player.hp`/`maxHp` w istniejącym teście oraz `MatchViewTests` dla `result.reason == "HP_DEPLETED"` (kontroler trudno doprowadzić do końca partii).
- **Decision**: FIXED (asercje hp/maxHp w replay w MatchControllerTests + MatchViewTests.finishedMatchSerializesHpBelowZeroAndHpDepletedReason)

### F2 — „HP spadło do 0” przy wartości ujemnej

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Adherence
- **Location**: src/main/java/com/example/itcardbrawl/api/MatchLogFormatter.java (koniec partii), frontend/src/app/i18n/pl.ts / en.ts `reason.HP_DEPLETED`
- **Detail**: HP celowo może zejść poniżej 0 i jest widoczne (−3/30), a komunikat końca mówi „spadło do 0”. Tekst jest dosłownie z planu — to wybór planu, nie dryf.
- **Fix**: Opcjonalnie „spadło do 0 lub niżej” / „dropped to 0 or below” w logu i i18n (+ testy), albo zaakceptować.
- **Decision**: FIXED („do 0 lub niżej” / „to 0 or below” w logu, i18n i testach)

### F3 — Niezgrabne angielskie zdanie logu zdolności

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Pattern Consistency
- **Location**: src/main/java/com/example/itcardbrawl/api/MatchLogFormatter.java:388
- **Detail**: „%s deals the opponent damage: −N” / „%s deals you damage: −N” brzmi nienaturalnie (tekst z planu).
- **Fix**: „%s damages the opponent: −N” / „%s damages you: −N” + aktualizacja `MatchLogFormatterTests`.
- **Decision**: FIXED („damages the opponent / damages you”)

### F4 — Dwa różne znaki minus w logu

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Pattern Consistency
- **Location**: src/main/java/com/example/itcardbrawl/api/MatchLogFormatter.java:53-56
- **Detail**: Kwoty używają U+2212 („−8”), a ujemne HP formatowane `%d` daje ASCII „-3” („2 → -3/30”). UI (`-3/30`) też używa ASCII.
- **Fix**: Zostawić (spójne z UI) albo formatować ujemne HP przez U+2212 w logu.
- **Decision**: FIXED (helper `hp(int)` w MatchLogFormatter — ujemne HP z U+2212 w logu; panel UI bez zmian)

### F5 — Styl polskich opisów efektów

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Pattern Consistency
- **Location**: src/main/java/com/example/itcardbrawl/game/EffectKind.java:99, :560
- **Detail**: `ENEMY_ALL` „wrogim postaciom i przeciwnikowi” odbiega od sąsiedniego `ENEMY_BOARD` „postaciom przeciwnika”; `HEAL TARGET` powtarza „przywróć … lub przywróć graczowi”. Gramatycznie poprawne, teksty z planu.
- **Fix**: Opcjonalnie „… wybranej postaci lub graczowi N HP”; reszta bez zmian.
- **Decision**: FIXED (HEAL TARGET: „… wybranej postaci lub graczowi N HP”; ENEMY_ALL bez zmian)

### F6 — Zdolność HEAL loguje „+N” przy pełnym HP

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: src/main/java/com/example/itcardbrawl/game/MatchEngine.java:339-342
- **Detail**: `AbilityTriggered` („leczy Twoje HP: +3”) pojawia się nawet gdy HP = 30 i nic się nie zmienia. Zachowanie istniało wcześniej (przy stresie 0); `HpChanged` poprawnie nie jest logowane.
- **Fix**: Zaakceptować jako istniejące zachowanie (lub w przyszłości logować faktycznie przywróconą wartość).
- **Decision**: SKIPPED

### F7 — Higiena testów: duplikat i literał 30

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Pattern Consistency
- **Location**: src/test/java/com/example/itcardbrawl/game/MatchEngineTests.java:156, :391; src/test/java/com/example/itcardbrawl/api/MatchViewTests.java:180,188
- **Detail**: `healIsCappedAtMaxHp` (:391) i `healsHpAndCharacterDamageWithoutExceedingMaximum` (:156) testują to samo (HEAL 5 przy 28 → 30). `MatchViewTests` używa literału `30`, pozostałe testy `MatchRules.MAX_HP`.
- **Fix**: Zamienić literał na `MatchRules.MAX_HP` w `MatchViewTests`; duplikat zostawić (nazwany test z planu).
- **Decision**: FIXED (literał 30 → MatchRules.MAX_HP w MatchViewTests; duplikat testu zostawiony)
