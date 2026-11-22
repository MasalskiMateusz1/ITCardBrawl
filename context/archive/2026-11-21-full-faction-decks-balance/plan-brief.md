# Pełne talie frakcji i balans (S-06) — Plan Brief

> Full plan: `context/changes/full-faction-decks-balance/plan.md`
> Research: `context/changes/full-faction-decks-balance/research.md`

## What & Why

Obie frakcje mają mieć realną szansę na zwycięstwo, a partia ma mieścić się w ≤ 15 min. Autor chce balansu pojedynczych kart, a nie tylko dwóch gotowych talii, bo później planuje budowanie własnej talii. Dlatego S-06 wprowadza jawną regułę wyceny kart i mierzy balans symulacją komputer kontra komputer — „jedyny sposób na realne sprawdzenie z graczami na tym samym poziomie”.

## Starting Point

Dwie talie po 30 kart z tekstami PL+EN i zgadniętymi wartościami; brak reguły wyceny (są karty zdominowane), brak pomiaru balansu, AI zaszyte na stronę OPPONENT, brak limitu czasu tury, CI bez artefaktów.

## Desired End State

Każda karta przechodzi regułę z `context/foundation/card-pricing.md` (test blokuje build). AI ocenia karty tym samym modelem. `./mvnw -B verify` rozgrywa 1000 partii komputer–komputer i pilnuje, by każda komórka frakcja × strona zaczynająca wygrywała 40–60%, a p90 szacowanego czasu partii ≤ 15 min; ślad CSV + podsumowanie jest w artefakcie CI. Gracz ma 45 s na turę, potem tura kończy się sama.

## Key Decisions Made

| Decision | Choice | Why (1 sentence) | Source |
| --- | --- | --- | --- |
| Treść kart | Id, nazwy i żarty bez zmian; zmieniają się tylko wartości | Autor zostawia obecny szkic tekstów na razie | Research |
| Kryterium balansu | Symulacja komputer–komputer, 2 z 5 = 40% | Obie strony na tym samym poziomie | Research |
| Kształt bramki | Każda z 4 komórek (frakcja × start) w 40–60% | Żadna kombinacja nie może być jednostronna | Plan |
| Liczba partii | 1000 (250/komórkę), cel strojenia 45–55% | ±6 p.p. szumu nie rozstrzyga o bramce | Plan |
| Reguła wyceny | Od zera, dokument + `CardPricing` + test wywalający build | Jasne zasady dla przyszłych kart bez blokowania startu aplikacji | Research + Plan |
| Podstawa wzoru | Przegląd źródeł (MTG, Hearthstone, Lorcana, prace naukowe) jako faza 1 i warunek przed jakąkolwiek implementacją; akceptacja autora | Najpierw dane do reguły, potem kod | Plan (po review) |
| Ślad symulacji | CSV per partia i per karta + `summary.md`, artefakt CI zawsze | Analiza później i trend między zmianami | Research + Plan |
| Wagi AI | Wyprowadzone z reguły wyceny + ręczna korekta | AI nie przecenia kart sprzecznie z regułą; naprawia `FAVORABLE_TRADE` | Research + Plan |
| Przewaga pierwszego | Karta-moneta (`neu-coffee`) dla drugiego gracza — tylko jeśli pomiar jej wymaga | Sprawdzony wzorzec, nie rusza krzywej | Plan |
| Talia aggro | Tylko raport w symulacji, bez bramki | Wczesny sygnał dla kreatora talii | Plan |
| Limit tury | 45 s, auto koniec tury, backend + licznik SPA | Wygodne dla nowych graczy; nie da się obejść zamknięciem karty | Plan |
| Weryfikacja 15 min | p90 szacunku (20 s/turę człowieka + 0,7 s/ruch komputera) + 3 partie na stoperze | Automatyczna bramka i realny pomiar | Plan |

## Scope

