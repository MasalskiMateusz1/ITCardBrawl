---
date: 2026-11-21T22:17:19+01:00
researcher: Claude (Opus 5.5) dla Mateusz Masalski
git_commit: 624b91591a0da07a6d4c5cecb724a59acd997b3e
branch: main
repository: ITCardBrawl
topic: "full-faction-decks-balance (S-06): stan talii, narzędzia do pomiaru balansu i długości partii"
tags: [research, catalog, factions-json, computer-opponent, balance, simulation]
status: complete
last_updated: 2026-11-21
last_updated_by: Claude (Opus 5.5)
last_updated_note: "Follow-up (7) 2026-11-21: Q6 — wagi AI stroimy w S-06"
---

# Research: full-faction-decks-balance (S-06)

**Date**: 2026-11-21T22:17:19+01:00
**Researcher**: Claude (Opus 5.5) dla Mateusz Masalski
**Git Commit**: 624b91591a0da07a6d4c5cecb724a59acd997b3e (drzewo robocze: niezatwierdzona zmiana tylko w `context/foundation/roadmap.md`)
**Branch**: main
**Repository**: ITCardBrawl

## Research Question

Co jest potrzebne do wycinka S-06 (`context/foundation/roadmap.md:174-186`): 2 pełne talie po 30 kart z humorem IT (PL + EN), żadna frakcja nie wygrywa zawsze, partia ≤ 15 min. Jaki jest obecny stan talii, co blokuje zmianę danych, czym da się zmierzyć balans i długość partii, i co poprzednie zmiany odłożyły do S-06?

## Summary

1. **Talie już mają po 30 kart z tekstami PL+EN.** Mają też identyczną krzywą kosztów. S-06 nie zaczyna więc od pustej talii, tylko od przeglądu i strojenia. W `src/main/resources/game/factions.json:58-101` każda z 2 talii ma 10 kart frakcji × 2 kopie oraz 10 kart neutralnych × 1. Daje to 15 postaci i 15 wydarzeń. Te wartości to jednak zatwierdzony szkic agenta. Teksty EN też są szkicem do poprawy przez autora (zob. Historical Context).
2. **Zmiana treści kart to zmiana danych, nie kodu.** Ograniczają ją walidacja katalogu przy starcie (`CardCatalog.java:16-36`, `:226-288`) i testy, które przypinają konkretne id i wartości kart (`CardCatalogTests.java:84-170`, plus id w `MatchLogFormatterTests`, `MatchViewTests` i `MatchControllerTests`).
3. **Balansu frakcji ani długości partii nie mierzy dziś nic.** Jedyna automatyczna symulacja to `ComputerOpponentTests.beatsRandomPlayerInAtLeastEightyPercentOfSeededMatches` (`ComputerOpponentTests.java:55-84`). Rozgrywa 200 partii komputera przeciw `RandomPlayer` i liczy tylko wygrane komputera. Nie zapisuje zwycięzcy wg frakcji, wg tego, kto zaczynał, ani liczby tur.
4. **Symulacja komputer kontra komputer jest wykonalna przy jednej małej zmianie w kodzie.** Silnik jest czysty, a cała losowość jest wstrzykiwana (`MatchFactory.java:22,42,55`; `ComputerOpponent.java:39`). Jedyną blokadą jest strona AI zaszyta na sztywno: `private static final Side SIDE = Side.OPPONENT;` (`ComputerOpponent.java:31`).
5. **Na długość partii wpływa też tempo UI.** Każdy ruch komputera jest odtwarzany co 700 ms (`frontend/src/app/match-table/match-table.ts:29-31`). Liczba tur z symulacji nie przekłada się więc wprost na minuty. Potrzebne jest założenie czasu na turę albo pomiar ręczny.

## Detailed Findings

### Obecne talie (`factions.json`)

- Katalog ma 31 kart w `factions.json:23-55`: 10 kart `developers`, 10 kart `testers` i 11 neutralnych. `neu-daily-standup` (`:46`) jest w katalogu, ale nie trafia do żadnej talii, co pilnuje `CardCatalogTests.java:106-108`.
- Skład obu talii (`factions.json:58-101`), policzony skryptem node na tym pliku:

| | developers | testers |
|---|---|---|
| Karty / postacie / wydarzenia | 30 / 15 / 15 | 30 / 15 / 15 |
| Średni koszt | 5.00 | 5.00 |
| Suma ataku postaci (z kopiami) | 63 | 61 |
| Suma zdrowia postaci (z kopiami) | 75 | 77 |
| Bezpośrednie obrażenia w gracza (ON_PLAY_DAMAGE + PLAYER_DAMAGE) | 22 | 26 |
| Słowa kluczowe (liczba kopii) | RUSH 2, GUARD 3, HEAL 3, ON_PLAY_DAMAGE 4, OVERTIME 1 | ON_PLAY_DAMAGE 6, GUARD 5, OVERTIME 3, HEAL 1 |

- Krzywa kosztów jest identyczna w obu taliach: koszt 1→3, 2→3, 3→5, 4→3, 5→4, 6→3, 7→3, 8→2, 9→1, 10→3.
- Statyczne liczby nie mówią, która frakcja wygrywa. Testerzy mają więcej bezpośrednich obrażeń i ochrony, Developerzy więcej leczenia i Szarży. Odpowiedź da dopiero pomiar (zob. symulacja).

### Ograniczenia danych (walidacja przy starcie)

- Ograniczenia w `CardCatalog.java:16-36`:
  - koszt 1–`BUDGET_CAP` (10);
  - atak i zdrowie 1–10;
  - `duration` 0–5;
  - kopie 1–2;
  - dokładnie 2 grywalne frakcje.
