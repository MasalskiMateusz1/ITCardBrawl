---
date: 2026-11-21T10:02:00+01:00
researcher: Claude (claude-opus-5-5)
git_commit: cdc84d3
branch: main
repository: ITCardBrawl
topic: "Zastąpienie stresu gracza (0→30 = przegrana) przez HP gracza (30→0 = przegrana)"
tags: [research, match-engine, match-view, match-table, i18n, factions-json, prd]
status: complete
last_updated: 2026-11-21
last_updated_by: Claude (claude-opus-5-5)
---

# Research: Zastąpienie stresu gracza przez HP gracza

**Date**: 2026-11-21T10:02:00+01:00
**Researcher**: Claude (claude-opus-5-5)
**Git Commit**: cdc84d3
**Branch**: main
**Repository**: ITCardBrawl

## Research Question

Co w kodzie, danych, testach i dokumentach koduje dziś „stres” gracza i jak działa, żeby zaplanować decyzję z 2026-11-21 (`context/changes/event-cards/follow-ups/review-fixes.md:3-7`): gracz zaczyna z 30 HP, obrażenia je obniżają, leczenie podnosi (maks. 30), 0 HP = przegrana — wszędzie zamiast stresu.

## Summary

- Stres to jedna liczba na stronę, `MatchSide.stress` (`src/main/java/com/example/itcardbrawl/game/MatchSide.java:9`), startująca od literału `0` w `MatchFactory.java:66`, z limitem `MatchRules.STRESS_LIMIT = 30` (`MatchRules.java:17`).
- Wszystkie zmiany idą przez `State.addStress(affected, delta)` (`MatchEngine.java:526-537`): dodaje deltę, loguje `StressChanged` tylko gdy wartość się zmieniła, a przy `>= STRESS_LIMIT` kończy partię wygraną `affected.opponent()` z `EndReason.STRESS_LIMIT`. Brak górnego ograniczenia (stres 33 jest legalny i testowany); dolne ograniczenie (`-min(amount, stress)`) leży w trzech miejscach wywołania leczenia.
- `addStress` ma 9 miejsc wywołania w sprawdzonym `MatchEngine.java` (efekty STRESS/DAMAGE/HEAL, zdolności ON_PLAY_STRESS/HEAL, atak w gracza, Nadgodziny). Inwersja to w praktyce jedna funkcja „zmień HP” z odwróconym znakiem i limitem dolnym 0 / górnym 30 oraz przeniesienie clampów.
- Kontrakt zewnętrzny (konsumowany przez SPA, budowane w tym samym obrazie): pola JSON `player|opponent.stress` i `stressLimit` w `PlayerView`/`OpponentView` (`api/MatchView.java:84-107`, także w krokach odtwarzania), `result.reason = "STRESS_LIMIT"`, nazwy enumów w danych `EffectKind.STRESS` i `AbilityKeyword.ON_PLAY_STRESS` (z `factions.json`, wracają do SPA w `EffectView.kind`/`AbilityView.keyword`). Cel `"PLAYER"` w `TargetView`/`TargetRequest` jest już neutralny.
- Nie ma bazy danych ani migracji: partie żyją w pamięci (`MatchStore.java:18`), jedynym konsumentem JSON jest SPA z tego samego obrazu.
- SPA nie liczy nic ze stresu — tylko wyświetla `stress/stressLimit` jako tekst (`match-table.html:17-20`, `:172-175`) i ma 6 kluczy i18n ze stresem. Etykieta „HP” jest już używana dla postaci (`i18n/pl.ts:56`, `en.ts:56`).
- PRD świadomie wybrał stres zamiast życia (`context/foundation/prd.md:115`, „Stres zastępuje klasyczne „życie””) bez podanego uzasadnienia; zmiana odwraca regułę biznesową PRD i FR-007 (`prd.md:98`), więc dokumenty foundation trzeba zaktualizować.

## Detailed Findings

### 1. Stan i reguły (backend)

