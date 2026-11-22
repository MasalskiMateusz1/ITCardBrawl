# Pełne talie frakcji i balans (S-06) — plan implementacji

## Overview

S-06 zamyka balans M-1. Wprowadzamy jawną regułę wyceny kart (od zera), dostosowujemy do niej wartości 31 kart, stroimy wagi AI tak, by oceniał karty tym samym modelem, i mierzymy balans symulacją komputer kontra komputer z analizowalnym śladem w CI. Na koniec włączamy bramki balansu i długości partii oraz dodajemy limit czasu tury 45 s, żeby partia realnie mieściła się w ≤ 15 min.

## Current State Analysis

- Obie talie mają po 30 kart PL+EN i identyczną krzywą kosztów (`src/main/resources/game/factions.json:57-101`); wartości są zgadniętym szkicem (research, Historical Context).
- Nie istnieje żadna reguła wyceny — `CardCatalog.java:16-36` sprawdza tylko zakresy (koszt 1–10, atak/zdrowie 1–10, `duration` 0–5, kopie 1–2). Są karty zdominowane (`neu-devops` vs `tst-automation`) i rozjazdy ceny tego samego efektu (research, Follow-up (2)).
- Balansu nie mierzy nic. Jedyna symulacja to komputer kontra `RandomPlayer` (`ComputerOpponentTests.java:55-84`, ≥ 80% z 200 partii).
- Symulację komputer–komputer blokuje `private static final Side SIDE = Side.OPPONENT` (`ComputerOpponent.java:31`), używana także w statycznych helperach (`explained`, `actingCard`, `board`, `firstDefeated`, `playerHpLoss`, `ownHpGain`, `drawnCards`, `budgetGain` — `:217-277`).
- `PositionEvaluator` ma własną, niejawną wycenę postaci: `1 + 1.0·atak + 0.7·zdrowie + GUARD 1.0 + OVERTIME 1.5` (`PositionEvaluator.java:42-62,129-139`); przy obecnych wagach `FAVORABLE_TRADE` praktycznie nie występuje (`context/archive/2026-11-21-sensible-computer-opponent/reviews/impl-review.md:65`).
- Zaczynający ma budżet 1/1, drugi 0/0, a jedyną rekompensatą jest brak dobrania w turze 1 (`MatchFactory.java:44-49,66`).
- Limitu czasu tury nie ma. `MatchStore` trzyma w pamięci sam `Match` (`MatchStore.java:18`); `MatchService.move` waliduje numer tury (`MatchService.java:63-84`); `GET /api/matches/current` zwraca stan bez odtwarzania (`MatchController.java:76-81`); SPA odtwarza ruchy komputera co 700 ms (`frontend/src/app/match-table/match-table.ts:29-31`), a 409 odświeża widok po cichu (`match-table.ts:228-229`).
- `ci.yml` uruchamia `./mvnw -B verify` (`.github/workflows/ci.yml:31`) i nie publikuje żadnych artefaktów.

## Desired End State

- `context/foundation/card-pricing.md` opisuje regułę wyceny; `CardPricing` w pakiecie `game` ją implementuje; `CardPricingTests` wywala build, gdy karta z `factions.json` jest poza tolerancją lub zdominowana (poza jawną listą wyjątków z uzasadnieniem).
- Wszystkie karty mieszczą się w regule; id i teksty PL+EN bez zmian.
- Wagi `PositionEvaluator` wynikają z reguły wyceny; `FAVORABLE_TRADE` występuje w symulacji; komputer nadal wygrywa ≥ 80% z graczem losowym.
- `BalanceSimulationTests` (w `./mvnw -B verify`) rozgrywa 1000 partii komputer–komputer z ziarnami, po 250 na każdą komórkę frakcja × strona zaczynająca. Każda komórka mieści się w 40–60% (cel strojenia 45–55%), a p90 szacowanego czasu partii ≤ 15 min. Ślad (CSV per partia, CSV per karta, podsumowanie Markdown z raportem talii aggro) trafia do artefaktu CI i podsumowania joba — także przy zielonym buildzie.
- Gracz ma 45 s na turę: SPA pokazuje odliczanie i po zerze kończy turę; backend kończy turę po terminie także przy zamkniętej karcie (przy następnym żądaniu).

### Key Discoveries:

- Silnik jest czysty, losowość wstrzykiwana: `MatchFactory(CardCatalog, RandomGenerator)`, `ComputerOpponent(MatchEngine, PositionEvaluator, RandomGenerator)`; katalog ładuje się bez Springa (`ComputerOpponentTests.java:28-30`).
- `MatchFactory.create(faction)` zawsze stawia frakcję gracza po stronie PLAYER, a komputer dostaje drugą frakcję (`MatchFactory.java:34-37`) — symulacja to dwie instancje AI (PLAYER i OPPONENT) i dobór frakcji wejściowej.
- Kontrola strony zaczynającej: wzór opakowania generatora (`factoryRandom(seed, playerStarts)` w `ComputerOpponentTests`).
- Remisów nie ma: `MatchResult` ma zawsze zwycięzcę, `EndReason` ∈ {`HP_DEPLETED`, `EMPTY_DECK`} (`MatchResult.java:6-13`).
- `CardCatalog` przyjmuje dane w pamięci (`CardCatalog.java:44`, użyte w `CardCatalogTests.java:78-80`) — talię aggro można zbudować bez zmiany `factions.json`.
- Testy przypięte do wartości kart: `CardCatalogTests.java:84-170` (m.in. `dev-backend` 4/6 GUARD, `dev-senior` HEAL 3, `tst-flaky-test` duration 3, 15 wydarzeń na talię).
- Komentarze w kodzie po angielsku (reguła z zmiany comments-in-english); teksty w UI przez słowniki `frontend/src/app/i18n/{pl,en}.ts`.

