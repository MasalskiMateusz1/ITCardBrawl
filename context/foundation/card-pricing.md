# Reguła wyceny kart

**Status:** wersja 1 (S-06, faza 3, 2026-11-22), stosowana w fazach 4–6. Faza 6 (2026-11-22): strojenie kart w tolerancji, karta dla drugiego gracza (`secondPlayerBonus`) i włączone bramki balansu (sekcja „Pomiar balansu”).
**Kod:** `src/main/java/com/example/itcardbrawl/game/CardPricing.java` (stałe wzoru), `src/test/java/com/example/itcardbrawl/game/CardPricingTests.java` (sprawdzenie katalogu).

Dokument mówi, ile punktów kosztu jest warta karta. Autor nowej karty liczy jej wartość według tego wzoru i ustawia koszt na wartość zaokrągloną do liczby całkowitej. Test `CardPricingTests` sprawdza każdą kartę z `factions.json` i wywala build, jeśli karta jest poza tolerancją albo jest zdominowana przez inną kartę.

Wzór powstał od zera na podstawie przeglądu źródeł, a nie z obecnych wartości kart. Obecne karty mogą więc z nim się nie zgadzać (sekcja „Wyjątki”).

---

## Źródła

Pełny przegląd: [`context/changes/full-faction-decks-balance/pricing-sources.md`](../changes/full-faction-decks-balance/pricing-sources.md). Wiążące są tam sekcje „Wnioski dla wzoru” i „Decyzje autora (akceptacja fazy 1, 2026-11-22)”.

Najważniejsze źródła, na które powołują się ceny poniżej (oznaczenia jak w przeglądzie):

| Skrót                | Źródło                                                                                       | Do czego                                                                                                                                                      |
| -------------------- | -------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **HS-2c+1**          | Forum Blizzarda „Card health amounts and why”; Mr Wong's Hearthstone Blog [społeczność]      | budżet statystyk 2 × koszt + 1, Taunt = +1 pkt statystyk                                                                                                      |
| **Bursztein**        | E. Bursztein, „How to find undervalued Hearthstone cards automatically” (2014) [społeczność] | ceny w manie za punkt: Damage 0,82, Board Damage 1,84, Draw 1,84, Freeze 1,02, Destroy 5,33, Taunt 0,51, Charge 0,33/atak, Heal 0,34; model liniowy addytywny |
| **Block**            | G. Block, „Hearthstone: Mana cost” (CTOForADay, 2014) [społeczność]                          | regresja HS: atak 0,504, zdrowie 0,439                                                                                                                        |
| **MDS**              | Magic Data Science, „The Empirical Vanilla Test in MTG” [społeczność]                        | atak droższy od zdrowia (0,78 vs 0,53 MV), wyraz wolny karty                                                                                                  |
| **MTGS**             | bravelion83, „The price is right” (MTG Salvation) [społeczność]                              | burn: 2 ≈ 1, 3 ≈ 2, 4 ≈ 4, 5 ≈ 6 many; dobieranie: 1 ≈ 1, 2 ≈ 3, 3 ≈ 4, 4 ≈ 5; obrażenia tylko w gracza to „drawback”                                         |
| **WotC-heal**        | G. Verhey „The Meaning of Life”, M. Rosewater „Psychological Reactions” [wydawca]            | leczenie jest słabe                                                                                                                                           |
| **Hashe**            | D. Hashe, regresja kosztów Lorcany [społeczność]                                             | 87% kart w ±½ kosztu od wzoru                                                                                                                                 |
| **Silva**            | de Mesentier Silva i in., arXiv:1907.01623 [preprint]                                        | +1 koszt ≈ +1/+1                                                                                                                                              |
| **Brode**            | B. Brode (PC Gamer 2015, Ars Technica 2018) [wydawca]                                        | Charge najtrudniejszy do zbalansowania                                                                                                                        |
| **arXiv:2609.06816** | praca o LoCM [preprint]                                                                      | szum pomiaru ±3 p.p. przy 1000 partii                                                                                                                         |

Każda cena bez źródła jest oznaczona jako **założenie własne** i ma uzasadnienie z mechaniki gry (`MatchEngine`, `CharacterStats`). Takie ceny weryfikuje symulacja komputer kontra komputer (`BalanceSimulationTests`, metryki per karta w `cards.csv`).

---

## Zasady ogólne

1. **Jednostka:** punkt kosztu (1 punkt budżetu). Wszystkie ceny są podane w tej jednostce.
2. **Model liniowy addytywny** (Bursztein, założenia 1–5; regresje MDS i Hashe, R² ≈ 0,93–0,95):
   - postać: `wartość = BAZA + atak × cena_ataku + zdrowie × cena_zdrowia + cena_zdolności`;
   - wydarzenie: `wartość = BAZA + Σ cena_efektu`.
3. **Baza karty `CARD_BASE = −0,5`** dla każdej karty. Wynika z budżetu HS-2c+1: postać za `c` dostaje `2c + 1` punktów statystyk, czyli statystyki warte `c + 0,5`. Odpowiada to wyrazowi wolnemu z MDS (ok. 0,6 MV) i Burszteina (wartość samej karty, bo liczba dobrań jest ograniczona). Tę samą bazę dostają wydarzenia, żeby karta była zawsze wyceniana tak samo. Przy tej bazie ceny efektów odtwarzają tabele źródeł (sekcja „Wydarzenia”).
4. **Ta sama zdolność lub efekt ma tę samą cenę na każdej karcie** (Bursztein, założenie 3). Dlatego `ON_PLAY_DAMAGE` i `PLAYER_DAMAGE` mają tę samą cenę za punkt, a nadana Ochrona kosztuje tyle co zdolność `GUARD`.
5. **Efekty się sumują.** MTGS zauważa, że suma dwóch efektów nie zawsze kosztuje sumę cen. Przy kartach z 1–2 efektami to pomijamy, a ewentualne odchylenie pokaże symulacja.
6. **Wzór jest hipotezą.** Wszystkie źródła (Warner, Bursztein, Mahlmann, García-Sánchez) mówią, że wzór trzeba zweryfikować grą. Zmiana ceny wymaga zmiany tego dokumentu i stałej w `CardPricing`, a nie wyjątku.

