# Wybór frakcji i stół partii — Plan Brief

> Full plan: `context/changes/match-setup-table/plan.md`

## What & Why

Element roadmapy S-02: zalogowany gracz wybiera frakcję (Developerzy albo Testerzy), rozpoczyna partię przeciwko komputerowi i widzi stół — rękę, liczbę kart w taliach, stres obu stron (0/30), zasób i czyja jest tura. To model partii i stały widok stanu (FR-007), na którym S-03 zbuduje zagrania i pętlę gry.

## Starting Point

Działa logowanie (GitHub/Google), lobby z powitaniem i wdrożenie po merge do main. Backend nie ma kodu domeny ani bazy; PRD nie określał zasad tury — ten plan je ustala.

## Desired End State

Lobby → „Nowa partia” → `/new-match` (wybór 1 z 2 frakcji) → `/match` ze stołem: 5 kart na ręce gracza, 25/25 w taliach, stres 0/30 obu stron, zaczynający z zasobem 1/1, druga strona 0/0, tekst tury. Ręka komputera ukryta (widać tylko liczby). Odświeżenie wznawia partię, nowy start ją zastępuje.

## Key Decisions Made

| Decision | Choice | Why (1 sentence) |
| --- | --- | --- |
| Ręka startowa | 5 kart (w talii zostaje 25) | Wybór taktyczny od pierwszej tury, talia wystarcza na partię ≤ 15 min. |
| Kto zaczyna | Losowo (serwer) | Brak stałej przewagi pierwszego ruchu po żadnej stronie. |
| Zasób | Od 1 w pierwszej własnej turze, +1 na turę, limit 10; karty mają koszt 1–10 | Daje taktykę i tempo partii (rdzeń FR-004). |
| Dobieranie | 1 karta na początku tury; zaczynający nie dobiera w pierwszej turze | Wyrównuje przewagę pierwszego ruchu bez dodatkowych mechanik. |
| Frakcje | Developerzy i Testerzy (grywalne) + niegrywalna pula neutralna w obu taliach | Decyzja użytkownika; zgodne z PRD (wybór 1 z 2), neutralne karty współdzielone. |
| Talie | Placeholdery agenta, zdefiniowane raz w `factions.json` i używane w każdej partii | S-02 nie czeka na treść; S-06 podmienia dane bez zmian kodu. |
| Stan partii | Pamięć serwera, jedna partia na gracza | Serwer źródłem prawdy; karty komputera nie trafiają do przeglądarki. |
| Widoczność przeciwnika | Tylko liczby (ręka, talia, stres, zasób) | Klasyczna gra karciana, jawny stan publiczny (FR-007). |
| Odświeżenie / ponowny start | Odświeżenie wznawia; nowy start zastępuje | Proste i odporne na przypadkowe F5. |
| Przepływ UI | Osobny ekran `/new-match`, potem `/match` | Decyzja użytkownika; czysty podział ekranów. |
| Frakcja komputera | Druga grywalna frakcja | Decyzja agenta — najprostsza, łatwa do zmiany. |

## Scope

**In scope:**
- Katalog JSON (2 frakcje + pula neutralna + talie 30) z walidacją przy starcie
- Logika startu partii z wstrzykiwanym losowaniem
- `GET /api/factions`, `POST /api/matches`, `GET /api/matches/current`
- Ekrany `/new-match` i `/match`, link „Nowa partia” w lobby

**Out of scope:**
- Zagrania, tura komputera, koniec partii i wynik (S-03); efekty kart (S-03/S-04)
- Docelowa treść kart i balans (S-06); trwały zapis partii
- „Kontynuuj partię” w lobby, mulligan, wybór frakcji neutralnej

## Architecture / Approach

`factions.json` → `CardCatalog` (walidacja fail-fast) → `MatchFactory` (tasowanie + rozdanie, `RandomGenerator`) → `MatchStore` (mapa w pamięci, klucz `provider:principal`) → `MatchController` zwraca `MatchView` z perspektywy gracza (bez ręki komputera) → `GameService` w SPA → widoki `NewMatch` i `MatchTable` chronione `authGuard`. Wszystkie endpointy pod `/api/**` (lessons.md).

## Phases at a Glance

| Phase | What it delivers | Key risk |
| --- | --- | --- |
| 1. Katalog kart i model startu partii | `factions.json`, walidowany katalog, fabryka partii + testy | Jackson 3 w Boot 4 (pakiet `tools.jackson`) |
| 2. API partii | Magazyn w pamięci + 3 endpointy + MockMvc | Wyciek ręki komputera w widoku; CSRF na POST |
| 3. Ekran wyboru frakcji | `GameService`, `/new-match`, link z lobby | Stany błędów i blokada przycisku |
| 4. Stół partii | `/match` z wznawianiem i pełnym stanem stołu | Czytelność układu stołu |

**Prerequisites:** S-01 (logowanie) i F-02 (wdrożenie) — zrobione.
**Estimated effort:** ~3–4 sesje w 4 fazach.

## Open Risks & Assumptions

- Frakcja neutralna jest w roadmapie w §Parked (po v1), a zasady tury nie są w PRD — po tej zmianie warto zaktualizować roadmapę/PRD (`/10x-roadmap`).
- Gdy zaczyna komputer, stół pokazuje „tura przeciwnika” i nic się nie dzieje — tura komputera przychodzi w S-03.
- Restart/wdrożenie kasuje trwające partie (akceptowane: v1 bez zapisu postępu).

## Success Criteria (Summary)

- Gracz przechodzi od lobby do stołu i widzi poprawnie rozdaną partię wybraną frakcją przeciw drugiej.
- Stres obu stron, zasób i tura są stale widoczne; ręka komputera nie trafia do przeglądarki.
- Odświeżenie `/match` nie gubi partii.