## What We're NOT Doing

- Nie przepisujemy nazw ani żartów kart i nie zmieniamy id (decyzja autora, research Follow-up (1)); weryfikacja „znajomi rozpoznają żarty” (`prd.md:49`) zostaje na później.
- Nie budujemy kreatora talii (Non-Goal v1, `prd.md:134`); talia aggro istnieje wyłącznie w teście i nie jest bramką.
- Nie wprowadzamy nowych zdolności ani rodzajów efektów (enumy `AbilityKeyword`, `EffectKind` bez zmian).
- Nie podpinamy `DRAW_PER_TURN` w silniku (decyzja autora, Follow-up (5) p. 7).
- Nie dodajemy automatycznego przeszukiwania wag AI ani osobnych wag per strona.
- Nie dodajemy kar za przekroczenie czasu (tylko automatyczne zakończenie tury) ani przycisku pominięcia odtwarzania; `REPLAY_STEP_MS` zostaje 700 ms.
- Nie poprawiamy kosmetyki logu HEAL „+N przy pełnym HP” (stress-to-hp F6).
- Nie zapisujemy raportów balansu w repozytorium — ślad żyje w artefaktach CI.

## Implementation Approach

Najpierw przegląd źródeł zewnętrznych wyceny (faza 1) jako warunek przed jakimkolwiek kodem, potem narzędzia pomiaru (faza 2: symulacja, faza 3: reguła wyceny oparta na przeglądzie źródeł z fazy 1), potem strojenie w kolejności, która nie unieważnia pomiaru: karty (4) → wagi AI (5) → strojenie do celu i włączenie bramek (6). Limit tury (7) jest niezależny od balansu i idzie na końcu, bo jego wartość (45 s) nie wpływa na symulację — wpływa tylko na szacunek czasu w fazie 6 przez stałe z `MatchRules`.

Bramki w fazie 2 są wyłączone (tylko raport), żeby zmierzyć stan wyjściowy i czas działania symulacji przed jakąkolwiek zmianą danych. Kompensata dla drugiego gracza (karta-moneta) jest warunkowa: wchodzi w fazie 6 tylko wtedy, gdy po strojeniu kart i wag komórki strony zaczynającej nadal wychodzą poza 45–55%.

## Critical Implementation Details

- **Kolejność strojenia:** każda zmiana wag AI (faza 5) unieważnia wcześniejszy pomiar balansu — pomiar rozstrzygający robimy wyłącznie w fazie 6, po ostatniej zmianie wag. Jeśli strojenie w fazie 6 zmusi do poprawki wag, wszystkie komórki trzeba zmierzyć ponownie.
- **Termin tury a odtwarzanie:** odpowiedź serwera po końcu tury gracza zawiera kroki komputera, które SPA odtwarza po 700 ms każdy, zanim gracz może działać. Termin na serwerze = czas odpowiedzi + `TURN_LIMIT` (45 s) + liczba kroków odtwarzania × `REPLAY_STEP` (700 ms, stała backendu odpowiadająca `REPLAY_STEP_MS`) + `TURN_GRACE` (3 s). SPA liczy 45 s od zakończenia odtwarzania. Bez tego gracz traciłby czas na oglądanie ruchów komputera.
- **Wydajność symulacji:** `withEventAt` i `State` kopiują cały log przy każdym ruchu-kandydacie (`Match.java:40-44`) — czas 1000 partii jest nieznany. Faza 2 mierzy go; jeśli 1000 partii trwa > 120 s na lokalnej maszynie, zmniejszamy do największego N podzielnego przez 4, które mieści się w 120 s (minimum 400), i zapisujemy wynik pomiaru oraz wybrane N w `card-pricing.md` / komentarzu testu.

## Phase 1: Przegląd źródeł wyceny (warunek przed implementacją)

### Overview

Zbiera z zewnętrznych źródeł, jak wycenia się karty w Magic: The Gathering, Hearthstone i Disney Lorcana, oraz sprawdza, czy istnieją prace naukowe o wycenie kart i balansie gier karcianych. Faza nie zmienia kodu ani danych. Jest warunkiem wstępnym całej implementacji: żadna kolejna faza nie startuje, dopóki autor nie zaakceptuje wniosków, bo z nich wynikają reguła wyceny, przeliczenie kart i wagi AI.

### Changes Required:

#### 1. Przegląd źródeł zewnętrznych

**File**: `context/changes/full-faction-decks-balance/pricing-sources.md` (nowy)

**Intent**: Zanim powstanie jakikolwiek kod lub wzór, zebrać udokumentowane praktyki wyceny kart z innych gier i z literatury, żeby reguła opierała się na źródłach, a nie na zgadywaniu. Przegląd robimy przez wyszukiwanie w sieci i czytanie stron źródłowych, nie z pamięci modelu.

**Contract**: Sekcje dokumentu:
- **Magic: The Gathering** — zależność statystyk od kosztu many, wycena słów kluczowych, wartość karty w ręce (card advantage), tempo.
- **Hearthstone** — „vanilla test” (statystyki na punkt many), ceny słów kluczowych (np. Taunt, Charge, Battlecry), wycena czarów zadających obrażenia i dobierających karty, ewentualne analizy społeczności (np. regresje kosztu kart).
- **Disney Lorcana** — koszt atramentu vs statystyki, wycena zdolności, czym model różni się od MTG/Hearthstone.
- **Prace naukowe** — wyniki wyszukiwania prac o wycenie kart, balansie gier karcianych i balansowaniu przez symulację AI (np. w Google Scholar, arXiv, materiałach konferencji typu IEEE CoG / AIIDE), z krótkim streszczeniem tego, co z każdej pracy wynika dla S-06. Jeśli nic przydatnego się nie znajdzie, dokument mówi to wprost.
- **Mapowanie na ITCardBrawl** — tabela: która zasada z której gry odpowiada której mechanice u nas (statystyki, 5 zdolności z `AbilityKeyword`, 11 efektów z `EffectKind`, `EffectScope`, `duration`), z uwagą o różnicach (np. 30 HP, budżet do 10, brak many kolorowej).
- **Wnioski dla wzoru** — proponowane założenia, z których korzysta `card-pricing.md`.