- Talia musi mieć dokładnie `MatchRules.DECK_SIZE` = 30 kart (`CardCatalog.java:282-284`). Może zawierać wyłącznie karty własnej frakcji lub frakcji niegrywalnej (`:265-268`).
- Każde pole tekstowe musi mieć niepusty tekst w każdym `Language`, czyli PL i EN (`CardCatalog.java:291-298`). Sprawdzane są pola `name`/`description` frakcji (`:103-104`) oraz `name`/`text` kart (`:125-126`).
- Wydarzenie z `duration` 0 nie może mieć modyfikatorów. Wydarzenie z `duration` > 0 musi mieć co najmniej jeden modyfikator (`CardCatalog.java:207-212`).
- Dostępne rodzaje efektów i ich zakresy są w `EffectKind.java:375-391`.
- Dostępne zdolności postaci to dokładnie 5 słów kluczowych: ON_PLAY_DAMAGE, HEAL, GUARD, RUSH, OVERTIME (`AbilityKeyword.java:317-330`).
- Nowa mechanika spoza tych enumów oznacza zmianę kodu, a nie samych danych.
- Opisy zdolności i efektów generuje kod (`AbilityKeyword.describe`, `EffectKind.description`). Autor pisze tylko `name` i `text` (żart).

### Testy przypięte do treści kart (kontrakt przy zmianie talii)

- `CardCatalogTests.realCatalogLoads` (`CardCatalogTests.java:84-109`) wymaga:
  - id frakcji `developers` i `testers` w tej kolejności;
  - nazw PL „Developerzy” i „Testerzy”;
  - dokładnie 15 wydarzeń w każdej talii (`:100-101`);
  - konkretnych efektów `neu-requirements-change`;
  - tego, że `neu-daily-standup` jest poza taliami.
- `everyEventHasEffects` (`:113-127`) wylicza 16 konkretnych id wydarzeń.
- `realCatalogHasCardStats` (`:153-170`) przypina statystyki `dev-backend` (4/6, GUARD), zdolność `dev-senior` (HEAL 3), brak zdolności `dev-fullstack` i `duration` 3 `tst-flaky-test`.
- Id kart są też użyte w `MatchLogFormatterTests.java:30,32,184,201`, `MatchViewTests.java:145-238` i `MatchControllerTests.java:551-591`. Przemianowanie lub usunięcie tych kart wymaga zmian w testach.

### Zasady wpływające na długość partii

- Stałe w `MatchRules.java`:
  - `DECK_SIZE` 30 (`:13`);
  - `STARTING_HAND` 5 (`:15`), więc w talii zostaje 25 kart;
  - `MAX_HP` 30 (`:18`);
  - `BUDGET_CAP` 10 (`:20`);
  - `HAND_LIMIT` 10 (`:25`);
  - `BOARD_LIMIT` 5 (`:28`).
- `DRAW_PER_TURN` = 1 (`MatchRules.java:22`) nie jest nigdzie używane. Grep w `src/main` znalazł tylko javadoc i deklarację. Silnik zawsze dobiera jedną kartę na turę (`MatchEngine.java:441`).
- Budżet rośnie o 1 na własną turę i jest uzupełniany do pełna (`MatchEngine.java:447-448`). Strona zaczynająca startuje z 1/1, druga z 0/0 (`MatchFactory.java:66`).
- Górna granica, wyliczona przez pracownika i nieuruchamiana: strona niezaczynająca próbuje 26. dobrania w okolicy tury 52. Przy pustej talii przegrywa (`MatchEngine.java:454-456`). Karty DRAW skracają tę granicę.
- Tempo UI to 700 ms na każdy odtwarzany ruch komputera (`match-table.ts:29-31`). Nie ma przycisku pominięcia (zob. Historical Context).

### Kto zaczyna i dobór przeciwnika

- O stronie zaczynającej decyduje `random.nextBoolean()` (`MatchFactory.java:42`). Jedyną rekompensatą dla drugiej strony jest to, że zaczynający nie dobiera karty w turze 1 (`MatchFactory.java:44-49`). Drugi gracz nie dostaje dodatkowej karty ani zasobu.
- Frakcja komputera to pierwsza grywalna frakcja inna niż frakcja gracza (`MatchFactory.java:34-37`). Przy 2 frakcjach jest to zawsze developers kontra testers. Lustrzanej partii fabryka nie tworzy.

### Wykonalność symulacji komputer kontra komputer

- Silnika można używać bez Springa:
  - `MatchEngine` i `PositionEvaluator` mają konstruktory bez argumentów;
  - `MatchFactory(CardCatalog, RandomGenerator)` i `ComputerOpponent(MatchEngine, PositionEvaluator, RandomGenerator)` przyjmują generator losowy;
  - testy już podają `SplittableRandom(seed)` (`ComputerOpponentTests.java:51-52`);
  - katalog ładuje się bez kontekstu Springa (`CardCatalogTests.java:86-87`).
- Blokada: `ComputerOpponent` gra wyłącznie stroną OPPONENT (`ComputerOpponent.java:31`). Pracownik wskazał użycia tej stałej w `:62`, `:85-101`, `:134`, `:176-227` i `:256-276`.
- `PositionEvaluator.score(match, perspective)` przyjmuje już perspektywę, więc jest niezależny od strony (wg raportu pracownika).
- Minimalna zmiana: strona jako parametr konstruktora i dwie instancje z różnymi ziarnami. Ścieżka produkcyjna w `MatchService` się nie zmienia.
- Kontrola strony zaczynającej: test opakowuje generator, żeby ustalić wynik `nextBoolean` (`ComputerOpponentTests.java:356-369`, wg pracownika). Wzór pętli partii jest w `ComputerOpponentTests.java:66-75`.
- Zabezpieczenia przed zapętleniem:
  - `MAX_MOVES_PER_TURN` = 100 (`ComputerOpponent.java:23`);
  - pusta talia kończy partię;
  - test ma limit `MAX_TURNS` = 200 (`ComputerOpponentTests.java:39`).