**In scope:** strona AI jako parametr; `BalanceSimulationTests` + ślad w CI; przegląd zewnętrznych źródeł wyceny (MTG, Hearthstone, Lorcana, prace naukowe); reguła wyceny (dokument, kod, test); przeliczenie wartości 31 kart; strojenie wag `PositionEvaluator`; bramki balansu i czasu; warunkowa karta-moneta; limit tury 45 s (backend, API, SPA, PL/EN).

**Out of scope:** nowe teksty/id kart; kreator talii; nowe zdolności/efekty; `DRAW_PER_TURN` w silniku; automatyczne przeszukiwanie wag; kary za timeout; przycisk pominięcia odtwarzania; raporty balansu w repozytorium.

## Architecture / Approach

Najpierw przegląd źródeł zewnętrznych jako twarda bramka przed jakimkolwiek kodem, potem narzędzia pomiaru (symulacja w trybie raportu, reguła wyceny oparta na źródłach), potem strojenie w kolejności, która nie unieważnia pomiaru: karty → wagi AI → strojenie do celu i włączenie bramek. Termin tury żyje w `MatchStore`/`MatchService` z wstrzykiwanym `Clock`, poza rekordem `Match`, więc silnik i symulacja zostają wolne od czasu. Termin uwzględnia czas odtwarzania ruchów komputera (kroki × 0,7 s + 3 s zapasu).

## Phases at a Glance

| Phase | What it delivers | Key risk |
| --- | --- | --- |
| 1. Przegląd źródeł (warunek wstępny) | Research w Exa (`exa:search`): ≥ 3 źródła na grę, ≥ 3 prace naukowe, log zapytań → `pricing-sources.md` z mapowaniem i wnioskami; bez kodu | Wzory z innych gier nie przenoszą się 1:1 (30 HP, budżet do 10); źródła społeczności bywają nieoficjalne |
| 2. Symulacja i ślad CI | AI po obu stronach, 1000 partii, CSV + summary w artefakcie | Czas działania nieznany (fallback N ≥ 400) |
| 3. Reguła wyceny | `card-pricing.md` z odnośnikami do źródeł, `CardPricing`, test blokujący build | Wzór może nie pasować do zakresów 1–10 |
| 4. Przeliczenie kart | `factions.json` zgodny z regułą | Wartości mogą kłócić się z żartami |
| 5. Wagi AI | Wagi z reguły, `FAVORABLE_TRADE` działa, ≥ 80% vs losowy | Słabsze AI po zmianie |
| 6. Bramki | 4 komórki 40–60%, p90 ≤ 15 min, ewentualna moneta | Przewaga startu nieosiągalna samymi kartami |
| 7. Limit tury 45 s | Termin w backendzie, auto koniec tury, licznik SPA | Wyścig między licznikiem SPA a terminem serwera |

**Prerequisites:** S-04 i S-05 zakończone (są); zaakceptowany przegląd źródeł z fazy 1 przed startem fazy 2; dostęp do GitHub Actions dla artefaktów; wdrożona wersja do pomiaru stoperem.
**Estimated effort:** ~6–8 sesji w 7 fazach; fazy 4 i 6 są iteracyjne.

## Open Risks & Assumptions

- Założenie 20 s na turę człowieka w szacunku czasu jest zgadnięte; ręczny pomiar w fazie 7 je weryfikuje.
- Balans AI kontra AI to przybliżenie stylu gry tego AI, nie dowód dla graczy-ludzi.
- 45 s × ~26 tur w najgorszym przypadku = 19,5 min — limit sam nie gwarantuje 15 min; gwarantuje to bramka p90 długości partii.
- Strojenie w fazie 6 może wymagać zmiany reguły wyceny (wtedy aktualizacja dokumentu, nie wyjątki).

## Success Criteria (Summary)

- Każda frakcja, zaczynając i nie zaczynając, wygrywa 40–60% partii komputer–komputer, a wynik i ślad są widoczne w każdym buildzie CI.
- Nowa karta niezgodna z regułą wyceny nie przejdzie builda.
- Partia na wdrożonej wersji trwa ≤ 15 min, a bezczynny gracz nie blokuje jej dłużej niż 45 s na turę.