Każde twierdzenie ma link do źródła. Dokument oznacza, czy dane pochodzą od wydawcy, ze źródła społeczności, czy z pracy recenzowanej. Dokument w języku polskim.

#### 2. Metoda wyszukiwania: Exa

**Tool**: skill `exa:search` (wyszukiwanie wieloetapowe) przez plugin Exa; przy szerszych pytaniach `exa:exa-agent`.

**Intent**: Zrobić rzetelny research, a nie jedno zapytanie na grę. Exa szuka stron i prac naukowych, a każde twierdzenie w dokumencie opiera się na przeczytanej stronie źródłowej, nie na samym fragmencie z wyników wyszukiwania.

**Contract**:
- **Przed startem:** zalogowanie serwera Exa (`authenticate` → `complete_authentication`). Jeśli Exa jest niedostępne, zatrzymaj się i zapytaj autora, czy użyć zwykłego wyszukiwania w sieci. Bez zgody nie zastępuj Exa wiedzą modelu.
- **Osobne serie zapytań dla każdego obszaru:** MTG, Hearthstone, Lorcana, prace naukowe. Każda seria obejmuje co najmniej:
  - zasady wyceny statystyk względem kosztu (vanilla test, krzywa many lub atramentu);
  - cenę słów kluczowych i zdolności;
  - wycenę czarów i wydarzeń (obrażenia, leczenie, dobieranie, efekty na planszę);
  - przewagę pierwszego gracza i jej kompensatę (np. The Coin w Hearthstone, zasada bez dobrania na start w MTG i Lorcanie);
  - dla prac naukowych: wycena kart, balans gier karcianych i balans mierzony symulacją AI (np. self-play, algorytmy ewolucyjne); źródła takie jak Google Scholar, arXiv, IEEE CoG, AIIDE.
- **Źródła:** w każdym obszarze priorytet mają źródła od wydawcy lub autorów gry (np. artykuły projektantów Wizards of the Coast i Blizzarda, oficjalne zasady Lorcany) oraz prace recenzowane. Źródła społeczności tylko jako uzupełnienie, oznaczone jako takie.
- **Minimum:** co najmniej 3 źródła na grę i co najmniej 3 prace naukowe, albo jawna informacja, że trafnych prac jest mniej.
- **Kryterium zakończenia:** kolejne zapytania nie przynoszą nowych zasad dla wzoru.
- **Log wyszukiwania:** na końcu `pricing-sources.md` sekcja „Log wyszukiwania” z użytymi zapytaniami i liczbą przydatnych wyników, żeby research dało się powtórzyć.

### Success Criteria:

#### Automated Verification:

- `pricing-sources.md` istnieje i ma sekcje MTG, Hearthstone, Lorcana, Prace naukowe, Mapowanie i Wnioski, a każda sekcja gry zawiera co najmniej jeden link do źródła
- Każda sekcja gry ma ≥ 3 źródła, sekcja Prace naukowe ma ≥ 3 prace (albo jawną informację, że jest ich mniej), a dokument kończy się sekcją „Log wyszukiwania” z zapytaniami Exa
- Drzewo robocze nie zawiera zmian poza `context/changes/full-faction-decks-balance/` (`git status --porcelain`)

#### Manual Verification:

- Autor przegląda `pricing-sources.md` i akceptuje wnioski dla wzoru

**Implementation Note**: Twarda bramka. Po tej fazie zatrzymaj się i nie zaczynaj fazy 2, dopóki autor nie zaakceptuje wniosków.

---

## Phase 2: Symulacja komputer kontra komputer i ślad w CI

### Overview

Umożliwia grę AI po obu stronach, dodaje test symulacji w `verify` w trybie raportu (bez bramek) i publikuje ślad w CI. Daje pomiar stanu wyjściowego i czas działania.

### Changes Required:

#### 1. Strona komputera jako parametr

**File**: `src/main/java/com/example/itcardbrawl/game/ComputerOpponent.java`

**Intent**: Zamienić stałą `SIDE` na pole instancji, żeby ten sam algorytm mógł grać stroną PLAYER w symulacji. Ścieżka produkcyjna (bean Springa, `MatchService`) nadal gra stroną OPPONENT.

**Contract**: Nowy konstruktor `ComputerOpponent(MatchEngine, PositionEvaluator, RandomGenerator, Side side)`; istniejący 3-argumentowy konstruktor (używany przez Springa i testy) deleguje z `Side.OPPONENT`. Statyczne helpery zależne od strony stają się metodami instancji albo przyjmują stronę jako argument. `MoveExplained` zapisuje faktyczną stronę.

#### 2. Test symulacji balansu (tryb raportu)

**File**: `src/test/java/com/example/itcardbrawl/game/BalanceSimulationTests.java` (nowy)

**Intent**: Rozegrać N = 1000 partii komputer–komputer z deterministycznymi ziarnami, po równo w 4 komórkach (frakcja gracza ∈ {developers, testers} × strona zaczynająca ∈ {PLAYER, OPPONENT}), zebrać metryki i zapisać ślad. W tej fazie żadna bramka balansu nie jest asercją — test sprawdza tylko, że każda partia kończy się przed `MAX_TURNS` i ma wynik.