- Koszt wydajności, nieuruchamiany: pracownik wskazał, że `State` i `withEventAt` kopiują cały log przy każdym symulowanym ruchu-kandydacie (`Match.java:40-44`). Czas jednej partii jest nieznany, bo niczego nie uruchomiono.
- Siła AI: `TEMPERATURE` = 1.0 to stała statyczna (`ComputerOpponent.java:26`). Losowanie softmax działa w `:115`.

## Code References

- `src/main/resources/game/factions.json:23-55` – katalog 31 kart (PL+EN)
- `src/main/resources/game/factions.json:57-101` – obie talie (10×2 + 10×1)
- `src/main/java/com/example/itcardbrawl/game/CardCatalog.java:16-36` – limity walidacji
- `src/main/java/com/example/itcardbrawl/game/CardCatalog.java:240-288` – rozwijanie talii i warunek 30 kart
- `src/main/java/com/example/itcardbrawl/game/MatchRules.java:13-28` – stałe zasad
- `src/main/java/com/example/itcardbrawl/game/AbilityKeyword.java:317-330` – 5 zdolności
- `src/main/java/com/example/itcardbrawl/game/EffectKind.java:375-391` – rodzaje efektów i zakresy
- `src/main/java/com/example/itcardbrawl/game/MatchFactory.java:34-49` – frakcja komputera, losowanie startu
- `src/main/java/com/example/itcardbrawl/game/ComputerOpponent.java:23,26,31` – limit ruchów, temperatura, sztywna strona
- `src/test/java/com/example/itcardbrawl/game/ComputerOpponentTests.java:55-84` – jedyna symulacja pełnych partii
- `src/test/java/com/example/itcardbrawl/game/CardCatalogTests.java:84-170` – testy przypięte do treści kart
- `frontend/src/app/match-table/match-table.ts:29-31` – `REPLAY_STEP_MS` = 700

## Architecture Insights

- Treść kart żyje w jednym pliku danych, zgodnie z założeniem S-02 „S-06 podmienia dane bez zmian kodu”. Mechanika jest zamknięta w enumach `AbilityKeyword` i `EffectKind`.
- Strojenie balansu ma 3 dźwignie:
  - dane kart (koszt, statystyki, efekty, kopie, dobór neutralnych);
  - stałe zasad w `MatchRules`, które zmieniają tempo obu stron naraz;
  - wagi AI w `PositionEvaluator` i `ComputerOpponent`, które zmieniają siłę przeciwnika, a nie samych frakcji.
- Balans frakcji mierzony grą AI kontra AI mówi, jak frakcje radzą sobie przy stylu gry tego AI. Gracz człowiek może grać inaczej, więc to przybliżenie, nie dowód.

## Historical Context (from prior changes)

Wszystkie poniższe twierdzenia są poparte. Sprawdzałem źródła pracownika punktowo w miejscach oznaczonych ✓.

- `context/archive/2026-11-20-first-full-match/plan-brief.md:80` ✓: „Balans szkicowych wartości jest zgadywany. Partia może trwać dłużej niż 15 min albo być jednostronna; korekta w S-06.”
- `context/archive/2026-11-20-event-cards/plan.md:38,111`: wartości efektów wydarzeń to szkic, a balans przechodzi do S-06.
- `context/archive/2026-11-19-match-setup-table/plan-brief.md:26,43`: talie są zdefiniowane raz w `factions.json`, a S-06 ma podmienić dane bez zmian kodu.
- `context/archive/2026-11-19-ui-in-english-and-polish/plan.md:44`: obecne teksty EN to szkic agenta. W S-06 mają być wymienione razem z PL, a humor może wymagać korekty autora.
- `context/archive/2026-11-21-sensible-computer-opponent/plan-brief.md:31` ✓: test symulacji (≥ 80% z 200 partii, w `./mvnw -B verify`) sprawdza automatycznie każdą zmianę kart w S-06.
- `plan-brief.md:64` tej samej zmiany ✓: próg 80% może zostać obniżony po pierwszym pomiarze, ale tylko z uzasadnieniem w planie.
- `plan-brief.md:67` tej samej zmiany ✓: wagi i progi AI przejdą ponowne strojenie w S-06.
- `context/archive/2026-11-21-sensible-computer-opponent/reviews/impl-review.md:65` ✓: przy obecnych wagach nieśmiertelny atak na postać nigdy nie wygrywa z bezpośrednim atakiem na gracza, więc `FAVORABLE_TRADE` praktycznie nie występuje. Zalecenie: wrócić do wag w S-06.
- `context/archive/2026-11-20-first-full-match/plan-brief.md:84`: odtwarzanie ~700 ms na ruch i brak przycisku pominięcia mogą wydłużać partię. W S-05 `REPLAY_STEP_MS` pozostało bez zmian (`sensible-computer-opponent/plan-brief.md:30`).
- Ryzyko S-05 z `roadmap.md:171`, że strategia nie zna wydarzeń, jest nieaktualne. S-05 powstało po S-04 i obsługuje wydarzenia (`sensible-computer-opponent/plan.md:11,113-115`, wg pracownika).
- `context/archive/2026-11-21-stress-to-hp/reviews/impl-review.md:77-84` (F6, SKIPPED): zdolność HEAL loguje „+N” także przy pełnym HP. To kosmetyka, ale widoczna, jeśli S-06 doda karty z leczeniem.
- Nieaktualna terminologia w roadmapie: „stres” w `roadmap.md:32,47,124,137,195,215` i „zasób” w `:207`. Blok S-06 (`:176-185`) jest aktualny. PRD jest aktualny, używa HP i budżetu (`prd.md:113-121`).
- `event-cards/plan.md:115-132` używa starych nazw enumów (STRESS, RESOURCE). Nie należy go kopiować dosłownie.