---

## Postacie

### Statystyki

| Stała | Wartość | Źródło / uzasadnienie |
|---|---|---|
| `ATTACK_PRICE` | 4/7 ≈ **0,571** za punkt ataku | Decyzja autora: atak : zdrowie = **1,0 : 0,75**. Skala: średnio 2 punkty statystyk na punkt kosztu (HS-2c+1; Silva: +1 koszt ≈ +1/+1), więc (0,571 + 0,429) / 2 = 0,5. Zgodne z HS: atak 0,50–0,57 (Block, Bursztein). |
| `HEALTH_PRICE` | 3/7 ≈ **0,429** za punkt zdrowia | 0,75 × `ATTACK_PRICE` (decyzja autora). HS: zdrowie 0,40–0,44. |

**Krzywa dla wysokich kosztów.** Wzór jest liniowy w całym zakresie 1–10, bez spłaszczenia (decyzja autora do pkt 2: „karty za 9–10 muszą mieć zdolności albo wyższe statystyki niż karty tańsze”). Karta bez zdolności o statystykach `c / c+1` albo `c+1 / c` ma wartość `c ± 0,07`, czyli mieści się w tolerancji dla każdego kosztu 1–9. Dla kosztu 10 jedyną taką kartą jest 10/10 (wartość 9,5, na granicy tolerancji). Każda inna karta za 10 musi mieć zdolność. Limit walidacji (atak i zdrowie ≤ 10, `CardCatalog`) jest więc zachowany bez osobnej krzywej.

Przykładowe postacie bez zdolności:

| Koszt | Statystyki w tolerancji (wartość) |
|---|---|
| 1 | 1/1 (0,50), 1/2 (0,93), 2/1 (1,07) |
| 2 | 2/2 (1,50), 2/3 (1,93), 3/2 (2,07) |
| 3 | 3/3 (2,50), 3/4 (2,93), 4/3 (3,07) |
| 4 | 4/4 (3,50), 4/5 (3,93), 5/4 (4,07) |
| 5 | 5/5 (4,50), 5/6 (4,93), 6/5 (5,07) |
| 6 | 6/6 (5,50), 6/7 (5,93), 7/6 (6,07) |
| 7 | 7/7 (6,50), 7/8 (6,93), 8/7 (7,07) |
| 8 | 8/8 (7,50), 8/9 (7,93), 9/8 (8,07) |
| 9 | 9/9 (8,50), 9/10 (8,93), 10/9 (9,07) |
| 10 | 10/10 (9,50) — granica; zalecana zdolność |

Karta 1/1 za 1 ma wartość 0,5, czyli leży na dolnej granicy tolerancji. To zgodne z MagicProfessor („1/1 za 1 jest niegrywalne, bo się nie wymienia”). Lorcania daje tanim kartom premię +1/+2. Nie wprowadzamy jej, bo w HS (najbliższy model walki) jej nie ma.

### Zdolności (`AbilityKeyword`)

| Zdolność | Cena | Źródło / uzasadnienie |
|---|---|---|
| `GUARD` | **0,5** (stała) | 1 punkt statystyk: Taunt = +1 pkt (HS-2c+1), 0,51 many (Bursztein); Bodyguard 0,40 (Hashe). U nas GUARD blokuje też atak w gracza, jak Taunt. |
| `RUSH` | **0,33 × atak** | Bursztein: Charge 0,33 many za punkt ataku. Nasz RUSH pozwala atakować też gracza, więc to Charge, nie HS-owy Rush. Brode: Charge jest najtrudniejszy do zbalansowania, więc bierzemy górną cenę ze źródła, bez rabatu. |
| `ON_PLAY_DAMAGE N` | **0,65 × N** | Przegląd: 1,2–1,5 pkt statystyk za obrażenie = 0,6–0,75 kosztu. Dwie niezależne drogi dają 0,65: (a) HS Damage 0,82 (dowolny cel) z rabatem ok. 20%, bo trafia tylko gracza (MTGS: „drawback”); (b) MTG Lava Axe (5 obrażeń tylko w gracza za 5 many) przy 20 HP, przeskalowane do naszych 30 HP: 1,0 × 20/30 ≈ 0,67. Cena jest liniowa, bo źródła nie podają wzrostu ceny dla obrażeń tylko w gracza. |
| `HEAL N` | **0,3 × N** | Bursztein: Heal 0,34 many/pkt; przegląd: ok. 0,6 pkt statystyk za HP. WotC-heal: leczenie jest słabe, więc bierzemy dolną cenę. Leczenie nie przekracza 30 HP (`MatchRules.MAX_HP`), więc część może przepaść. |
| `OVERTIME` | **1,3** (= `ON_PLAY_DAMAGE 2`) | Brak źródła. Decyzja autora: start od ceny `ON_PLAY_DAMAGE 2`. Mechanika: 1 obrażenie na koniec każdej własnej tury, póki postać żyje, czyli wycena zakłada ok. 2 tury przeżycia. **Do weryfikacji** metryką per karta z symulacji. |

---

## Wydarzenia

Cena efektu zależy od rodzaju (`EffectKind`), kwoty, zasięgu (`EffectScope`) i, dla modyfikatorów, czasu trwania (`duration`). Efekty natychmiastowe działają raz, więc `duration` ich nie mnoży.

### Ważna różnica względem HS i MTG

Obrażenia postaci znikają na koniec każdej tury (`MatchEngine.endTurn`, krok 2). Wynika z tego, że:
- **leczenie postaci jest prawie bezwartościowe**: przywraca tylko obrażenia z tej samej tury, a postać, która je przeżyła, i tak zostanie wyleczona na koniec tury;
- **obrażenia, które nie zabijają, przepadają**. Cen obrażeń z HS (gdzie obrażenia zostają) na razie nie obniżamy, ale to pierwsza rzecz do sprawdzenia w symulacji (sekcja „Pomiar balansu”).

### Efekty natychmiastowe