**Contract**:
- Obie strony: osobne instancje `ComputerOpponent` z tymi samymi wagami i `TEMPERATURE`, różne ziarna.
- Metryki per partia: ziarno, frakcja PLAYER, frakcja OPPONENT, strona zaczynająca, zwycięzca (frakcja i strona), `EndReason`, liczba tur, ruchy komputera per strona (liczba kroków `playTurnSteps`), końcowe HP obu stron, szacowany czas partii.
- Szacowany czas partii = tury strony „człowieka” × `ASSUMED_HUMAN_TURN_SECONDS` (20 s) + ruchy strony „komputera” × 0,7 s. Dla każdej partii liczymy go z perspektywy obu stron (każda strona raz jako „człowiek”) i bierzemy większą wartość.
- Metryki per karta: liczba zagrań, liczba partii, w których karta została zagrana, odsetek wygranych w tych partiach.
- Talia aggro (tylko raport): w pamięci budowany katalog z testową talią 30 kart o koszcie 1–3 z obecnego katalogu, grana przeciw obu prawdziwym taliom w 4 komórkach (mniejsze N, np. 200), raportowany win rate zaczynającego i niezaczynającego.
- Pliki w `target/balance/`: `matches.csv`, `cards.csv`, `summary.md`. `summary.md` ma tabelę 4 komórek (win rate, przedział ufności 95%), win rate per frakcja, rozkład tur (mediana, p90, max), rozkład szacowanego czasu (mediana, p90), metryki `MoveReason` (w tym liczba `FAVORABLE_TRADE`), sekcję talii aggro oraz czas działania symulacji.

#### 3. Publikacja śladu w CI

**File**: `.github/workflows/ci.yml`

**Intent**: Wysłać `target/balance/` jako artefakt i dopisać `summary.md` do podsumowania joba — zawsze, także przy porażce i przy zielonym buildzie.

**Contract**: W jobie `test` po kroku „Testy backendu”: `actions/upload-artifact@v4` z `if: always()` (nazwa `balance-report`, ścieżka `target/balance/`, `if-no-files-found: warn`) oraz krok z `if: always()` dopisujący `target/balance/summary.md` do `$GITHUB_STEP_SUMMARY`, jeśli plik istnieje. Komentarze po angielsku.

### Success Criteria:

#### Automated Verification:

- Wszystkie testy backendu przechodzą: `./mvnw -B verify`
- `ComputerOpponentTests` (w tym ≥ 80% z graczem losowym) przechodzi bez zmiany progu
- Po `./mvnw -B verify` istnieją `target/balance/matches.csv` (1000 wierszy danych lub zmniejszone N zgodnie z Critical Implementation Details), `target/balance/cards.csv`, `target/balance/summary.md`
- Dwukrotne uruchomienie daje identyczny `matches.csv` (determinizm ziaren)

#### Manual Verification:

- `summary.md` jest czytelny i zawiera tabelę 4 komórek, rozkład tur i czasu, sekcję aggro oraz czas działania; zanotować wyniki stanu wyjściowego
- Na PR w GitHub Actions widać artefakt `balance-report` i podsumowanie w zakładce joba

**Implementation Note**: Po fazie zatrzymaj się na ręczne potwierdzenie i zapisz stan wyjściowy (win rate komórek, p90 czasu, czas działania) — będzie punktem odniesienia dla faz 4–6.

---

## Phase 3: Reguła wyceny kart

### Overview

Na podstawie zaakceptowanego przeglądu źródeł (`pricing-sources.md` z fazy 1) ustala od zera jawny wzór wartości karty w punktach kosztu, implementuje go w kodzie i dodaje test, który blokuje build przy karcie poza regułą. W tej fazie obecne karty mogą nie przechodzić — test dostaje tymczasową listę znanych odchyleń, którą faza 4 opróżnia.

### Changes Required:

#### 1. Dokument reguły

**File**: `context/foundation/card-pricing.md` (nowy)

**Intent**: Opisać regułę dla autora przyszłych kart:
- budżet statystyk na punkt kosztu, z krzywą dla wysokich kosztów, mieszczącą się w zakresie atak/zdrowie 1–10;
- cenę każdej z 5 zdolności, z kwotą dla ON_PLAY_DAMAGE/HEAL;
- cenę każdego z 11 rodzajów efektów, z mnożnikiem zasięgu (`EffectScope`) i czasu trwania;
- tolerancję, definicję karty zdominowanej i zasady listy wyjątków.

Wzór wynika z wniosków z `pricing-sources.md`, nie z obecnych wartości kart.

**Contract**: Sekcje: Źródła (link do `pricing-sources.md` i najważniejsze źródła), Zasady ogólne, Postacie (statystyki + zdolności), Wydarzenia (efekty × zasięg × czas trwania), Tolerancja, Karty zdominowane, Wyjątki, Pomiar balansu (bramki z fazy 6, założenie 20 s na turę człowieka). Przy każdej cenie odnośnik do źródła albo uzasadnienie, dlaczego odbiega od źródeł. Dokument w języku polskim.

#### 2. Implementacja wzoru

**File**: `src/main/java/com/example/itcardbrawl/game/CardPricing.java` (nowy)

**Intent**: Policzyć wartość karty w punktach kosztu według dokumentu, żeby test i przyszły kreator talii miały wspólną miarę. Klasa w `src/main` (nie w teście), bo jest częścią modelu domeny; nie jest wpięta w walidację `CardCatalog`.

**Contract**: `static double value(Card card)` — wartość w punktach kosztu; `static boolean dominates(Card a, Card b)` — `a` dominuje `b`: ten sam typ, koszt `a` ≤ `b`, każda statystyka/kwota `a` ≥ `b`, zdolności/efekty `a` ⊇ `b`, i co najmniej jedna nierówność ostra. Stałe wzoru jako `static final` z komentarzem odsyłającym do `card-pricing.md`.