## Related Research

- `context/archive/2026-11-20-first-full-match/research.md` – skład talii 15/15 i zasady postaci
- `context/archive/2026-11-21-stress-to-hp/research.md`, `context/archive/2026-11-21-resource-to-budget/research.md` – zmiany nazw HP i budżetu

## Open Questions

Decyzje produktowe należą do autora i żadna nie jest rozstrzygnięta w repozytorium.

1. ~~**Treść kart:** czy autor przepisuje nazwy i żarty (PL+EN) wszystkich 31 kart, czy tylko poprawia obecny szkic? Czy zmieniają się id kart? Zmiana id pociąga za sobą zmiany w testach z sekcji „Testy przypięte”.~~ **Rozstrzygnięte 2026-11-21 (autor):** na razie zostaje obecny szkic — patrz „Follow-up 2026-11-21”.
2. ~~**Definicja „realnej szansy”:** PRD nie podaje progu (`prd.md:55`). Potrzebna mierzalna granica, np. win rate każdej frakcji w AI kontra AI w przedziale X–Y% przy N partiach, rozbita na stronę zaczynającą.~~ **Rozstrzygnięte 2026-11-21 (autor):** 2 wygrane na 5 partii — patrz „Follow-up 2026-11-21 (2)”.
3. **Przewaga pierwszego gracza:** czy mierzyć ją osobno i czy kompensować, np. dodatkową kartą lub budżetem dla drugiego gracza? To zmiana zasad, nie danych.
4. **Pomiar ≤ 15 min:** jak przeliczać tury na minuty? Potrzebne założenie sekund na turę gracza plus 700 ms × liczba ruchów komputera, albo pomiar ręczny na wdrożonej wersji. Do decyzji też, czy dodać przycisk pominięcia lub skrócić `REPLAY_STEP_MS`.
5. **Forma symulacji:** test JUnit w `./mvnw -B verify`, test otagowany i wyłączony z domyślnego builda, czy osobna klasa main z raportem? Od tego zależą czas builda i to, czy balans jest bramką CI.
6. **Strojenie AI:** czy S-06 obejmuje wagi `PositionEvaluator` (zalecenie z impl-review S-05)? Zmiana wag zmienia wynik pomiaru balansu, więc kolejność kroków ma znaczenie.
7. **Nieużywane `DRAW_PER_TURN`:** podpiąć je w silniku czy usunąć? To drobne, ale ważne, jeśli tempo będzie strojone tą stałą.

Luki w dowodach:
- Niczego nie uruchomiono. Rzeczywisty rozkład tur, win rate frakcji i czas jednej symulowanej partii pozostają nieznane do czasu pierwszego pomiaru.
- Pracownik archiwalny opierał się częściowo na grepie słów kluczowych. Decyzje sformułowane innymi słowami mogły zostać pominięte.

## Follow-up 2026-11-21: decyzja o treści kart

**Decyzja autora:** „Zostawiamy obecny szkic na razie”. Nazwy, żarty PL+EN i id 31 kart z `factions.json:23-55` pozostają na tym etapie bez przepisywania.

Konsekwencje (wnioski z ustaleń powyżej, bez nowego przeszukiwania):
- Przy niezmienionych id kart testy z sekcji „Testy przypięte do treści kart” nie wymagają zmian z powodu treści.
- Nadal otwarte: czy strojenie balansu może zmieniać **wartości** kart (koszt, atak/zdrowie, efekty, kopie). Jeśli tak, `CardCatalogTests.realCatalogHasCardStats` (`CardCatalogTests.java:153-170`) przypina statystyki `dev-backend`, `dev-senior` i `tst-flaky-test`, a `realCatalogLoads` wymaga 15 wydarzeń na talię (`:100-101`).
- Unknown z `roadmap.md:183` (treść kart, Owner: user, Block: no) zostaje odroczone, a nie zamknięte. Ryzyko „treść kart to praca autorska” (`roadmap.md:185`) pozostaje na później.
- Bez zmian zostają: teksty EN to szkic agenta (`ui-in-english-and-polish/plan.md:44`), więc Secondary success criterion „znajomi rozpoznają żarty” (`prd.md:49`) nie jest weryfikowany w tym kroku.

Pozostałe otwarte pytania: 2–7.

## Follow-up 2026-11-21 (2): próg szansy i balans kart zamiast talii

**Decyzje autora (dosłownie):** „gracz ma realną szansę na zwycięstwo 2 na 5 partii. Jednak bardziej mi chodzi o zbalansowanie kart niż talii, później chciałbym dodać opcję budowania własnej talii.”

- **Próg:** 2/5 = 40% wygranych. Interpretacja (gracz–komputer czy frakcja–frakcja w AI kontra AI) — patrz Open Question 2a niżej.
- **Zmiana celu S-06:** priorytetem jest wycena pojedynczych kart (koszt vs siła), a nie strojenie dwóch gotowych talii. Uzasadnienie autora: przyszłe budowanie własnej talii — przy dowolnych taliach balans talii nie wystarcza, bo każda karta musi być warta swojego kosztu.
- **Zgodność z PRD:** budowanie talii jest Non-Goal v1 (`prd.md:134`) i parkiem roadmapy (`roadmap.md:212`). Decyzja go nie wprowadza do S-06; zmienia tylko kryterium balansu. Guardrail „żadna frakcja nie wygrywa zawsze” (`prd.md:55`) nadal obowiązuje dla obecnych talii.