| Efekt | Cena | Źródło / uzasadnienie |
|---|---|---|
| `PLAYER_DAMAGE N` (OPPONENT) | **0,65 × N** | Tak samo jak `ON_PLAY_DAMAGE` (Zasady ogólne, pkt 4). |
| `DAMAGE N` (TARGET) | **0,8 za punkt do 3**, **1,2 za każdy punkt powyżej 3** | Bursztein: Damage 0,82/pkt. MTGS: cena krańcowa rośnie z kwotą (2 ≈ 1, 3 ≈ 2, 4 ≈ 4, 5 ≈ 6 many), bo duża kwota zabija większe postacie. Z bazą −0,5: 2 → 1,1; 3 → 1,9; 4 → 3,1; 5 → 4,3. Dla 4–5 to mniej niż w MTG, ale więcej niż Fireball w HS (6 za 4, Bursztein) — kompromis między źródłami. Cel to postać **lub** gracz, czyli „upside” względem `PLAYER_DAMAGE` (MTGS). |
| `HEAL N` (TARGET) | **0,3 × N** | Cel może być graczem, a leczenie gracza jest jedynym sensownym użyciem (patrz wyżej). Cena jak zdolność `HEAL`. |
| `HEAL N` na postaciach | **0,1 × N za postać** | **Założenie własne** z mechaniki (obrażenia postaci znikają co turę). Cena jest niezerowa, bo leczenie po własnym ataku pozwala jeszcze przetrwać obrażenia w tej samej turze (np. z `DAMAGE ALL_BOARDS`). |
| `DRAW N` | **1,8 za pierwszą kartę**, **1,2 za każdą następną** | Bursztein: Draw Card 1,84. MTGS: kolejne karty są tańsze (1 ≈ 1, 2 ≈ 3, 3 ≈ 4, 4 ≈ 5 many). Z bazą −0,5: 1 → 1,3; 2 → 2,5; 3 → 3,7 (MTG 4); 4 → 4,9 (MTG 5). Limit ręki 10 (`MatchRules.HAND_LIMIT`) może spalić dobraną kartę, a DRAW przyspiesza koniec talii. |
| `BUDGET_GAIN N` | **0,5 × N** | **Założenie własne** (decyzja autora). Najbliższy odpowiednik to The Coin w HS (0 many, +1 na jedną turę), bez ceny liczbowej. Jednorazowe przyspieszenie: wymaga karty, na którą można wydać budżet, i zużywa kartę, więc punkt budżetu jest wart połowę punktu kosztu. Np. +2 za 1 (`neu-coffee`) ma wartość 0,5. |
| `BUDGET_MAX N` | **1,5 × N** | **Założenie własne** (decyzja autora). Trwały +1 budżetu w każdej kolejnej turze (rampa MTG, brak ceny w źródłach). Zwrot rozkłada się na kilka tur i znika przy budżecie 10 (`MatchRules.BUDGET_CAP`), więc cena jest wyraźnie wyższa od `BUDGET_GAIN`, ale niższa od dobrania 1 karty. |

### Modyfikatory (`duration` > 0)

Modyfikator działa na każdą postać w zasięgu w każdej turze, póki wydarzenie jest aktywne. Cena = cena za postać i turę × mnożnik zasięgu × współczynnik czasu trwania.

| Efekt | Cena za postać i turę | Źródło / uzasadnienie |
|---|---|---|
| `ATTACK ±N` | **0,571 × N** | Cena punktu ataku (Postacie). Ujemna kwota na wrogich postaciach to korzyść. |
| `HEALTH ±N` | **0,429 × N** | Cena punktu zdrowia (Postacie). |
| `GUARD` (nadana) | **0,5** | Cena zdolności `GUARD`. |
| `RUSH` (nadana) | **0,99** = 0,33 × 3 | Cena zdolności `RUSH` dla postaci o ataku 3 (`REFERENCE_ATTACK`, typowa postać za 3). **Założenie własne:** atak przyszłej postaci nie jest znany przy wycenie wydarzenia. |
| `TRAP` (TARGET) | **2,0**, najwyżej **5,33** łącznie | Decyzja autora: TRAP jest wyceniany **wyżej** niż Freeze (1,02, Bursztein), bo w praktyce usuwa postać: uwięziona postać nie atakuje, nie może być celem ataku ani wydarzenia i nie daje Ochrony (`CharacterStats`, `MatchEngine`). 2,0 to ok. 2 × Freeze. Górna granica to Destroy Minion (5,33, Bursztein), bo czasowe usunięcie nie może być warte więcej niż trwałe. |

### Zasięg (`EffectScope`)

| Zasięg | Mnożnik | Źródło / uzasadnienie |
|---|---|---|
| `TARGET` | **× 1** | Właściciel wybiera cel. Modyfikator na wybraną postać liczy się jako korzyść niezależnie od znaku (wzmocnienie własnej albo osłabienie wrogiej). |
| `OPPONENT`, `SELF` | **× 1** | Efekty na gracza (`PLAYER_DAMAGE`, `DRAW`, `BUDGET_*`). |
| `ENEMY_BOARD`, `OWN_BOARD` | **× 2,2** | Bursztein: Board Damage 1,84 vs Damage 0,82 (× ≈ 2,2). Plansza ma najwyżej 5 postaci (`MatchRules.BOARD_LIMIT`), a zwykle ok. 2–3. Kierunek: efekt korzystny dla przeciwnika ma wartość **ujemną** (np. `ATTACK +2` na `ENEMY_BOARD`). |
| `RUSH` na `OWN_BOARD` | **× 1** | **Założenie własne** z mechaniki: postacie już leżące na stole i tak mogą atakować, więc korzysta tylko postać zagrana w tej turze, zwykle jedna. |
| `ENEMY_ALL` (DAMAGE) | **× 2,2 + część gracza** | `DAMAGE N ENEMY_BOARD` + `PLAYER_DAMAGE N`. Przegląd: „ENEMY_ALL trochę drożej” niż `ENEMY_BOARD`; dodajemy dokładnie cenę obrażeń w gracza (Zasady ogólne, pkt 4). |
| `OWN_ALL` (HEAL) | **× 2,2 + część gracza** | `HEAL N OWN_BOARD` + leczenie gracza (0,3 × N). |
| `ALL_BOARDS` | **× 2,2 × 0,5 = × 1,1** | Decyzja autora: start od proponowanego rabatu względem zasięgu tylko wrogiego (MTGS: efekt raniący też własną stronę to „drawback”). **Założenie własne co do wielkości:** 50%, bo efekt działa na obie strony po równo, a gracz wybiera moment, w którym przeciwnik ma więcej na stole. Ta sama zasada dotyczy modyfikatorów: liczy się wartość bezwzględna z rabatem. |