#### 3. Test reguły

**File**: `src/test/java/com/example/itcardbrawl/game/CardPricingTests.java` (nowy)

**Intent**: Dla każdej karty z prawdziwego katalogu sprawdzić `|value − cost| ≤ TOLERANCE` i brak dominacji między kartami. Karta spoza tolerancji lub zdominowana wywala build, chyba że jest na liście wyjątków (id + uzasadnienie). Dodatkowe testy jednostkowe wzoru na kartach z pamięci (każda zdolność, każdy efekt, zasięg, czas trwania).

**Contract**: Komunikat porażki wymienia każdą kartę z wartością, kosztem i odchyleniem. Lista wyjątków w teście; na koniec fazy 3 zawiera wszystkie obecne odchylenia oznaczone „do przeliczenia w fazie 4”.

### Success Criteria:

#### Automated Verification:

- `./mvnw -B verify` przechodzi
- `CardPricingTests` pokrywa każdą wartość `AbilityKeyword` i `EffectKind` co najmniej jednym testem wzoru
- Test dominacji wykrywa parę `tst-automation` → `neu-devops` (obecnie na liście wyjątków)

#### Manual Verification:

- Autor czyta `card-pricing.md` i akceptuje wzór, ceny zdolności/efektów oraz tolerancję

**Implementation Note**: Po fazie zatrzymaj się na akceptację wzoru przez autora przed przeliczaniem kart.

---

## Phase 4: Przeliczenie kart

### Overview

Dostosowuje wartości kart w `factions.json` do reguły i opróżnia listę tymczasowych wyjątków.

### Changes Required:

#### 1. Dane kart

**File**: `src/main/resources/game/factions.json`

**Intent**: Zmienić koszt, atak/zdrowie, kwoty zdolności, efekty, `duration` i kopie tak, by każda karta przeszła `CardPricingTests`. Id, nazwy, żarty PL+EN, przynależność do frakcji i rozmiar talii (30) bez zmian. Obie talie zachowują 15 postaci i 15 wydarzeń (wymóg `CardCatalogTests.java:100-101`), chyba że autor zdecyduje inaczej przy przeglądzie.

**Contract**: Plik przechodzi walidację `CardCatalog` (zakresy z `CardCatalog.java:16-36`). Lista wyjątków w `CardPricingTests` zawiera tylko świadome wyjątki z uzasadnieniem (docelowo pusta).

#### 2. Testy przypięte do wartości

**File**: `src/test/java/com/example/itcardbrawl/game/CardCatalogTests.java`, oraz w razie potrzeby `MatchLogFormatterTests`, `MatchViewTests`, `MatchControllerTests`

**Intent**: Zaktualizować asercje, które przypinają konkretne statystyki lub efekty kart (`realCatalogHasCardStats`, `realCatalogLoads`), do nowych wartości.

**Contract**: Testy nadal sprawdzają te same id kart; zmieniają się tylko oczekiwane liczby.

### Success Criteria:

#### Automated Verification:

- `./mvnw -B verify` przechodzi
- Lista tymczasowych wyjątków „do przeliczenia w fazie 4” w `CardPricingTests` jest pusta
- `ComputerOpponentTests` z graczem losowym nadal ≥ 80%

#### Manual Verification:

- Porównanie `summary.md` z fazy 2 i bieżącego: zanotować zmianę win rate komórek i p90 czasu
- Autor przegląda zmienione wartości kart pod kątem tego, czy pasują do żartów (np. „Junior” nadal słaby)

**Implementation Note**: Po fazie zatrzymaj się na przegląd wartości przez autora.

---

## Phase 5: Strojenie wag AI

### Overview

Wyprowadza wagi `PositionEvaluator` z reguły wyceny, żeby AI nie przeceniało systematycznie części kart, i naprawia brak `FAVORABLE_TRADE`.

### Changes Required:

#### 1. Wagi oceny pozycji

**File**: `src/main/java/com/example/itcardbrawl/game/PositionEvaluator.java`

**Intent**: Ustawić proporcje `ATTACK_WEIGHT`/`HEALTH_WEIGHT`, `GUARD_VALUE`, `OVERTIME_VALUE` (i ewentualnie `CHARACTER_BASE`) zgodnie z ceną statystyk i zdolności z `card-pricing.md`, przeliczoną na skalę punktów HP. Następnie ręcznie skorygować wagi tak, by korzystna wymiana na postać mogła wygrać z bezpośrednim atakiem na gracza.

**Contract**: Nazwy stałych i struktura `Score` bez zmian. Javadoc każdej zmienionej stałej odsyła do odpowiedniej ceny w `card-pricing.md` albo opisuje świadomą różnicę.

#### 2. Test wymiany

**File**: `src/test/java/com/example/itcardbrawl/game/ComputerOpponentTests.java`

**Intent**: Dodać test na ręcznie zbudowanym stanie, w którym korzystna wymiana na postać jest najlepszym ruchem, i sprawdzić, że komputer ją wybiera z powodem `FAVORABLE_TRADE` (dla 20 ziaren, jak istniejące testy „no dumb moves”).

**Contract**: Istniejące testy `ComputerOpponentTests` i `PositionEvaluatorTests` przechodzą (asercje na konkretne wartości wyniku aktualizowane do nowych wag).

### Success Criteria:

#### Automated Verification:

- `./mvnw -B verify` przechodzi
- Komputer wygrywa ≥ 80% z graczem losowym (próg bez zmian)
- Nowy test `FAVORABLE_TRADE` przechodzi
- `summary.md` raportuje liczbę `FAVORABLE_TRADE` > 0 w symulacji