### Wycena kart — stan obecny (policzone skryptem node na `factions.json`)

Postacie (koszt, atak/zdrowie, suma statystyk / koszt, zdolność):

| Koszt | Karta | A/Z | Suma/koszt | Zdolność |
|---|---|---|---|---|
| 1 | dev-junior | 1/1 | 2.00 | RUSH |
| 1 | tst-intern | 1/2 | 3.00 | ON_PLAY_DAMAGE 1 |
| 2 | neu-rubber-duck | 1/3 | 2.00 | HEAL 2 |
| 3 | dev-fullstack | 3/3 | 2.00 | — |
| 3 | tst-manual | 2/4 | 2.00 | GUARD |
| 3 | neu-scrum-master | 2/4 | 2.00 | GUARD |
| 5 | dev-backend | 4/6 | 2.00 | GUARD |
| 5 | tst-automation | 4/5 | 1.80 | OVERTIME |
| 5 | neu-devops | 4/4 | 1.60 | OVERTIME |
| 6 | neu-project-manager | 4/5 | 1.50 | ON_PLAY_DAMAGE 3 |
| 7 | dev-senior | 6/7 | 1.86 | HEAL 3 |
| 7 | tst-performance | 7/5 | 1.71 | ON_PLAY_DAMAGE 3 |
| 10 | dev-tech-lead | 8/8 | 1.60 | ON_PLAY_DAMAGE 4 |
| 10 | tst-qa-lead | 7/10 | 1.70 | GUARD |
| 10 | neu-client | 8/9 | 1.70 | ON_PLAY_DAMAGE 5 |

Obserwacje (bezpośrednio z tabeli):
- W repozytorium nie ma zapisanej formuły kosztu. Suma statystyk na punkt kosztu spada z 2.00 (koszt 1–5) do 1.50–1.86 (koszt 6–10); zdolności nie mają ustalonej ceny.
- **Karta zdominowana:** `neu-devops` (5, 4/4, OVERTIME) jest pod każdym względem słabsza od `tst-automation` (5, 4/5, OVERTIME). Przy budowaniu talii nikt jej nie wybierze.
- **Ta sama zdolność, różna cena:** ON_PLAY_DAMAGE 3 kosztuje 6 przy 4/5 (`neu-project-manager`) i 7 przy 7/5 (`tst-performance`) — +1 kosztu za +3 ataku.
- **Koszt 1:** `tst-intern` daje 3 statystyki + 1 obrażenie, `dev-junior` 2 statystyki + Szarżę.
- `tst-manual` i `neu-scrum-master` są identyczne mechanicznie (3, 2/4, GUARD).

Wydarzenia — przykłady rozjazdu wyceny tego samego efektu:
- Obrażenia w wybrany cel: 3 za koszt 3 (`tst-edge-case`) vs 5 za koszt 6 (`dev-force-push`).
- Obrażenia w gracza: 2 za koszt 2 (`tst-vague-bug-report`, 1.0/pkt) vs 6 za koszt 9 (`neu-deadline`, 0.67/pkt).
- Pełna lista 16 wydarzeń z efektami: `factions.json:24-54`.

### Co z tego wynika dla planu (wnioski, nie decyzje)

- Balans kart wymaga **jawnej reguły wyceny** (np. budżet statystyk na punkt kosztu + cena każdej zdolności/efektu), którą da się sprawdzić automatycznie — np. test lub walidacja zgłaszająca karty poza tolerancją i karty zdominowane. Dziś nic takiego nie istnieje (`CardCatalog.java:16-36` sprawdza tylko zakresy).
- Symulacja AI kontra AI (sekcja wyżej) mierzy balans talii; do balansu kart można dodać metryki per karta (np. odsetek wygranych partii, w których karta została zagrana). To przybliżenie stylu gry AI, nie człowieka.
- Strojenie wartości kart zmieni przypięte statystyki w `CardCatalogTests.java:153-170` (`dev-backend`, `dev-senior`, `tst-flaky-test`) — patrz Follow-up (1).

### Nowe / zaktualizowane pytania otwarte

- ~~**2a.** Do czego odnosi się próg 2 z 5: człowiek kontra komputer (np. ręczne 5 partii na wdrożonej wersji), czy każda frakcja ≥ 40% w symulacji AI kontra AI? To zmienia, co jest bramką.~~ **Rozstrzygnięte 2026-11-21 (autor):** symulacja komputer kontra komputer — patrz „Follow-up 2026-11-21 (3)”.
- ~~**8.** Czy S-06 ma ustalić regułę wyceny kart (formułę kosztu) jako artefakt — np. w `docs/` lub jako test — żeby przyszłe budowanie talii i nowe karty miały wspólną miarę?~~ **Rozstrzygnięte 2026-11-21 (autor):** tak — patrz „Follow-up 2026-11-21 (4)”.

Pozostałe otwarte: 3–7 — zob. Follow-up (5).

## Follow-up 2026-11-21 (3): próg mierzony symulacją komputer kontra komputer

**Decyzja autora (dosłownie):** „raczej chodzi mi o symulację komputer–komputer, to jedyny sposób na realne sprawdzenie z graczami na tym samym poziomie”.