### Czas trwania (`duration`)

Wydarzenie o czasie trwania `d` działa przez resztę bieżącej tury właściciela, `d` tur przeciwnika i `d − 1` kolejnych tur właściciela (`ActiveEvent`, `MatchEngine.startTurn`).

**Współczynnik:** pierwsza tura liczy się w całości, każda kolejna to 0,75 poprzedniej (`DURATION_DECAY`):

| `duration` | 1 | 2 | 3 | 4 | 5 |
|---|---|---|---|---|---|
| współczynnik | 1,00 | 1,75 | 2,31 | 2,73 | 3,05 |

**Założenie własne** (decyzja autora; brak źródła). Dyskonto odpowiada temu, że postacie giną, a stół się zmienia, więc późniejsze tury efektu są mniej pewne (przegląd, Wnioski pkt 6). Do weryfikacji symulacją.

### Przykład

`dev-legacy-refactor` (koszt 8, `duration` 2): `DAMAGE 2 ENEMY_BOARD` + `ATTACK −2 ENEMY_BOARD`.
- obrażenia: 2 × 0,8 × 2,2 = 3,52;
- modyfikator: 2 × 0,571 × 2,2 × 1,75 = 4,40;
- wartość: −0,5 + 3,52 + 4,40 = **7,42**, odchylenie −0,58 (poza tolerancją).

---

## Tolerancja

**`TOLERANCE = 0,5`**: karta przechodzi, gdy `|wartość − koszt| ≤ 0,5`.

- Koszty są całkowite (MTG, HS, Lorcana), a wzór daje ułamki. Tolerancja ±0,5 znaczy po prostu: **koszt = wartość zaokrąglona**. Dla każdej wartości 0,5–10,5 istnieje więc poprawny koszt.
- Hashe: regresja kosztów Lorcany trafia 87% kart w ±½ kosztu, więc ostrzejsza tolerancja nie ma podstaw w danych.
- Dodatnie odchylenie znaczy, że karta jest **silniejsza**, niż kosztuje; ujemne, że **słabsza**.

Zmiana karty w fazie 6 (strojenie balansu) musi zostać w tolerancji. Jeśli symulacja wymaga wyjścia poza nią, zmieniamy cenę w tym dokumencie (z uzasadnieniem), a nie dopisujemy wyjątek.

---

## Karty zdominowane

Karta `a` **dominuje** kartę `b` (`CardPricing.dominates(a, b)`), gdy wszystkie warunki są spełnione:
1. ten sam typ (postać albo wydarzenie);
2. koszt `a` ≤ koszt `b`;
3. postać: atak i zdrowie `a` ≥ `b`; jeśli `b` ma zdolność, `a` ma to samo słowo kluczowe z kwotą ≥;
4. wydarzenie: `duration` `a` ≥ `b`; każdy efekt `b` ma w `a` odpowiednik tego samego rodzaju, zasięgu i znaku kwoty, o kwocie nie gorszej dla właściciela (np. `ATTACK −3` na `ENEMY_BOARD` jest lepsze niż `−2`, a `ATTACK +1` na `ENEMY_BOARD` lepsze niż `+2`);
5. co najmniej jedna z tych nierówności jest ostra albo `a` ma dodatkową zdolność lub efekt.

Karta zdominowana nie ma sensu w żadnej talii, także w przyszłym kreatorze talii (research, Follow-up (2)). Karty identyczne mechanicznie (na koniec fazy 3 `tst-manual` i `neu-scrum-master`: 3, 2/4, GUARD; rozdzielone w fazie 4) nie są zdominowane, bo żadna nierówność nie jest ostra.

Dominacja nie zależy od cen, tylko od samych liczb. Karta może być w tolerancji i jednocześnie zdominowana (np. `neu-devops` na koniec fazy 3).

---

## Wyjątki

Lista wyjątków jest w `CardPricingTests`: `TOLERANCE_EXCEPTIONS` (karta poza tolerancją) i `DOMINANCE_EXCEPTIONS` (karta zdominowana; klucz to id karty słabszej). Zasady:
- każdy wyjątek to id karty i uzasadnienie; test sprawdza, że id istnieje w katalogu, a uzasadnienie nie jest puste;
- karta spoza tolerancji albo zdominowana, której nie ma na liście, wywala build z komunikatem: id, wartość, koszt, odchylenie;
- wyjątek jest dopuszczalny tylko jako świadoma decyzja autora (np. karta-żart celowo słaba) z uzasadnieniem. Docelowo lista jest pusta;
- na koniec fazy 3 lista zawiera wszystkie obecne odchylenia z dopiskiem „do przeliczenia w fazie 4”. Faza 4 przelicza karty i opróżnia listę.

### Stan katalogu na koniec fazy 3

Wartości policzone przez `CardPricing.value` dla wszystkich 31 kart z `factions.json` (w tym `neu-daily-standup`, która nie trafia do talii):

