# Sensowny przeciwnik komputerowy — Plan Brief

> Full plan: `context/changes/sensible-computer-opponent/plan.md`

## What & Why

S-05 z roadmapy: gracz mierzy się z komputerem, który wybiera zagrania na podstawie oceny sytuacji na stole (nie losowo), a przy każdym ruchu widzi krótki powód. PRD wymaga tego wprost (FR-003, FR-006, guardrail „przeciwnik gra sensownie — nie losowo”). Bez tego taktyka traci sens, a gracz nie rozumie tury komputera.

## Starting Point

`RandomOpponent` losuje ruchy z `MatchEngine.legalMoves`, aż ich zabraknie. Od S-04 potrafi przez to leczyć gracza, ranić albo więzić własne postacie. Silnik jest czystą funkcją `(Match, Side, Move) → Match`, a log to strukturalne zdarzenia formatowane pl/en. Odtwarzanie w SPA pokazuje wycinek logu do `logSize` każdego kroku, ale żaden wpis nie mówi „dlaczego”.

## Desired End State

Komputer zawsze kończy partię, gdy może, nigdy nie robi sobie krzywdy i gra zrównoważenie: atakuje, gdy jest blisko wygranej, a broni się, gdy ma mało HP. Gra przy tym nieco różnorodnie, a nie jak automat. Przed każdym jego ruchem, łącznie z końcem tury, log pokazuje krótki powód, np. „Przeciwnik: usuwa Senior — Twoją najsilniejszą postać”. Podczas odtwarzania ten powód wisi jako podpis nad stołem. W 200 partiach z ziarnem komputer wygrywa z graczem losowym ≥ 80%.

## Key Decisions Made

| Decision | Choice | Why (1 sentence) |
| --- | --- | --- |
| Kryterium „sensowności” | Symulacja ≥ 80% wygranych w 200 partiach z ziarnem + testy „bez głupich ruchów” | Mierzalne w CI i łapie zarówno słabą strategię, jak i oczywiste błędy. |
| Strategia | Ocena 1 ruchu wprzód: `engine.apply` na każdym ruchu i funkcja oceny stołu | Silnik jest czysty, więc każdy efekt wydarzeń jest obsłużony bez reguł per karta. |
| Różnorodność | Losowanie ważone oceną (`exp((s − best)/T)`) | Partie się nie powtarzają (wybór użytkownika zamiast losowania wśród remisów). |
| Gwarancje przy losowaniu | Wygrana zawsze brana; ruchy niepoprawiające pozycji odrzucane; losowanie tylko wśród reszty | Godzi różnorodność z deterministycznymi testami „bez głupich ruchów”. |
| Uczciwość | Tylko informacje jawne; ręka i talie czytane jako liczby kart | Powody odwołują się do weryfikowalnych faktów, a symulacja DRAW nie zdradza kolejności talii. |
| Styl gry | Zrównoważony; agresja przy niskim HP gracza, obrona przy niskim własnym HP | Sensowny w każdej fazie partii i daje zróżnicowane powody. |
| Gdzie powód | Wpis w logu (`MoveExplained`) + podpis podczas odtwarzania | Korzysta z istniejącego cięcia logu po `logSize` i jest widoczny w trakcie ruchu. |
| Które ruchy | Każdy, łącznie z końcem tury | Spełnia „pokazuje powód ruchu” jedną spójną regułą. |
| Ton powodów | Rzeczowe szablony pl/en | Jasne, weryfikowalne i tanie w tłumaczeniu; humor zostaje na kartach. |
| Tempo | `REPLAY_STEP_MS` bez zmian | Bez wpływu na guardrail ≤ 15 minut; powód można doczytać w logu. |
| Test symulacji | ≥ 80% z 200 partii, w zwykłym `./mvnw -B verify` | Każda zmiana kart w S-06 jest sprawdzana automatycznie. |
| Losowy komputer | Usunięty z `src/main`, zostaje jako `RandomPlayer` w testach | Martwy kod znika, a punkt odniesienia dla symulacji zostaje. |

## Scope

**In scope:**
- `PositionEvaluator`, `ComputerOpponent` w miejsce `RandomOpponent` w `MatchService`
- `MatchEvent.MoveExplained` + `MoveReason`, zdania pl/en, `LogEntryView.reason`
- Podpis powodu w odtwarzaniu i wyróżnienie w logu SPA
- Testy: „bez głupich ruchów”, uczciwość, symulacja, formater, widok, SPA

**Out of scope:**
- Przeszukiwanie wielu ruchów naprzód, poziomy trudności, podglądanie ukrytych kart
- Humorystyczne powody, zmiana tempa odtwarzania
- Zmiany silnika, zasad, katalogu kart i balansu (S-06)

## Architecture / Approach

`ComputerOpponent.playTurnSteps` w pętli: dla każdego ruchu z `legalMoves` wykonuje `engine.apply` na kopii stanu i `PositionEvaluator.score` (składniki: HP, stoły, liczby kart). Ruch wygrywający jest brany od razu; ruchy o ocenie ≤ bieżącej odpadają; z reszty losowanie ważone; pusta pula oznacza koniec tury. Powód (`MoveReason`) wynika z rodzaju ruchu i największego składnika różnicy oceny. `MoveExplained` jest wstawiany do logu wyniku przed zdarzeniami ruchu, więc trafia do właściwego kroku odtwarzania. API formatuje go jak każdy wpis i oznacza `reason: true`, a SPA pokazuje ostatni taki wpis z widocznego wycinka jako podpis.

## Phases at a Glance

| Phase | What it delivers | Key risk |
| --- | --- | --- |
| 1. Ocena pozycji i wybór ruchu | Komputer grający sensownie + symulacja ≥ 80% | Strojenie wag i temperatury, żeby próg przechodził stabilnie |
| 2. Powód ruchu w logu | `MoveExplained` przed każdym ruchem, zdania pl/en | Powód nietrafny względem tego, co faktycznie zaważyło |
| 3. Podpis powodu w SPA | Podpis w odtwarzaniu, wyróżnione wpisy | Tekst za długi, by przeczytać go w czasie kroku |

**Prerequisites:** S-03 i S-04 `done` (są); lokalne uruchomienie z danymi OAuth do testów ręcznych.
**Estimated effort:** ~3 sesje po pracy, po jednej na fazę; faza 1 najdłuższa przez strojenie.

## Open Risks & Assumptions

- Próg 80% to założenie. Jeśli talie są bardzo losowe, może wymagać obniżenia po pierwszym pomiarze (z uzasadnieniem w planie, a nie po cichu).
- Ocena 1 ruchu wprzód nie widzi kombinacji typu „wzmocnij, potem atakuj”, chyba że ocena nagradza samo wzmocnienie.
- Losowanie ważone może czasem wybrać słabszy, choć nieszkodliwy ruch. To świadomie przyjęte w zamian za różnorodność.
- Wagi i progi przejdą ponowne strojenie w S-06 przy pełnych taliach.

## Success Criteria (Summary)

- Komputer nigdy nie przepuszcza wygranej i nie robi sobie krzywdy; z graczem losowym wygrywa ≥ 80%.
- Przy każdym ruchu komputera gracz widzi krótki, zrozumiały powód, w logu i nad stołem.
- Partia nadal mieści się w ≤ 15 minutach.