- Pole i stała: `MatchSide.stress` (`game/MatchSide.java:9`, 4. składnik rekordu), `MatchRules.STRESS_LIMIT = 30` (`game/MatchRules.java:17`); brak osobnej stałej „HP startowe/maks.”.
- Start: obie strony dostają `0` (`game/MatchFactory.java:66`); w sprawdzonej ścieżce nie ma innej wartości startowej.
- Kopia robocza silnika: `SideState.stress` (`game/MatchEngine.java:566`, `:579`, `:585`).
- `addStress` (`game/MatchEngine.java:526-537`): kolejność zastosuj → loguj (`from != to`) → sprawdź `>= LIMIT` → `finish(affected.opponent(), STRESS_LIMIT, activeSide)`; aktor w logu to zawsze `activeSide`.
- Miejsca wywołania (raport backendu, kotwice w `game/MatchEngine.java`):
  - `:131` efekt `STRESS` → `+amount` przeciwnikowi zagrywającego;
  - `:166` `DAMAGE TARGET` na `Target.Player` → `+amount` wskazanej stronie (może to być zagrywający — przegrana „na własne życzenie” jest możliwa i testowana);
  - `:174-177` `DAMAGE ENEMY_ALL` → najpierw stres przeciwnika, powrót, jeśli partia się skończyła;
  - `:207` `HEAL TARGET` na graczu → `-min(amount, stress)`;
  - `:216` `HEAL OWN_ALL` → `-min(amount, own.stress)`;
  - `:337-340` zdolność `ON_PLAY_STRESS` → `+amount` przeciwnikowi (po `AbilityTriggered`);
  - `:342-344` zdolność `HEAL` → `-min(amount, own.stress)`;
  - `:370` atak z `targetId == null` → `+attack` przeciwnikowi (po `Attacked`);
  - `:400-408` `OVERTIME` na koniec tury → `+1` przeciwnikowi za każdą postać, z wyjściem po końcu partii.
- Reguły celu „gracz”: atak w gracza dozwolony tylko bez nieuwięzionej Ochrony u przeciwnika (`:68`, `:76`, egzekwowane `:364-366`); cel `Target.Player` oferowany tylko dla `DAMAGE`/`HEAL` (`supportsPlayer`, `:119-121`), dla obu stron (`:104-108`).
- `MatchService` i `RandomOpponent` nie odwołują się do stresu (raport backendu; `RandomOpponent` wybiera z `legalMoves`).

### 2. Zdarzenia, wynik, log

- `MatchEvent.StressChanged(side, affected, from, to)` (`game/MatchEvent.java:41`); javadoc `Attacked` mówi „target == null = atak w stres” (`:34-35`).
- `MatchResult.EndReason { STRESS_LIMIT, EMPTY_DECK }` (`game/MatchResult.java:9-10`).
- `api/MatchLogFormatter.java`: `StressChanged` jako „Twój stres: x → y/30” (`:52-56`, mianownik `STRESS_LIMIT`), sufiks celu „— Twój stres / stres przeciwnika” (`:76-79`), ON_PLAY_STRESS i OVERTIME „dodaje … stresu” (`:91-95`), HEAL „zmniejsza … stres” (`:96-100`), atak „atakuje stres …” (`:108-113`), koniec „stres … osiągnął limit” (`:132-135`); wszystko pl/en.
- Komentarze: `game/Move.java:16`, `api/MatchController.java:158` („targetId == null = atak w stres”).

### 3. Nazwy w danych (factions.json) i opisy

