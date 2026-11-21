# Stres → HP gracza — Plan Brief

> Full plan: `context/changes/stress-to-hp/plan.md`
> Research: `context/changes/stress-to-hp/research.md`

## What & Why

Zastępujemy stres gracza (start 0, 30 = przegrana) przez HP gracza: start 30, obrażenia obniżają, leczenie podnosi najwyżej do 30, HP ≤ 0 = przegrana. Decyzja z triage impl-review event-cards (2026-11-21): stres był mylący — „HP” to licznik, który każdy gracz rozumie od razu.

## Starting Point

Każda zmiana stresu przechodzi przez jedną funkcję `State.addStress` (`MatchEngine.java:526-537`, 9 wywołań). Nazwy stresu są w kontrakcie JSON (`stress`/`stressLimit`, `STRESS_LIMIT`), danych kart (`STRESS` ×3, `ON_PLAY_STRESS` ×5 w `factions.json`), logu, SPA (tylko wyświetlanie) i PRD. Brak bazy; SPA wychodzi w tym samym obrazie co backend.

## Desired End State

Gracz widzi „HP 30/30” po obu stronach na starcie; ataki i wydarzenia zbijają HP, leczenie zatrzymuje się na 30, a spadek do 0 lub niżej (widoczne np. −3/30) kończy partię komunikatem „HP spadło do 0”. W kodzie, danych i PRD nie ma już słowa „stres”.

## Key Decisions Made

| Decision | Choice | Why (1 sentence) | Source |
| --- | --- | --- | --- |
| Kierunek licznika | HP 30→0, leczenie z sufitem 30 | Decyzja F1 z impl-review event-cards | Research |
| HP poniżej 0 | Pokazujemy realną wartość (−3/30) | Symetria z wcześniejszą decyzją „realna wartość ponad limit” (33/30) | Plan |
| Nazwy kontraktu | `hp`/`maxHp`, `MAX_HP`, `HpChanged`, `HP_DEPLETED`, `ON_PLAY_DAMAGE` | „HP wszędzie”, a `health` jest już zajęte przez postacie | Plan |
| Efekt `STRESS` | Zmiana nazwy na `PLAYER_DAMAGE`, bez scalania z `DAMAGE` | Czysta zmiana nazwy bez zmiany zasięgów i walidacji | Plan |
| Słownictwo UI/log | „HP” w pl i en („Twoje HP: 24 → 16/30”) | Termin użytkownika; pozycja panelu i „Twoje/przeciwnika” odróżniają od HP postaci | Plan |
| Etykiety zdolności | „Leczenie / Obrażenia po zagraniu”, „Heal / Damage on play” | „Ulga” straciła sens bez stresu | Plan |
| Dokumenty | Tylko PRD (contract-surfaces.md nie istnieje); archiwum, shape-notes, roadmapa bez zmian | Żywe źródło prawdy zgodne z kodem, historia uczciwa | Plan |
| Kolejność | `/10x-archive event-cards` przed Fazą 1 | Jedna aktywna zmiana na silniku naraz | Plan |
| Wdrożenie | Jeden push po Fazie 2 | Push po samym backendzie wdrożyłby SPA czytające nieistniejące pola | Plan |

## Scope

**In scope:**
- `changeHp` z sufitem leczenia i końcem przy ≤ 0; zmiany nazw w modelu, zdarzeniach, wyniku, enumach i `factions.json`
- Pola JSON `hp`/`maxHp` (także w `replay`), teksty logu i opisów pl/en
- SPA: typy, szablon, CSS, i18n, specy
- PRD (scenariusz, US-01, FR-007, reguła biznesowa)

**Out of scope:**
- Zmiany mechaniki poza kierunkiem i sufitem; scalanie `PLAYER_DAMAGE` z `DAMAGE`
- Clamp HP na 0, pasek HP, kolory, animacje
- shape-notes, roadmap, tech-stack, `context/archive/`
- Wersjonowanie API

## Architecture / Approach

Inwersja jest lokalna: jedna funkcja `changeHp(affected, delta)` (ujemna delta = obrażenia bez dolnego limitu, dodatnia = leczenie do `MAX_HP`) zastępuje `addStress` i trzy clampy w miejscach wywołania. Logika „kto przegrywa” i przerywanie efektów po końcu partii nie zależą od kierunku licznika. Reszta to mechaniczna zmiana nazw w kontrakcie przechodzącym backend → JSON → SPA.

## Phases at a Glance

| Phase | What it delivers | Key risk |
| --- | --- | --- |
| 1. Backend — HP w silniku, danych, API i logu | `changeHp`, zmiany nazw, nowe teksty, testy (sufit, HP < 0) | ~80 asercji z dokładnymi wartościami do odwrócenia (s → 30 − s) |
| 2. SPA — HP w typach, stole i i18n | Front czyta `hp/maxHp`, etykiety „HP”; push + obserwacja wdrożenia | Fixture z tekstami logu muszą odpowiadać nowym zdaniom backendu |
| 3. PRD i kontrola resztek | PRD opisuje HP; grep „stres” pusty | Pominięte wzmianki w PRD poza wskazanymi liniami |

**Prerequisites:** `/10x-archive event-cards` (status `impl_reviewed`).
**Estimated effort:** ~1–2 sesje w 3 fazach; Faza 1 jest największa (głównie testy).

## Open Risks & Assumptions

- Ujemne HP (−3/30) może wyglądać dla graczy jak błąd — świadomy wybór; łatwo zmienić na clamp w jednej funkcji.
- Zakładamy, że poza SPA nikt nie czyta JSON (brak zewnętrznych klientów wg research).
- „HP” oznacza na ekranie zarówno HP gracza, jak i postaci — rozróżnienie wyłącznie kontekstem panelu.

## Success Criteria (Summary)

- Partia od 30/30 do HP ≤ 0 działa w pl i en na wdrożonej wersji; leczenie nie przekracza 30.
- `./mvnw -B verify`, `ng test`, `npm run build` zielone; `grep -rni "stres" src frontend/src` pusty.
- PRD opisuje tę samą regułę, którą realizuje silnik.
