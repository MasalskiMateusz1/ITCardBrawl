# Zasób → budżet — Plan Brief

> Full plan: `context/changes/resource-to-budget/plan.md`
> Research: `context/changes/resource-to-budget/research.md`

## What & Why

Zmieniamy nazwę zasobu, którym gracz płaci za zagrywanie kart („Zasób”, `resource`), na „Budżet” (`budget`) we wszystkich warstwach. Mechanika zostaje bez zmian.

## Starting Point

Zasób to dwie liczby na stronę (`MatchSide.resourceCurrent/resourceMax`, limit `RESOURCE_CAP = 10`), używane w 7 miejscach silnika. W kontrakcie JSON to `player|opponent.resource`, w danych kart 2 efekty `RESOURCE`/`RESOURCE_MAX`, w tekstach 3 łańcuchy backendu i jedna etykieta SPA. PRD w ogóle nie opisuje zasobu.

## Desired End State

Gracz widzi panel „Budżet” / „Budget” (1/1 i 0/0 na starcie, +1 na turę do 10). „Kawa z automatu” mówi „Zwiększ budżet o 2”, a za drogie zagranie zwraca „Za mało budżetu na zagranie karty”. W kodzie, danych, JSON i PRD nie ma już słowa „zasób/resource” w tym znaczeniu.

## Key Decisions Made

| Decision | Choice | Why (1 sentence) | Source |
| --- | --- | --- | --- |
| Zakres | Pełna zmiana nazwy we wszystkich warstwach, mechanika bez zmian | Jak przy stress-to-hp, bez mieszania nazw w kodzie i UI | Research |
| Nazwy kodu/JSON | `budgetCurrent/budgetMax`, `BUDGET_CAP`, `BudgetView`, `player.budget.{current,max}` | Ten sam kształt co dziś, łatwy grep | Plan |
| Enumy efektów | `BUDGET_GAIN`, `BUDGET_MAX` | Nazwa efektu mówi, co robi (wybór użytkownika) | Plan |
| Teksty | „Zwiększ budżet o N” / „Increase budget by N”, „Zwiększ maksymalny budżet o N”, „Za mało budżetu na zagranie karty”, „Budżet” / „Budget” | Bez ciężkiego „pkt budżetu” i niepoliczalnego „Gain N budget” | Plan |
| PRD | Dopisać zdanie o budżecie; „zasobem przegranej” → „licznikiem przegranej” | PRD opisuje regułę widoczną dla gracza, grep po PRD czysty | Plan |
| Karty | Nazwy i teksty fabularne bez zmian; tylko 2 pola `kind` | Zmiana nazwy, nie przepisywanie żartów | Plan |
| Wdrożenie | Jeden push po Fazie 2 | Push po samym backendzie wdrożyłby SPA czytające nieistniejące `resource` | Research |
| Kryterium resztek | Grep z wykluczeniami dla Springa i „zasobów statycznych” | Naiwny grep nigdy nie będzie pusty | Research |

## Scope

**In scope:**
- Model, reguły, silnik, enumy, `factions.json` (2 pola), `BudgetView`, teksty pl/en, komunikat błędu
- Testy backendu, w tym `budget` w `replay[*]` i komunikat odrzucenia
- SPA: typy, panel stołu, i18n, specy
- PRD §Business Logic

**Out of scope:**
- Zmiany mechaniki i pola `cost`
- Nazwy i teksty fabularne kart
- Nowe zdarzenie logu o budżecie
- shape-notes, roadmap, tech-stack, archiwum

## Architecture / Approach

Trzy fazy jak w stress-to-hp. Backend idzie razem, bo zmiana nazw w `game` łamie kompilację `api`, i kończy się commitem lokalnym bez pusha. SPA kończy się jedynym pushem, bo CI wdraża każdy push na `main`. Na końcu PRD i grep resztek.

## Phases at a Glance

| Phase | What it delivers | Key risk |
| --- | --- | --- |
| 1. Backend | Nazwy budżetu w silniku, danych, JSON i tekstach; testy na nowych nazwach | Pominięte użycie w testach (ok. 45 wywołań `SideBuilder.resource`) |
| 2. SPA | Panel „Budżet”, typy, i18n, specy; push i wdrożenie | Push przed tą fazą = pusty panel na produkcji |
| 3. PRD i resztki | Zdanie o budżecie w PRD; pusty grep | Grep z wykluczeniami przepuści nową nazwę Springa |

**Prerequisites:** brak aktywnej zmiany na silniku (stress-to-hp zarchiwizowany, `7d5c40b`).
**Estimated effort:** ~1 sesja, 3 fazy.

## Open Risks & Assumptions

- Zakładamy, że SPA jest jedynym konsumentem JSON (jak w stress-to-hp), więc nie potrzebujemy zgodności wstecznej.
- Commit archiwizacji `7d5c40b` jest jeszcze niewypchnięty; pójdzie razem z pushem po Fazie 2.

## Success Criteria (Summary)

- Gracz widzi „Budżet” zamiast „Zasób” w pl i en, a gra działa jak wcześniej.
- `./mvnw -B verify`, testy i build frontu są zielone, a grep z wykluczeniami jest pusty.