- `EffectKind.STRESS` (`game/EffectKind.java:9`, natychmiastowy, 1–10, zasięg tylko `OPPONENT`); opisy ze stresem: `STRESS` (`:69-70`), `DAMAGE ENEMY_ALL` (`:97-98`), `HEAL TARGET` (`:106-108`), `HEAL OWN_ALL` (`:111-112`).
- `AbilityKeyword.ON_PLAY_STRESS` i `HEAL` (`game/AbilityKeyword.java:9-13`); opisy „+N stresu przeciwnika” / „−N własnego stresu” / Nadgodziny „+1 stresu przeciwnika” (`:46-53`).
- `factions.json` (raport backendu, numery linii pliku):
  - `ability.keyword: "ON_PLAY_STRESS"` — 5 kart: dev-tech-lead (32), tst-intern (34), tst-performance (41), neu-project-manager (51), neu-client (55);
  - `effects[].kind: "STRESS"` — 3 karty: tst-vague-bug-report (35), neu-daily-standup (46), neu-deadline (53);
  - karty z leczeniem/obrażeniami gracza pod innymi nazwami: HEAL TARGET dev-works-on-my-machine (26), DAMAGE TARGET dev-force-push (29) i tst-edge-case (37), DAMAGE ENEMY_ALL tst-production-bug (42), zdolność HEAL dev-senior (30) i neu-rubber-duck (47), OVERTIME tst-automation (39) i neu-devops (50).
  - Teksty fabularne kart (`text`) w sprawdzonym grepie nie zawierają słowa „stres”.

### 4. Kontrakt API

- `PlayerView(faction, stress, stressLimit, …)` (`api/MatchView.java:84-90`, `stressLimit = MatchRules.STRESS_LIMIT`), `OpponentView` analogicznie (`:100-107`); `ReplayStepView` osadza oba widoki (`:49-56`), więc pola są też w `replay[*]`.
- `ResultView(winner, reason)` (`:227`) wysyła `"STRESS_LIMIT"`.
- `TargetView`/`TargetRequest` z `kind: "PLAYER"` (`api/MatchView.java:183-188`, `api/MatchController.java:142-153`) — nazwa neutralna, bez zmian.
- Brak bazy i zewnętrznych klientów: `MatchStore` w pamięci (`game/MatchStore.java:18`); roadmapa „Data: absent” (`context/foundation/roadmap.md:71`); SPA w tym samym obrazie (`context/foundation/tech-stack.md:32`).

### 5. SPA (frontend/src/app)

- Typy: `PlayerView.stress/stressLimit` (`game/match.ts:136-137`), `OpponentView` (`:149-150`), `EndReason 'STRESS_LIMIT'` (`:20`), `AbilityKeyword 'ON_PLAY_STRESS'` (`:22`), `EffectKind 'STRESS'` (`:31`); `ReplayStep` niesie te same widoki (`:177-184`).
- Wyświetlanie: tekst „{{stress}}/{{stressLimit}}” w `div.stress` dla przeciwnika (`match-table/match-table.html:17-20`) i gracza (`:172-175`); bez paska, progów i kolorów; klasa `.stress` bez reguł CSS. Przyciski: `.attack-stress` („Atakuj stres”, `:40-47`), `.target-stress` dla przeciwnika (`:50-57`) i gracza (`:191-198`); CSS `match-table.css:109-111`.
- Logika po stronie klienta: brak obliczeń ze stresu; `canAttackTarget(null)`/`chooseTarget` są „stresowe” tylko w komentarzach (`match-table.ts:141-158`, `:174`).
- i18n (`i18n/messages.ts`, `pl.ts`, `en.ts`): `matchTable.stress` („Stres”/„Stress”), `attackStress`, `targetPlayer` („Cel: Twój stres”), `targetOpponent`, `keyword.ON_PLAY_STRESS` („Stres po zagraniu”), `reason.STRESS_LIMIT` („Przyczyna: limit stresu”) — 6 kluczy; `keyword.HEAL` to „Ulga po zagraniu” / „Relief on play” (bez słowa stres, ale znaczeniowo ulga od stresu).
- Kolizja nazw: „HP” już oznacza HP postaci (`matchTable.attackHealth`, `pl.ts:56`, `en.ts:56`; renderowane `match-table.html:72`, `:213`, `:293`).
- Teksty logu i efektów przychodzą zlokalizowane z backendu; SPA ich nie składa.

### 6. Testy (liczby wzmianek z raportów)