#### Manual Verification:

- Zagrać partię lokalnie i ocenić, czy powody ruchów komputera brzmią sensownie

**Implementation Note**: Po tej fazie nie zmieniamy wag bez ponownego pełnego pomiaru w fazie 6.

---

## Phase 6: Bramki balansu i długości partii

### Overview

Stroi wartości kart do celu 45–55% w każdej komórce, w razie potrzeby wprowadza kartę-monetę dla drugiego gracza, a potem włącza bramki w `BalanceSimulationTests`.

### Changes Required:

#### 1. Strojenie do celu

**File**: `src/main/resources/game/factions.json`

**Intent**: Iteracyjnie korygować wartości kart (w granicach tolerancji reguły wyceny), aż każda komórka frakcja × strona zaczynająca mieści się w 45–55%, a p90 szacowanego czasu partii ≤ 15 min.

**Contract**: `CardPricingTests` przechodzi po każdej iteracji. Zmiana wymagająca wyjścia poza tolerancję oznacza zmianę reguły w `card-pricing.md` (z uzasadnieniem), nie wyjątek.

#### 2. Karta-moneta dla drugiego gracza (warunkowo)

**File**: `src/main/java/com/example/itcardbrawl/game/MatchFactory.java`, `src/main/resources/game/factions.json`, `CatalogData`/`CardCatalog`

**Intent**: Tylko jeśli po korekcie kart komórki strony zaczynającej nadal wychodzą poza 45–55% w tym samym kierunku (przewaga zaczynającego). Wtedy strona niezaczynająca dostaje na start dodatkową kartę w ręce: egzemplarz `neu-coffee` (+2 budżetu w tej turze) z unikalnym `instanceId`, spoza talii. Jeśli komórki mieszczą się w celu bez monety, ta zmiana nie jest implementowana, a w `card-pricing.md` zapisujemy wynik pomiaru, który to uzasadnia.

**Contract**: W `factions.json` opcjonalne pole najwyższego poziomu `secondPlayerBonus: "<card id>"`, walidowane przez `CardCatalog` (karta istnieje, jest neutralna). `MatchFactory.deal` dodaje ją do ręki strony niezaczynającej (ręka 6 kart, `HAND_LIMIT` 10 bez zmian). Test w `MatchFactoryTests` na obie strony zaczynające.

#### 3. Włączenie bramek

**File**: `src/test/java/com/example/itcardbrawl/game/BalanceSimulationTests.java`

**Intent**: Zamienić raport na bramkę: każda z 4 komórek ma win rate w [40%, 60%], a p90 szacowanego czasu partii ≤ 900 s. Talia aggro pozostaje tylko raportem. Ślad nadal powstaje także przy porażce (pliki zapisane przed asercjami).

**Contract**: Komunikat porażki podaje komórkę, win rate, przedział i ścieżkę `target/balance/summary.md`. Stałe `CELL_MIN_WIN_RATE = 0.40`, `CELL_MAX_WIN_RATE = 0.60`, `MAX_P90_MATCH_SECONDS = 900`, `ASSUMED_HUMAN_TURN_SECONDS = 20` z komentarzem odsyłającym do `card-pricing.md`.

#### 4. Dokumentacja pomiaru

**File**: `context/foundation/card-pricing.md`

**Intent**: Dopisać wynik ostatecznego pomiaru (win rate komórek, p90 czasu, N, czas działania), decyzję o monecie z uzasadnieniem i instrukcję: przy każdej zmianie kart sprawdzić artefakt `balance-report`.

**Contract**: Sekcja „Pomiar balansu” zaktualizowana.

### Success Criteria:

#### Automated Verification:

- `./mvnw -B verify` przechodzi z włączonymi bramkami
- `summary.md`: każda komórka 45–55% (cel), wszystkie w 40–60% (bramka), p90 szacowanego czasu ≤ 15 min
- `CardPricingTests` przechodzi z pustą listą wyjątków lub wyłącznie wyjątkami z uzasadnieniem
- `ComputerOpponentTests` z graczem losowym ≥ 80%
- Celowe zepsucie jednej karty (np. koszt 1 dla `neu-client`) wywala `CardPricingTests` lub `BalanceSimulationTests` (sprawdzić i cofnąć)

#### Manual Verification:

- Autor rozgrywa po 1 partii każdą frakcją lokalnie i ocenia, czy obie są grywalne i żadna nie wydaje się jednostronna

**Implementation Note**: Po fazie zatrzymaj się na ręczne potwierdzenie przed limitem tury.

---

## Phase 7: Limit czasu tury 45 s

### Overview

Wprowadza limit 45 s na turę gracza: backend pilnuje terminu i kończy turę automatycznie, SPA pokazuje odliczanie i po zerze wysyła koniec tury.

### Changes Required:

#### 1. Stałe i zegar

**File**: `src/main/java/com/example/itcardbrawl/game/MatchRules.java`, konfiguracja Springa (`GameConfig.java`)

**Intent**: Dodać `TURN_LIMIT` (45 s), `TURN_GRACE` (3 s) i `REPLAY_STEP` (700 ms, odpowiednik `REPLAY_STEP_MS` w SPA); udostępnić bean `Clock` (systemowy UTC), żeby testy mogły wstrzyknąć zegar stały.

**Contract**: Stałe jako `java.time.Duration`; bean `Clock` w `GameConfig`.

#### 2. Termin tury w warstwie usługi

**File**: `src/main/java/com/example/itcardbrawl/game/MatchStore.java`, `MatchService.java`, `MatchOutcome.java`