Wynikające ustalenia (z sekcji „Wykonalność symulacji” powyżej, bez nowego przeszukiwania):
- Kryterium balansu: każda frakcja wygrywa ≥ 40% (2 z 5) partii w symulacji, w której obie strony gra ten sam `ComputerOpponent`.
- Warunek wstępny w kodzie: strona AI zaszyta na sztywno (`ComputerOpponent.java:31`) — musi stać się parametrem, żeby obie strony grał ten sam algorytm. `MatchService` (zawsze `Side.PLAYER` dla człowieka) się nie zmienia.
- „Ten sam poziom” wymaga tych samych ustawień dla obu stron: ta sama `TEMPERATURE` (`ComputerOpponent.java:26`) i te same wagi `PositionEvaluator`; różne tylko ziarna losowania.
- Strona zaczynająca jest losowana (`MatchFactory.java:42`), więc pomiar powinien rozdzielać partie po równo między zaczynających — inaczej przewaga pierwszego ruchu (pytanie 3) miesza się z balansem frakcji.
- Istniejący test komputer kontra `RandomPlayer` (`ComputerOpponentTests.java:55-84`, próg 80%) mierzy siłę AI, nie balans — zostaje osobną bramką.

Do rozstrzygnięcia w planie (nie decyzje autora):
- Liczba partii na pomiar. Przy 200 partiach przedział ufności 95% dla win rate ok. 50% wynosi ok. ±7 p.p. (przybliżenie normalne, `1.96·√(0.25/200)`), więc wynik 42% nie odróżnia się statystycznie od 40%; ciasna bramka wymaga więcej partii albo marginesu.
- Granica górna: przy 2 frakcjach „każda ≥ 40%” oznacza, że każda wygrywa 40–60%. Remisów nie ma: `MatchResult(Side winner, EndReason reason)` ma zawsze zwycięzcę, a `EndReason` ma dokładnie 2 wartości — `HP_DEPLETED`, `EMPTY_DECK` (`MatchResult.java:6-13`).
- Pytania 5 (bramka CI czy raport) i 6 (kolejność: strojenie wag AI przed pomiarem) nadal otwarte.

## Follow-up 2026-11-21 (4): reguła wyceny kart jest wymagana

**Decyzja autora (dosłownie):** „Tak, wydaje mi się, że taka reguła jest wymagana, żeby przy późniejszym dodawaniu karty można było trzymać się jasnych zasad.”

Zakres wynikający z decyzji (wnioski z ustaleń powyżej):
- S-06 dostarcza jawną regułę wyceny: ile statystyk (atak + zdrowie) przypada na punkt kosztu i ile kosztuje każda z 5 zdolności (`AbilityKeyword.java:317-330`) i każdy z 11 rodzajów efektów (`EffectKind.java:375-391`), z uwzględnieniem zasięgu (`EffectScope.java`) i czasu trwania wydarzeń.
- Obecne karty, które z regułą się rozjadą (z tabeli w Follow-up (2)): co najmniej `neu-devops` (zdominowana przez `tst-automation`) oraz pary z niespójną ceną tej samej zdolności/efektu (`neu-project-manager` / `tst-performance`, `tst-vague-bug-report` / `neu-deadline`, `tst-edge-case` / `dev-force-push`). Pełna lista wyjdzie dopiero po ustaleniu wzoru.
- Reguła i symulacja się uzupełniają: reguła daje wycenę z góry (sprawdzalną bez uruchamiania partii), symulacja komputer–komputer (Follow-up (3)) weryfikuje, czy wycena przekłada się na 40–60% wygranych.

Do rozstrzygnięcia w planie (nie decyzje autora):
- **Forma reguły:** dokument (katalog `docs/` nie istnieje w repozytorium — `ls docs/reference` zwraca błąd; miejsce do ustalenia, np. `context/foundation/` lub nowy `docs/`), kod (funkcja wyceny w pakiecie `game`), czy oba — dokument opisuje zasady, a test w `src/test` sprawdza każdą kartę z `factions.json` względem wzoru z tolerancją.
- **Siła bramki:** czy karta poza tolerancją blokuje build (test / walidacja `CardCatalog`), czy tylko trafia do raportu. Walidacja w `CardCatalog` zatrzymałaby start aplikacji (`CardCatalog.java:55-57`), test tylko build.
- **Wzór kalibrowany na czym:** wartości obecnych kart (np. 2.0 statystyki/koszt dla kosztu 1–5, `factions.json`) czy wartości od zera; nieliniowość dla wysokich kosztów (dziś 1.5–1.86 przy koszcie 6–10).
- Kontekst zgodności: konsumenci treści kart (testy przypięte, Follow-up (1)) — zmiana wartości wymaga aktualizacji `CardCatalogTests.java:153-170`.

## Follow-up 2026-11-21 (5): pytania 3–7

### Pytanie 3 — przewaga pierwszego gracza (wyjaśnienie, bez decyzji)

Autor zapytał, co to znaczy, i czy brak dobrania w turze 1 nie wystarcza jako handicap. Przewaga zaczynającego (z kodu, `MatchFactory.java:44-49,66`; `MatchEngine.java:447-448` wg raportu pracownika):
- Zaczynający ma budżet 1/1 w turze 1, druga strona 0/0. Do każdego poziomu budżetu dochodzi więc o turę wcześniej, czyli pierwszy wystawia postacie i pierwszy atakuje.
- Rekompensata: zaczynający nie dobiera karty w turze 1. Druga strona ma więc o 1 kartę więcej, licząc od swojej pierwszej tury.
- Skutek uboczny tej rekompensaty: talia zaczynającego kończy się później. Przy 25 kartach po rozdaniu (`MatchRules.java:13,15`) i dobieraniu co drugą turę zaczynający próbuje 26. dobrania w turze 53 (pierwsze dobranie w turze 3 + 2·25), a druga strona w turze 52 (pierwsze dobranie w turze 2 + 2·25). W partii zakończonej pustą talią przegrywa więc zawsze strona, która nie zaczynała. To arytmetyka na regułach, niczego nie uruchamiano.
- Czy 1 karta to wystarczający handicap, nie da się ustalić bez pomiaru. Wniosek: symulacja komputer–komputer powinna raportować win rate osobno dla zaczynającego i niezaczynającego. Decyzja o ewentualnej dodatkowej kompensacie zapada dopiero po wyniku.