- Backend:
  - `MatchEngineTests` — 56 wzmianek, ~20 testów zależnych od kierunku/limitu (np. `attackOnStressCanExceedLimitAndEndsMatch` 28+5=33, `onPlayStressEndsMatchWhenLimitReached` 27+3=30, `healDoesNotGoBelowZero`, `overtimeCanEndMatchAndStopsRemainingSteps`, `ownStressDamageAtLimitLosesAndStopsRemainingEffects`, `logRecordsPlayAttackAndEndTurnInOrder` z dokładnym `StressChanged(PLAYER, OPPONENT, 0, 3)`); helper `SideBuilder.stress(int)`.
  - `MatchLogFormatterTests` — 14 wzmianek, dokładne zdania „Twój stres: 16 → 24/30”, „atakuje Twój stres: +8”, sufiksy celu.
  - `MatchControllerTests` — 7 wzmianek (`$.player.stressLimit == 30`, `$.opponent.stress == 5` itd.).
  - `CardCatalogTests` — 5 (komunikat walidacji „zdolność ON_PLAY_STRESS”, dokładne opisy zdolności).
  - `MatchFactoryTests` — 1 (`stress().isZero()`).
- Frontend: `match-table.spec.ts` — 51 linii (m.in. `'0/30'`, `'33/30'`, przejście `'0/30'`→`'4/30'` w odtwarzaniu, etykiety pl/en, „Przyczyna: limit stresu”); `new-match.spec.ts` — 4 linie fixture.

## Code References

- `src/main/java/com/example/itcardbrawl/game/MatchEngine.java:526-537` — `addStress`: delta, log, limit, `finish`
- `src/main/java/com/example/itcardbrawl/game/MatchEngine.java:131,166,174-177,207,216,337-344,370,400-408` — miejsca wywołania
- `src/main/java/com/example/itcardbrawl/game/MatchSide.java:9` — pole `stress`
- `src/main/java/com/example/itcardbrawl/game/MatchRules.java:17` — `STRESS_LIMIT = 30`
- `src/main/java/com/example/itcardbrawl/game/MatchFactory.java:66` — start 0
- `src/main/java/com/example/itcardbrawl/game/MatchEvent.java:34-41` — `Attacked` javadoc, `StressChanged`
- `src/main/java/com/example/itcardbrawl/game/MatchResult.java:9-10` — `STRESS_LIMIT`
- `src/main/java/com/example/itcardbrawl/game/EffectKind.java:9,69-112` — `STRESS` i opisy
- `src/main/java/com/example/itcardbrawl/game/AbilityKeyword.java:9-13,46-53` — `ON_PLAY_STRESS`, `HEAL`, opisy
- `src/main/java/com/example/itcardbrawl/api/MatchView.java:49-56,84-107,227` — widoki i wynik
- `src/main/java/com/example/itcardbrawl/api/MatchLogFormatter.java:52-56,76-79,91-113,132-135` — zdania logu
- `src/main/resources/game/factions.json` — 5 kart ON_PLAY_STRESS, 3 karty STRESS
- `frontend/src/app/game/match.ts:20-31,136-150,177-184` — typy
- `frontend/src/app/match-table/match-table.html:17-20,40-57,172-198` — wyświetlanie i przyciski
- `frontend/src/app/i18n/{messages,pl,en}.ts` — 6 kluczy ze stresem

## Architecture Insights

- Jedno wąskie gardło: każda zmiana stresu przechodzi przez `addStress`, więc inwersja jest lokalna — zmiana znaku i warunku końca w jednej funkcji plus przeniesienie trzech clampów leczenia (dziś w miejscach wywołania) do tej funkcji albo zamiana na `min(amount, MAX - hp)`.
- Logika „kto przegrywa” (`affected.opponent()` wygrywa) i przerywanie reszty efektów po `finished()` nie zależą od kierunku licznika — przenoszą się bez zmian.
- Kontrakt JSON i nazwy enumów w danych to jedyne zewnętrzne nazwy; SPA jest jedynym konsumentem i jest wdrażane razem z backendem, więc zmiana nazw pól nie wymaga wersjonowania API.
- SPA jest „głupie” wobec stresu (tylko interpolacja), więc po stronie frontu to zmiana typów, szablonu, i18n i fixture.

## Historical Context (from prior changes)