**Intent**: Przechowywać obok `Match` termin bieżącej tury gracza, poza rekordem `Match` (silnik i symulacja zostają wolne od czasu). Termin ustawiany, gdy tura gracza się zaczyna (start partii lub koniec tury komputera) = teraz + `TURN_LIMIT` + liczba kroków odtwarzania × `REPLAY_STEP` + `TURN_GRACE`. Przy każdym `move` i `current`, jeśli gracz jest aktywny i termin minął, usługa najpierw stosuje `EndTurn` gracza i turę komputera (jak przy ręcznym końcu tury), atomowo w tym samym `store.update`.

**Contract**:
- `MatchStore` przechowuje parę (`Match`, `Instant playerTurnDeadline`, `null` gdy nie tura gracza); `update` operuje na tej parze.
- `MatchService.current` zwraca `Optional<MatchOutcome>` (z odtwarzaniem, jeśli tura została zakończona automatycznie).
- `move` po automatycznym końcu tury rzuca `StaleMoveException`, bo numer tury się zmienił (SPA odświeży widok po 409).
- `MatchOutcome` niesie termin.

#### 3. API

**File**: `src/main/java/com/example/itcardbrawl/api/MatchView.java`, `MatchController.java`

**Intent**: Udostępnić SPA limit tury, żeby licznik nie zależał od zegara klienta.

**Contract**: `TurnView` dostaje `Integer timeLimitSeconds` (45 w turze gracza, `null` poza nią). `GET /api/matches/current` zwraca odtwarzanie z `MatchOutcome`, gdy tura została zakończona automatycznie. Testy w `MatchControllerTests` z zegarem stałym: termin minął → odpowiedź zawiera turę komputera; termin nie minął → bez zmian.

#### 4. Odliczanie w SPA

**File**: `frontend/src/app/match-table/match-table.ts`, `match-table.html`, `match-table.css`, `frontend/src/app/game/match.ts`, `frontend/src/app/i18n/{messages,pl,en}.ts`

**Intent**: Gdy jest tura gracza i odtwarzanie się skończyło, startuje licznik od `turn.timeLimitSeconds`. Pokazujemy pozostały czas obok przycisku „Zakończ turę” (wyróżnienie przy ≤ 10 s). Przy zerze SPA wywołuje `endTurn()`, o ile nie ma żądania w toku. Licznik zatrzymuje się przy zmianie tury, końcu partii i zniszczeniu komponentu.

**Contract**: Typ `TurnView` w `match.ts` dostaje `timeLimitSeconds: number | null`. Teksty PL/EN w słownikach (np. „Pozostało: {s} s” / „Time left: {s} s”). Specy z fałszywym zegarem (`vi.useFakeTimers` lub odpowiednik używany w `match-table.spec.ts`): licznik startuje po odtwarzaniu, przy zerze wysyła end-turn, nie wysyła go przy żądaniu w toku.

### Success Criteria:

#### Automated Verification:

- `./mvnw -B verify` przechodzi (w tym nowe testy `MatchServiceTests`/`MatchControllerTests` z zegarem stałym)
- Testy frontu przechodzą: `cd frontend && npx ng test --watch=false`
- Build SPA przechodzi: `cd frontend && npm run build`

#### Manual Verification:

- Lokalnie: licznik pojawia się po odtwarzaniu ruchów komputera, odlicza od 45 s i po zerze tura kończy się sama
- Lokalnie: zamknięcie karty na > 50 s i powrót → widok pokazuje turę komputera już rozegraną
- Na wdrożonej wersji: 3 partie z pomiarem stopera, każda ≤ 15 min

**Implementation Note**: Po fazie zatrzymaj się na ręczne potwierdzenie, w tym pomiar partii na wdrożonej wersji.

---

## Testing Strategy

### Unit Tests:

- `CardPricingTests`: wzór dla każdej zdolności i efektu, zasięg i czas trwania, dominacja, tolerancja na prawdziwym katalogu.
- `ComputerOpponentTests`: konstruktor ze stroną PLAYER gra poprawnie (ruchy i powody z `side == PLAYER`), test `FAVORABLE_TRADE`, istniejąca bramka ≥ 80%.
- `MatchFactoryTests`: karta-moneta dla strony niezaczynającej (tylko jeśli wprowadzona).
- `MatchServiceTests`: automatyczny koniec tury po terminie, termin z uwzględnieniem kroków odtwarzania, brak zmiany przed terminem, `StaleMoveException` po automatycznym końcu.

### Integration Tests:

- `BalanceSimulationTests`: 1000 partii, 4 komórki 40–60%, p90 czasu ≤ 15 min, ślad w `target/balance/`.
- `MatchControllerTests`: `TurnView.timeLimitSeconds` i automatyczny koniec tury przez `GET /api/matches/current`.
- `match-table.spec.ts`: licznik, auto end-turn, interakcja z odtwarzaniem.

### Manual Testing Steps:

1. Po fazie 2 i 6 przejrzeć artefakt `balance-report` w GitHub Actions.
2. Rozegrać lokalnie po partii każdą frakcją, ocenić powody ruchów i grywalność.
3. Przetestować limit tury: bezczynność do zera, zamknięcie karty, ruch tuż przed terminem.
4. Na wdrożonej wersji zmierzyć stoperem 3 partie (≤ 15 min każda).

## Performance Considerations

- Czas 1000 partii nieznany; faza 2 mierzy go i ewentualnie zmniejsza N (minimum 400, podzielne przez 4), zapisując uzasadnienie. Symulacja wydłuża `verify` w CI; dopuszczalne do ~2 min.
- Termin tury nie wprowadza zadań w tle — sprawdzany leniwie przy żądaniu.

## Migration Notes