### Pytanie 4 — limit czasu tury (decyzja autora)

**Decyzja (dosłownie):** „trzeba ustalić jakiś max czas, jaki gracz może wykonywać swoją turę”.
- Dziś limitu nie ma. Grep `Clock|Instant|Duration|timeout|deadline` w pakiecie `game` i w `frontend/src/app/match-table` zwraca tylko `match-table.spec.ts`. Limit to nowa mechanika, nie zmiana danych.
- Budżet czasu, policzony z górnej granicy długości partii (≈ 52 tury, czyli ≈ 26 tur gracza):
  - 15 min = 900 s, czyli ≈ 34 s na turę gracza, jeśli pominąć odtwarzanie ruchów komputera;
  - odtwarzanie to 26 tur komputera × liczba ruchów × 0,7 s (`match-table.ts:29-31`);
  - przy założonych 3 ruchach na turę (niezmierzone) daje to ≈ 55 s, więc na turę gracza zostaje ≈ 32 s.
  Symulacja da rzeczywisty rozkład tur i ruchów zamiast górnej granicy.
- Do rozstrzygnięcia w planie: wartość limitu; co dzieje się po upływie czasu (np. automatyczne zakończenie tury); czy limit pilnuje tylko SPA, czy też backend (`MatchService`). Sam licznik w przeglądarce można obejść, ale przy grze przeciw komputerowi to raczej kwestia tempa niż oszustwa.

### Pytanie 5 — co to bramka CI (wyjaśnienie, bez decyzji)

`.github/workflows/ci.yml` uruchamia `./mvnw -B verify` (`:31`). Etap `image` (`:57`) wymaga powodzenia testów, a `deploy` (`:86-87`) wymaga obrazu. „Bramka CI” znaczy tu: test symulacji jest częścią zwykłego `verify`, więc wynik poza 40–60% wywala build i blokuje wdrożenie. Istniejący test komputer kontra losowy gracz już tak działa (`sensible-computer-opponent/plan-brief.md:31`).
- Alternatywa to test otagowany i wyłączony z domyślnego `verify` albo osobny program z raportem. Uruchamia się go ręcznie przy zmianie kart i nie blokuje wdrożenia.
- Kompromisy:
  - Bramka nie przepuści niezbalansowanej zmiany kart, ale wydłuża build (czas partii jest nieznany, zob. wyżej). Przy wyniku blisko progu może też losowo migać, choć przy stałych ziarnach wynik jest powtarzalny.
  - Raport jest szybszy, ale wymaga pamiętania o jego uruchomieniu.
- Decyzja autora nadal otwarta.

### Kalibracja reguły wyceny — od zera (decyzja autora)

**Decyzja (dosłownie):** „możemy ustalić od 0, wzorując się na innych karciankach i wzorach matematycznych”. Odpowiedź padła pod numerem 6, ale dotyczy otwartego punktu „Wzór kalibrowany na czym” z Follow-up (4) i go rozstrzyga. Obecne wartości kart nie są punktem odniesienia. Wzór powstaje z ogólnych zasad projektowania gier karcianych (np. budżet statystyk na punkt kosztu, cena słów kluczowych), a karty dostosowuje się do niego.
- Ograniczenie z pierwotnych ustaleń: wzór musi mieścić się w zakresach walidacji (`CardCatalog.java:16-36`: atak i zdrowie 1–10, koszt 1–10) albo plan musi te zakresy świadomie zmienić.

### Pytanie 6 — strojenie wag komputera (nadal otwarte)

Pierwotne pytanie 6 dotyczyło wag `PositionEvaluator`/`ComputerOpponent` (impl-review S-05: `FAVORABLE_TRADE` praktycznie nie występuje, `sensible-computer-opponent/reviews/impl-review.md:65`), a nie wzoru kart. Pytanie pozostaje otwarte: czy S-06 stroi wagi, i czy przed pomiarem balansu (wynik symulacji zależy od wag).

### Pytanie 7 — `DRAW_PER_TURN` (decyzja autora)

**Decyzja (dosłownie):** „możemy na razie ją zostawić, bo może kiedyś będziemy chcieli zwiększać to lub jakieś specjalne karty będą modyfikowały ilość pobieranych kart w turze”. Stała zostaje (`MatchRules.java:22`).
- Fakt do uwzględnienia: dziś silnik jej nie czyta, tylko zawsze dobiera 1 kartę (`MatchEngine.java:441`). Zmiana wartości stałej nie zmieni więc gry, dopóki silnik jej nie użyje. Odnotowane, poza zakresem S-06.

Pozostałe otwarte: 5 (bramka czy raport), 6 (wagi AI), szczegóły limitu czasu tury (4), forma i siła bramki reguły wyceny (Follow-up (4)), ewentualna kompensata dla drugiego gracza (3, po pomiarze).

## Follow-up 2026-11-21 (6): pytania 3 i 5 rozstrzygnięte

### Pytanie 3 — przewaga pierwszego gracza (decyzja autora)