| Karta | Typ | Koszt | Wartość | Odchylenie | Status |
|---|---|---|---|---|---|
| `dev-junior` | postać | 1 | 0,83 | −0,17 | OK |
| `dev-stackoverflow` | wydarzenie | 2 | 2,50 | +0,50 | OK (granica) |
| `dev-fullstack` | postać | 3 | 2,50 | −0,50 | OK (granica) |
| `dev-works-on-my-machine` | wydarzenie | 3 | 0,40 | −2,60 | wyjątek: tolerancja |
| `dev-friday-hotfix` | wydarzenie | 4 | 3,90 | −0,10 | OK |
| `dev-backend` | postać | 5 | 4,86 | −0,14 | OK |
| `dev-force-push` | wydarzenie | 6 | 4,30 | −1,70 | wyjątek: tolerancja |
| `dev-senior` | postać | 7 | 6,83 | −0,17 | OK |
| `dev-legacy-refactor` | wydarzenie | 8 | 7,42 | −0,58 | wyjątek: tolerancja |
| `dev-tech-lead` | postać | 10 | 10,10 | +0,10 | wyjątek: zdominowana przez `neu-client` |
| `tst-intern` | postać | 1 | 1,58 | +0,58 | wyjątek: tolerancja |
| `tst-vague-bug-report` | wydarzenie | 2 | 0,80 | −1,20 | wyjątek: tolerancja |
| `tst-manual` | postać | 3 | 2,86 | −0,14 | OK |
| `tst-edge-case` | wydarzenie | 3 | 1,90 | −1,10 | wyjątek: tolerancja |
| `tst-regression` | wydarzenie | 4 | 2,80 | −1,20 | wyjątek: tolerancja |
| `tst-automation` | postać | 5 | 5,23 | +0,23 | OK |
| `tst-flaky-test` | wydarzenie | 6 | 4,13 | −1,88 | wyjątek: tolerancja |
| `tst-performance` | postać | 7 | 7,59 | +0,59 | wyjątek: tolerancja |
| `tst-production-bug` | wydarzenie | 8 | 6,73 | −1,27 | wyjątek: tolerancja |
| `tst-qa-lead` | postać | 10 | 8,29 | −1,71 | wyjątek: tolerancja |
| `neu-coffee` | wydarzenie | 1 | 0,50 | −0,50 | OK (granica) |
| `neu-daily-standup` | wydarzenie | 2 | 1,95 | −0,05 | OK |
| `neu-rubber-duck` | postać | 2 | 1,96 | −0,04 | OK |
| `neu-scrum-master` | postać | 3 | 2,86 | −0,14 | OK |
| `neu-sprint-planning` | wydarzenie | 4 | 2,73 | −1,27 | wyjątek: tolerancja |
| `neu-devops` | postać | 5 | 4,80 | −0,20 | wyjątek: zdominowana przez `tst-automation` |
| `neu-project-manager` | postać | 6 | 5,88 | −0,12 | OK |
| `neu-meeting` | wydarzenie | 7 | 3,46 | −3,54 | wyjątek: tolerancja |
| `neu-deadline` | wydarzenie | 9 | 3,40 | −5,60 | wyjątek: tolerancja |
| `neu-requirements-change` | wydarzenie | 5 | 5,83 | +0,83 | wyjątek: tolerancja |
| `neu-client` | postać | 10 | 11,18 | +1,18 | wyjątek: tolerancja |

Podsumowanie: 16 kart poza tolerancją — 12 za słabych na swój koszt (odchylenie ujemne) i 4 za silnych (`tst-intern`, `tst-performance`, `neu-requirements-change`, `neu-client`) — oraz 2 karty zdominowane (`neu-devops` przez `tst-automation`, `dev-tech-lead` przez `neu-client`). Postacie w większości mieszczą się w regule. Prawie wszystkie wydarzenia są dziś za drogie, najbardziej obrażenia tylko w gracza (`neu-deadline`), obrażenia na obie plansze (`neu-meeting`) i leczenie (`dev-works-on-my-machine`).

### Stan katalogu po fazie 4

Faza 4 przeliczyła karty według reguły. Listy `TOLERANCE_EXCEPTIONS` i `DOMINANCE_EXCEPTIONS` w `CardPricingTests` są puste: każda z 31 kart mieści się w tolerancji i żadna nie jest zdominowana. Id, nazwy, żarty PL+EN, frakcje, kopie i rozmiar talii (30, w tym 15 postaci i 15 wydarzeń) bez zmian.

Zasady wyboru poprawki:
- wydarzenie za drogie: zwykle niższy koszt, a gdy to psuje krzywą kosztów, wyższa kwota albo dłuższy `duration`;
- karta za silna: niższa statystyka albo niższa kwota zdolności;
- wybór zgodny z żartem karty (np. „U mnie działa” zostaje słabym leczeniem, „Nieprzesuwalny deadline” jest „jednak przesunięty, na wcześniej”, więc tańszy);
- Developerzy wyraźnie wygrywają w symulacji (faza 2), więc przy wyborze w granicach tolerancji karty Testerów dostały wartość bliżej górnej granicy (np. `tst-manual` 2/5), a karty Developerów bliżej dolnej (np. `dev-force-push` na −0,50). To nie jest strojenie balansu (faza 6), tylko wybór między poprawnymi wariantami.