- `context/foundation/prd.md:115` — „Stres zastępuje klasyczne „życie”” (decyzja bez uzasadnienia); `:113`, `:117`, `:119` — reguły wygranej, natychmiastowego końca i limitu 30; FR-007 (`:98`) — widoczność stresu obu stron; scenariusz sukcesu `:42-46`, US-01 `:66-70`. Wsparte: kod realizuje te reguły (`MatchEngine.java:526-537`).
- `context/foundation/shape-notes.md:28-35,184,193` — decyzje „tylko stres w v1”, limit 30 (rozstrzygnięte 2026-11-16), dług techniczny odłożony.
- `context/foundation/roadmap.md:32,47,124,137,195,215,227-228` — S-02 i S-03 (done) opisują wynik w kategoriach stresu; S-04 event-cards in-progress (`:49`).
- `context/foundation/tech-stack.md:24` — „stress limit 30” w uzasadnieniu stosu.
- `context/archive/2026-11-20-first-full-match/plan.md:42` i `plan-brief.md:30,58` — decyzja użytkownika: stres przechowuje realną wartość bez sufitu (np. 33/30); plan `:218,242` — HEAL nie schodzi poniżej 0; `:53,103` — Nadgodziny +1; `:222,241,458` — Ochrona blokuje atak w stres; `plan-brief.md:23` — atak w stres daje wybór „walka o stół vs presja”. Wsparte przez obecny kod i testy (`attackOnStressCanExceedLimitAndEndsMatch`).
- `context/archive/2026-11-19-match-setup-table/plan.md:92,94,146` — pierwotny kontrakt `player: { faction, stress, stressLimit, … }`.
- `context/changes/event-cards/plan.md:54-65` — `STRESS OPPONENT`, `ENEMY_ALL`/`OWN_ALL` obejmują stres; „obrażenia gracza = +N stresu; leczenie gracza = −N stresu, nie poniżej 0”.
- `context/changes/event-cards/follow-ups/review-fixes.md:3-7` — decyzja HP 30→0 (2026-11-21) i szacowany zakres; sugerowany id `player-hp`, faktyczny folder `stress-to-hp`.
- Pliki w `context/archive/` są niezmienne (CLAUDE.md) — ich słownictwo stresu zostaje.

## Related Research

- `context/archive/2026-11-20-first-full-match/research.md` — jak atak i HP przekładają się na stres (`:197-229`).
- `context/archive/2026-11-19-ui-in-english-and-polish/research.md:46` — etykieta „Stres” w i18n.

## Open Questions

Decyzje produktowe, których decyzja z 2026-11-21 nie rozstrzyga (do `/10x-plan`):

1. **HP poniżej 0** — dziś stres pokazuje realną wartość ponad limit (33/30, decyzja użytkownika z first-full-match). Czy HP ma schodzić poniżej 0 (np. −3/30), czy zatrzymywać się na 0?
2. **Nazwy kontraktu** — `stress`/`stressLimit` → np. `hp`/`maxHp`; `EndReason.STRESS_LIMIT` → np. `HP_DEPLETED`; `EffectKind.STRESS` → scalić z `DAMAGE`-na-przeciwnika czy przemianować; `AbilityKeyword.ON_PLAY_STRESS` → np. `ON_PLAY_DAMAGE`. Zmiana nazw enumów wymaga zmiany `factions.json`.
3. **Słownictwo fabularne** — PRD wybrał „stres” jako motyw IT. Jak nazwać HP gracza po polsku/angielsku, żeby nie kolidowało z HP postaci („HP”, „Zdrowie”, „Morale”?), i czy zdolność HEAL („Ulga po zagraniu”) zmienia nazwę.
4. **Zakres dokumentów** — aktualizacja PRD (reguła `:113-119`, FR-007), shape-notes i roadmapy (opisy S-02/S-03 zakończonych slice’ów) czy tylko PRD + notatka.
5. **Kolejność względem event-cards** — event-cards ma status `impl_reviewed` i nie jest zarchiwizowane; czy archiwizować je przed rozpoczęciem tej zmiany.