**Decyzja (dosłownie):** „masz rację, to istotna różnica, zwłaszcza dla ewentualnych decków aggro”.
- Przyjęte: przewaga zaczynającego jest istotna, a symulacja raportuje win rate osobno dla strony zaczynającej i niezaczynającej (wg Follow-up (5)). O kompensacie decyduje się po pierwszym pomiarze.
- Wniosek z ustaleń powyżej: przewaga tempa (budżet 1/1 kontra 0/0, `MatchFactory.java:66`) jest największa dla tanich i agresywnych kart. Jeśli dojdzie budowanie talii (Follow-up (2)), talia z samych kart za 1–3 może wzmacniać tę przewagę bardziej niż obecne talie o średnim koszcie 5.00. Dziś tego nie da się zmierzyć, bo obie talie mają identyczną krzywą kosztów (Detailed Findings). Pomiar dla talii aggro wymagałby testowej talii poza `factions.json`. `CardCatalog` przyjmuje dane w pamięci (`CardCatalog.java:44`, użyte w `CardCatalogTests.java:78-80`), więc taki wariant jest technicznie możliwy bez zmiany pliku katalogu. Zakres do decyzji w planie.

### Pytanie 5 — symulacja w CI (decyzja autora)

**Decyzja (dosłownie):** „symulacje w CI mogą być przydatne pod warunkiem, że zostawią po sobie jakiś sensowny ślad do przeanalizowania na później”.
- Przyjęte: symulacja balansu może być bramką w `./mvnw -B verify` (`ci.yml:31`), ale musi zostawiać analizowalny wynik, a nie samo „pass/fail”.
- Stan obecny: `ci.yml` nie publikuje dziś żadnych artefaktów. Grep `upload-artifact|artifact|reports` w `.github/workflows/ci.yml` nie zwrócił trafień. `target/` jest w `.gitignore:1`, więc pliki zapisane przez test w `target/` znikną razem z runnerem, jeśli workflow ich nie wyśle.
- Istniejący test komputer kontra losowy gracz w razie porażki podaje tylko odsetek wygranych w komunikacie asercji (`ComputerOpponentTests.java:81-83`).
- Do rozstrzygnięcia w planie: co zawiera ślad. Propozycja z ustaleń powyżej to per partia: ziarno, frakcje, kto zaczynał, zwycięzca, `EndReason`, liczba tur, końcowe HP, a do tego agregaty per frakcja × strona zaczynająca oraz metryki per karta z Follow-up (2). Do ustalenia też format (np. CSV/JSON + krótkie podsumowanie), gdzie trafia (artefakt workflow, podsumowanie joba, plik w repo) i czy ślad zostaje także przy zielonym buildzie, żeby dało się śledzić trend balansu między zmianami kart.

Pozostałe otwarte: 6 (wagi AI), szczegóły limitu czasu tury (4), forma i siła bramki reguły wyceny (Follow-up (4)), zawartość i miejsce śladu symulacji (5), zakres pomiaru talii aggro (3).

## Follow-up 2026-11-21 (7): pytanie 6 rozstrzygnięte

**Decyzja autora (dosłownie):** „możemy dostroić wagi, to ma sens”. Strojenie wag komputera wchodzi w zakres S-06.

Dźwignie strojenia: 20 stałych `static final` w `PositionEvaluator.java:23-80` (`grep -c`) oraz `TEMPERATURE` w `ComputerOpponent.java:26`. Między innymi:
- `ENEMY_HP_WEIGHT` 1.0 i `OWN_HP_WEIGHT` 0.7;
- `ATTACK_WEIGHT` 1.0 i `HEALTH_WEIGHT` 0.7;
- `GUARD_VALUE` 1.0 i `OVERTIME_VALUE` 1.5;
- `HAND_CARD_VALUE` 1.0;
- `BUDGET_MAX_WEIGHT` 1.0.

Wnioski z ustaleń powyżej (do uwzględnienia w planie, nie decyzje):
- **Kolejność kroków.** Wynik symulacji komputer–komputer zależy od wag, bo obie strony grają tym samym algorytmem. Sensowna kolejność:
  1. reguła wyceny kart (Follow-up (4)) i dostosowanie kart;
  2. strojenie wag;
  3. pomiar balansu 40–60% per frakcja × strona zaczynająca.
  Strojenie wag po pomiarze unieważnia wynik pomiaru.
- **Powiązanie wag z regułą wyceny.** `ATTACK_WEIGHT`/`HEALTH_WEIGHT` oraz wartości słów kluczowych (`GUARD_VALUE`, `OVERTIME_VALUE`) to w praktyce wewnętrzna wycena postaci przez AI. Jeśli te proporcje będą sprzeczne z regułą wyceny kart, AI będzie systematycznie przeceniać lub niedoceniać część kart. Wtedy symulacja zmierzy błąd AI, a nie balans kart. Warto wyprowadzić oba z tych samych założeń albo świadomie opisać różnicę.
- **Zalecenie impl-review S-05** (`sensible-computer-opponent/reviews/impl-review.md:65`): przy obecnych wagach nieśmiertelny atak na postać nigdy nie wygrywa z bezpośrednim atakiem na gracza, więc `FAVORABLE_TRADE` praktycznie nie występuje. To naturalny punkt kontrolny strojenia.
- **Istniejąca bramka.** Test komputer kontra `RandomPlayer` (≥ 80%, `ComputerOpponentTests.java:55-84`) musi przejść po strojeniu. Pilnuje, żeby strojenie nie osłabiło AI.
- **Ten sam poziom obu stron** (Follow-up (3)). Wagi są stałymi statycznymi, więc obie instancje AI w symulacji mają je automatycznie identyczne. Osobne wagi per strona wymagałyby zmiany kodu i nie są potrzebne.

Pozostałe otwarte (do planu): szczegóły limitu czasu tury (4); forma i siła bramki reguły wyceny (Follow-up (4)); zawartość i miejsce śladu symulacji (5); zakres pomiaru talii aggro (3); metoda strojenia wag (ręcznie czy automatyczne przeszukiwanie z symulacją).