| Karta | Typ | Koszt | Wartość | Odchylenie | Zmiana w fazie 4 |
|---|---|---|---|---|---|
| `dev-junior` | postać | 1 | 0,83 | −0,17 | — |
| `dev-stackoverflow` | wydarzenie | 2 | 2,50 | +0,50 | — |
| `dev-fullstack` | postać | 3 | 2,50 | −0,50 | — |
| `dev-works-on-my-machine` | wydarzenie | 1 | 0,70 | −0,30 | koszt 3 → 1, HEAL 3 → 4 |
| `dev-friday-hotfix` | wydarzenie | 4 | 3,90 | −0,10 | — |
| `dev-backend` | postać | 5 | 4,86 | −0,14 | — |
| `dev-force-push` | wydarzenie | 6 | 5,50 | −0,50 | DAMAGE 5 → 6 |
| `dev-senior` | postać | 7 | 6,83 | −0,17 | — |
| `dev-legacy-refactor` | wydarzenie | 7 | 7,42 | +0,42 | koszt 8 → 7 |
| `dev-tech-lead` | postać | 10 | 10,10 | +0,10 | — (dominacja usunięta zmianą `neu-client`) |
| `tst-intern` | postać | 1 | 1,15 | +0,15 | 1/2 → 1/1 |
| `tst-vague-bug-report` | wydarzenie | 2 | 2,10 | +0,10 | PLAYER_DAMAGE 2 → 4 |
| `tst-manual` | postać | 3 | 3,29 | +0,29 | 2/4 → 2/5 |
| `tst-edge-case` | wydarzenie | 3 | 3,10 | +0,10 | DAMAGE 3 → 4 |
| `tst-regression` | wydarzenie | 4 | 3,86 | −0,14 | `duration` 2 → 3 |
| `tst-automation` | postać | 5 | 5,23 | +0,23 | — |
| `tst-flaky-test` | wydarzenie | 4 | 4,12 | +0,12 | koszt 6 → 4 |
| `tst-performance` | postać | 7 | 7,16 | +0,16 | 7/5 → 7/4 |
| `tst-production-bug` | wydarzenie | 7 | 6,73 | −0,27 | koszt 8 → 7 |
| `tst-qa-lead` | postać | 10 | 10,00 | 0,00 | 7/10 → 10/10 |
| `neu-coffee` | wydarzenie | 1 | 0,50 | −0,50 | — |
| `neu-daily-standup` | wydarzenie | 2 | 1,95 | −0,05 | — |
| `neu-rubber-duck` | postać | 2 | 1,96 | −0,04 | — |
| `neu-scrum-master` | postać | 3 | 3,14 | +0,14 | 2/4 → 1/6 (rozdzielona z `tst-manual`) |
| `neu-sprint-planning` | wydarzenie | 3 | 2,73 | −0,27 | koszt 4 → 3 |
| `neu-devops` | postać | 5 | 5,37 | +0,37 | 4/4 → 5/4 (koniec dominacji `tst-automation`) |
| `neu-project-manager` | postać | 6 | 5,88 | −0,12 | — |
| `neu-meeting` | wydarzenie | 5 | 4,78 | −0,22 | koszt 7 → 5, DAMAGE 4 → 5 |
| `neu-deadline` | wydarzenie | 3 | 3,40 | +0,40 | koszt 9 → 3 |
| `neu-requirements-change` | wydarzenie | 6 | 5,83 | −0,17 | koszt 5 → 6 |
| `neu-client` | postać | 10 | 10,32 | +0,32 | 8/9 → 8/7 (koniec dominacji nad `dev-tech-lead`) |

**Krzywa kosztów talii** (liczba kart o danym koszcie, z kopiami):

| Talia | 1 | 2 | 3 | 4 | 5 | 6 | 7 | 8 | 9 | 10 | Średnia |
|---|---|---|---|---|---|---|---|---|---|---|---|
| developers, faza 3 | 3 | 3 | 5 | 3 | 4 | 3 | 3 | 2 | 1 | 3 | 5,00 |
| developers, faza 4 | 5 | 3 | 5 | 2 | 4 | 4 | 4 | 0 | 0 | 3 | 4,53 |
| testers, faza 3 | 3 | 3 | 5 | 3 | 4 | 3 | 3 | 2 | 1 | 3 | 5,00 |
| testers, faza 4 | 3 | 3 | 7 | 4 | 4 | 2 | 4 | 0 | 0 | 3 | 4,53 |

Krzywe obu talii nie są już identyczne. Obie straciły karty za 8–9, bo wydarzenia za 8–9 były według reguły za słabe na swój koszt. Najdroższe karty to nadal postacie za 10 (`dev-tech-lead`, `tst-qa-lead`, `neu-client`).

**Symulacja po fazie 4** (`BalanceSimulationTests`, N = 1000, te same ziarna, wagi AI bez zmian):

| Frakcja PLAYER | Zaczyna PLAYER | Zaczyna OPPONENT |
|---|---|---|
| developers | 72,4% (faza 2: 80,0%) | 43,6% (faza 2: 45,6%) |
| testers | 54,4% (faza 2: 52,4%) | 25,6% (faza 2: 21,6%) |

- win rate Developerów: **59,0%** (faza 2: 62,9%);
- win rate strony zaczynającej: **64,4%** (faza 2: 66,3%);
- p90 szacowanego czasu partii: **264 s** (faza 2: 258 s);
- liczba `FAVORABLE_TRADE`: **55** (faza 2: 85).

Przewaga Developerów i strony zaczynającej zmalała, ale nadal jest duża. Dalsze strojenie to fazy 5 (wagi AI) i 6 (strojenie kart w tolerancji, ewentualnie karta-moneta).

---

## Wagi AI (faza 5)

`PositionEvaluator` ocenia postacie tym samym modelem co ta reguła. Przelicznik to `HP_PER_COST_POINT = ENEMY_HP_WEIGHT / PLAYER_DAMAGE_PRICE`, czyli punkt kosztu wyrażony w punktach HP wroga. Z niego wynikają `ATTACK_WEIGHT`, `HEALTH_WEIGHT`, `GUARD_VALUE` i `OVERTIME_VALUE` (ceny z sekcji Postacie × przelicznik).

Plan fazy 5 zakładał samo przestrojenie istniejących stałych. Żeby korzystna wymiana na postać (`FAVORABLE_TRADE`) mogła wygrać z atakiem w gracza, potrzebne były też zmiany w strukturze oceny (`Score` bez zmian):

- **`DAMAGE_DISCOUNT` = 0,1** zmienia znaczenie. Wcześniej było to 0,5 odejmowane od zdrowia. Teraz to udział wartości, który uszkodzona postać traci proporcjonalnie do `damage / health`. Obrażenia znikają na koniec tury, więc takie, które nie prowadzą do pokonania postaci, są prawie bezwartościowe. Wartość jest trochę powyżej zera, żeby z dwóch równych ruchów wygrywał ten z mniejszym kontratakiem.
- **`SETUP_DAMAGE_SHARE` = 1,0** (nowa stała) to pełny udział dla uszkodzonej postaci strony nieaktywnej, którą strona aktywna może jeszcze dobić w tej turze. Taki cios przygotowuje dobicie, więc liczy się w całości.
- **`finishable()`** (nowa heurystyka) wybiera te postacie. Pojemność dobicia to suma ataku postaci strony aktywnej, które mogą jeszcze zaatakować (bez ataku w tej turze, bez Trap, wystawione wcześniej albo z Rush). Cele są brane zachłannie według wartości przygotowania, z uwzględnieniem Guard. Karty w ręce się nie liczą (ręka jest ukryta). To przybliżenie „uderz, potem dobij drugą postacią” w ocenie na jeden ruch do przodu.
- `characterValue(stats, damage, damageShare, extraGuard)` przyjmuje udział obrażeń jako parametr.