- `MatchStore` jest w pamięci; zmiana struktury przechowywania nie wymaga migracji (partie giną przy restarcie, jak dziś).
- Zmiana wartości kart w trakcie trwającej partii nie dotyczy — wdrożenie restartuje proces.

## References

- Research: `context/changes/full-faction-decks-balance/research.md`
- Przegląd źródeł wyceny (powstaje w fazie 1): `context/changes/full-faction-decks-balance/pricing-sources.md`
- Roadmapa: `context/foundation/roadmap.md` (S-06)
- PRD: `context/foundation/prd.md:49,55,134`
- Wzór pętli symulacji: `src/test/java/com/example/itcardbrawl/game/ComputerOpponentTests.java:55-84`
- Zalecenie wag: `context/archive/2026-11-21-sensible-computer-opponent/reviews/impl-review.md:65`
- Obsługa 409 w SPA: `frontend/src/app/match-table/match-table.ts:228-229`

## Progress

> Convention: `- [ ]` pending, `- [x]` done. Append ` — <commit sha>` when a step lands. Do not rename step titles. See `references/progress-format.md`.

### Phase 1: Przegląd źródeł wyceny (warunek przed implementacją)

#### Automated

- [x] 1.1 `pricing-sources.md` ma sekcje MTG, Hearthstone, Lorcana, Prace naukowe, Mapowanie, Wnioski z linkami — 88295f5
- [x] 1.2 ≥ 3 źródła na grę, ≥ 3 prace naukowe (lub jawny brak), sekcja „Log wyszukiwania” z zapytaniami Exa — 88295f5
- [x] 1.3 Brak zmian w kodzie i danych poza folderem zmiany — 88295f5

#### Manual

- [x] 1.4 Autor akceptuje wnioski z `pricing-sources.md` — 88295f5

### Phase 2: Symulacja komputer kontra komputer i ślad w CI

#### Automated

- [x] 2.1 Wszystkie testy backendu przechodzą: `./mvnw -B verify` — b937e77
- [x] 2.2 `ComputerOpponentTests` (w tym ≥ 80% z graczem losowym) przechodzi bez zmiany progu — b937e77
- [x] 2.3 Po `./mvnw -B verify` istnieją `target/balance/matches.csv`, `cards.csv`, `summary.md` — b937e77
- [x] 2.4 Dwukrotne uruchomienie daje identyczny `matches.csv` (determinizm ziaren) — b937e77

#### Manual

- [x] 2.5 `summary.md` czytelny; zanotowany stan wyjściowy (komórki, p90 czasu, czas działania) — b937e77
- [x] 2.6 Na PR widać artefakt `balance-report` i podsumowanie joba — b937e77

### Phase 3: Reguła wyceny kart

#### Automated

- [x] 3.1 `./mvnw -B verify` przechodzi — 334615f
- [x] 3.2 `CardPricingTests` pokrywa każdą wartość `AbilityKeyword` i `EffectKind` — 334615f
- [x] 3.3 Test dominacji wykrywa parę `tst-automation` → `neu-devops` — 334615f

#### Manual

- [x] 3.4 Autor akceptuje `card-pricing.md` (wzór, ceny, tolerancja) — 334615f

### Phase 4: Przeliczenie kart

#### Automated

- [x] 4.1 `./mvnw -B verify` przechodzi — 17f4412
- [x] 4.2 Lista tymczasowych wyjątków w `CardPricingTests` jest pusta — 17f4412
- [x] 4.3 `ComputerOpponentTests` z graczem losowym nadal ≥ 80% — 17f4412

#### Manual

- [x] 4.4 Porównanie `summary.md` z fazą 2 zanotowane — 17f4412
- [x] 4.5 Autor przegląda wartości kart pod kątem żartów — 17f4412

### Phase 5: Strojenie wag AI

#### Automated

- [x] 5.1 `./mvnw -B verify` przechodzi — 044f20a
- [x] 5.2 Komputer wygrywa ≥ 80% z graczem losowym — 044f20a
- [x] 5.3 Nowy test `FAVORABLE_TRADE` przechodzi — 044f20a
- [x] 5.4 `summary.md` raportuje `FAVORABLE_TRADE` > 0 — 044f20a

#### Manual

- [x] 5.5 Partia lokalna: powody ruchów komputera brzmią sensownie — 044f20a

### Phase 6: Bramki balansu i długości partii

#### Automated

- [x] 6.1 `./mvnw -B verify` przechodzi z włączonymi bramkami — 9f127a5
- [x] 6.2 Każda komórka 45–55%, wszystkie w 40–60%, p90 czasu ≤ 15 min — 9f127a5
- [x] 6.3 `CardPricingTests` przechodzi bez nieuzasadnionych wyjątków — 9f127a5
- [x] 6.4 `ComputerOpponentTests` z graczem losowym ≥ 80% — 9f127a5
- [x] 6.5 Celowe zepsucie karty wywala bramkę (sprawdzone i cofnięte) — 9f127a5

#### Manual

- [x] 6.6 Autor rozgrywa po partii każdą frakcją i ocenia grywalność — 9f127a5

### Phase 7: Limit czasu tury 45 s

#### Automated

- [x] 7.1 `./mvnw -B verify` przechodzi (w tym testy z zegarem stałym) — a057a99
- [x] 7.2 Testy frontu przechodzą: `npx ng test --watch=false` — a057a99
- [x] 7.3 Build SPA przechodzi: `npm run build` — a057a99

#### Manual

- [x] 7.4 Licznik startuje po odtwarzaniu, odlicza od 45 s, po zerze tura kończy się sama — a057a99
- [x] 7.5 Zamknięcie karty > 50 s i powrót pokazuje rozegraną turę komputera — a057a99
- [ ] 7.6 Na wdrożonej wersji 3 partie zmierzone stoperem, każda ≤ 15 min