Skutek: `FAVORABLE_TRADE` występuje w symulacji (ok. 900 na 1000 partii), a test `ComputerOpponentTests` z 20 ziarnami sprawdza wybór korzystnej wymiany. Każda zmiana tych stałych albo heurystyki wymaga pełnego pomiaru (sekcja niżej).

## Pomiar balansu

Reguła wyceny daje ocenę z góry. O tym, czy przekłada się na balans, rozstrzyga symulacja komputer kontra komputer (`BalanceSimulationTests`, ślad w artefakcie CI `balance-report`).

**Bramki (włączone w fazie 6, `BalanceSimulationTests`):**
- win rate frakcji PLAYER w każdej z 4 komórek (frakcja × strona zaczynająca) w **[40%, 60%]** — twarda bramka (`CELL_MIN_WIN_RATE = 0.40`, `CELL_MAX_WIN_RATE = 0.60`);
- **45–55%** to cel orientacyjny, nie warunek zaliczenia (decyzja autora do pkt 11): przy 250 partiach na komórkę 95% przedział ufności to ok. ±6 p.p. (1,96·√(0,25/250)), czyli jest szerszy niż cel; kryterium 6.2 planu czytamy zgodnie z tą decyzją;
- p90 szacowanego czasu partii ≤ **900 s** (15 min, `MAX_P90_MATCH_SECONDS`);
- szacowany czas partii = tury „człowieka” × **`ASSUMED_HUMAN_TURN_SECONDS = 20`** + ruchy komputera × 0,7 s (odtwarzanie w SPA); liczony z perspektywy obu stron, brana jest większa wartość;
- talia testowa aggro (karty za 1–3) pozostaje wyłącznie raportem, bez bramki;
- pliki śladu (`target/balance/matches.csv`, `cards.csv`, `summary.md`) powstają przed asercjami, więc są dostępne także przy porażce. Komunikat porażki podaje komórkę, win rate, przedział i ścieżkę `target/balance/summary.md`.

**Stan wyjściowy (faza 2, przed jakąkolwiek zmianą kart i wag AI):**

| Frakcja PLAYER | Zaczyna PLAYER | Zaczyna OPPONENT |
|---|---|---|
| developers | 80,0% | 45,6% |
| testers | 52,4% | 21,6% |

- win rate strony zaczynającej: **66,3%** (MTG 52–55%, Lorcana 53–54%, HS po The Coin ok. 52% — przegląd źródeł);
- p90 szacowanego czasu partii: **258 s**;
- N = **1000** partii (250 na komórkę), czas działania ok. **1,2 s**, więc zmniejszanie N (plan, Critical Implementation Details) nie było potrzebne.

Wnioski ze stanu wyjściowego: Developerzy wyraźnie wygrywają z Testerami, a przewaga strony zaczynającej jest dużo większa niż w źródłach. Obie rzeczy mają zmniejszyć faza 4 (karty według tej reguły) i faza 6 (strojenie, ewentualnie karta-moneta).

**Co sprawdzać w symulacji w pierwszej kolejności** (ceny bez źródła albo z ryzykiem złego przeniesienia):
1. obrażenia postaci, które nie zabijają (znikają na koniec tury) — czy `DAMAGE` nie jest przeszacowane;
2. `OVERTIME`, `TRAP`, `BUDGET_GAIN`, `BUDGET_MAX`, czas trwania i rabat `ALL_BOARDS`;
3. metryki per karta z `cards.csv` (odsetek wygranych w partiach, w których karta została zagrana, jak WRP u Silvy i in.).

### Faza 6: strojenie i karta dla drugiego gracza

Pomiar fazy 6 robimy po ostatniej zmianie wag AI (faza 5). Wagi `PositionEvaluator` i algorytm `ComputerOpponent` w tej fazie się nie zmieniają. Wszystkie pomiary: N = 1000, te same ziarna 0–999. Komórka to win rate frakcji PLAYER.

**Krok 1: strojenie kart w tolerancji.** Po fazie 5 Developerzy wygrywali 54,6%, więc karty Developerów zeszły bliżej dolnej granicy tolerancji:

| Karta | Zmiana | Wartość | Odchylenie |
|---|---|---|---|
| `dev-stackoverflow` | koszt 2 → 3 | 2,50 | −0,50 (granica) |
| `dev-tech-lead` | 8/8 → 7/8 (`ON_PLAY_DAMAGE 4` bez zmian) | 9,53 | −0,47 |

`dev-tech-lead` 7/8 nie jest zdominowany przez `neu-client` (8/7, `ON_PLAY_DAMAGE 5`), bo ma więcej zdrowia. `CardPricingTests` przechodzi bez wyjątków.

Win rate Developerów spadł do 51,9%, ale przewaga strony zaczynającej została (66,3%). Wszystkie 4 komórki są poza celem 45–55% w tę samą stronę, na korzyść strony zaczynającej, a 3 z nich także poza bramką 40–60%. Kartami w granicach tolerancji nie da się tego naprawić, bo tempo pierwszego ruchu działa na obie talie tak samo. Warunek wprowadzenia karty dla drugiego gracza (plan, faza 6, pkt 2) jest więc spełniony.

**Krok 2: karta dla drugiego gracza.** `factions.json` ma pole najwyższego poziomu `secondPlayerBonus` (id karty neutralnej, sprawdzane przez `CardCatalog`). `MatchFactory` dodaje egzemplarz tej karty do ręki strony niezaczynającej, spoza talii: ręka ma 6 kart, talia 25, a `HAND_LIMIT` (10) się nie zmienia. Karta dostaje `instanceId` po obu taliach (61).

Plan zakładał monetę `neu-coffee` (`BUDGET_GAIN 2` za 1, jak The Coin w HS). W symulacji ta karta **nic nie zmienia**, bo komputer nigdy jej nie zagrywa: ma 0 zagrań w `cards.csv`, a powód `GAIN_BUDGET` występuje 0 razy. Komputer patrzy tylko jeden ruch do przodu, a w `PositionEvaluator` zagranie kawy daje +1 netto bieżącego budżetu × `BUDGET_CURRENT_WEIGHT` (0,25) − 1 karta w ręce × `HAND_CARD_VALUE` (1,0) = **−0,75**. `ComputerOpponent` odrzuca więc ten ruch jako niekorzystny. Zmiana wag jest poza zakresem fazy 6, bo unieważniłaby pomiar. Dlatego porównaliśmy kilka kart neutralnych jako `secondPlayerBonus` (karty po kroku 1):

| `secondPlayerBonus` | dev / zaczyna PLAYER | tst / zaczyna PLAYER | dev / zaczyna OPPONENT | tst / zaczyna OPPONENT | Strona zaczynająca | Developerzy | p90 czasu |
|---|---|---|---|---|---|---|---|
| brak | 67,6% | 63,2% | 34,4% | 31,2% | 66,3% | 51,9% | 272 s |
| `neu-coffee` | 67,6% | 62,0% | 34,0% | 31,2% | 66,1% | 52,1% | 270 s |
| `neu-daily-standup` | 59,2% | 54,4% | 40,4% | 36,4% | 59,2% | 52,2% | 266 s |
| `neu-deadline` | 63,6% | 53,6% | 39,6% | 36,4% | 60,3% | 53,3% | 262 s |
| **`neu-rubber-duck`** | **55,2%** | **50,8%** | **46,4%** | **42,0%** | **54,4%** | **52,2%** | **269 s** |
| `neu-scrum-master` | 48,0% | 44,0% | 56,8% | 51,6% | 45,9% | 52,3% | 283 s |

**Decyzja autora:** `secondPlayerBonus = "neu-rubber-duck"` (postać za 2, 1/3, `HEAL 2`, wartość 1,96).
- To jedyny wariant, w którym wszystkie komórki mieszczą się w bramce 40–60%, a przewaga strony zaczynającej jest najbliżej źródeł (54,4% wobec 52–55% w MTG, Lorcanie i HS po The Coin).
- `neu-scrum-master` przesadza w drugą stronę: druga strona wygrywa 54,1%.
- `neu-coffee` zostaje w taliach jako zwykła karta. Jako karta dla drugiego gracza ma sens dopiero wtedy, gdy komputer nauczy się wydawać dodatkowy budżet (zmiana wag albo głębsze przeszukiwanie, poza S-06).

**Krok 3: krzywa i walka o stół (uwagi autora po partii ręcznej).** Autor grał Developerami i miał wrażenie, że Testerzy są silniejsi przez lepszą krzywą kosztów, a nie przez mocniejsze karty. Porównanie talii to potwierdziło:
- po kroku 1 Developerzy mieli tylko 1 kartę za 2 (neutralną `neu-rubber-duck`) i 2 karty za 4. Przeniesienie `dev-stackoverflow` na koszt 3 w kroku 1 zabrało im obie karty za 2;
- tanie wydarzenia Developerów (leczenie, dobieranie, wzmocnienie ataku) pomagają tylko wtedy, gdy stół jest już wygrany. Pierwsze usunięcie postaci mieli dopiero za 6 (`dev-force-push`). Testerzy mają `tst-edge-case` (4 obrażenia za 3) i `tst-flaky-test` (pułapka za 4).

Komputer słabo to odczuwa (Developerzy nadal wygrywali 52,2%), ale gracz za stołem tak. Zmiany po decyzji autora:

| Karta | Zmiana | Wartość | Odchylenie |
|---|---|---|---|
| `dev-fullstack` | 3/3 za 3 → 2/3 za 2 | 1,93 | −0,07 |
| `dev-force-push` | `DAMAGE 6` za 6 → `DAMAGE 3` za 2 | 1,90 | −0,10 |

Po samej zmianie `dev-fullstack` komórki wynosiły 54,8 / 53,2 / 47,2 / 40,4% (kolejność jak w tabeli wariantów), czyli komórka testers / zaczyna OPPONENT leżała tuż przy bramce. Tanie usunięcie (`dev-force-push`) przesunęło balans w stronę Testerów i odsunęło tę komórkę od granicy.

**Krzywa kosztów talii po fazie 6** (liczba kart o danym koszcie, z kopiami):

| Talia | 1 | 2 | 3 | 4 | 5 | 6 | 7 | 8 | 9 | 10 | Średnia |
|---|---|---|---|---|---|---|---|---|---|---|---|
| developers | 5 | 5 | 5 | 2 | 4 | 2 | 4 | 0 | 0 | 3 | 4,27 |
| testers | 3 | 3 | 7 | 4 | 4 | 2 | 4 | 0 | 0 | 3 | 4,53 |

**Pomiar ostateczny** (`neu-rubber-duck` jako `secondPlayerBonus`, karty po krokach 1 i 3, wagi AI z fazy 5):

| Frakcja PLAYER | Zaczyna PLAYER | Zaczyna OPPONENT |
|---|---|---|
| developers | 54,8% (95% CI 48,6–60,9%) | 42,4% (36,4–48,6%) |
| testers | 54,4% (48,2–60,5%) | 47,6% (41,5–53,8%) |

- win rate strony zaczynającej: **54,8%** (po fazie 5: 65,0%);
- win rate Developerów: **48,8%** (po fazie 5: 54,6%);
- p90 szacowanego czasu partii: **284 s** (4,7 min; mediana 220 s, maksimum 400 s), dużo poniżej 900 s;
- N = **1000** partii (250 na komórkę) + 200 partii talii aggro; czas działania ok. **1,4 s** (razem z aggro ok. 1,7 s).

Komórka developers / zaczyna OPPONENT (42,4%) nie mieści się w celu orientacyjnym 45–55%, ale przechodzi twardą bramkę 40–60%. Zgodnie z decyzją autora zapisaną wyżej to wystarcza: przedział ufności tej komórki (36,4–48,6%) obejmuje cel. Pozostałe trzy komórki mieszczą się w celu.

**Przy każdej zmianie kart** (wartości, talii albo `secondPlayerBonus`) i wag AI trzeba sprawdzić artefakt `balance-report` z CI (`summary.md`: komórki, strona zaczynająca, p90 czasu; `cards.csv`: metryki per karta). Bramka 40–60% wychwyci tylko duże zmiany, a dryf w stronę granic widać dopiero w raporcie.
