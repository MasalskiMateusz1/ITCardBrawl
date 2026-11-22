# Przegląd źródeł wyceny kart (S-06, faza 1)

**Data:** 2026-11-21
**Cel:** zebrać udokumentowane praktyki wyceny kart z Magic: The Gathering, Hearthstone i Disney Lorcana oraz z literatury naukowej. Na tej podstawie powstanie reguła wyceny w `context/foundation/card-pricing.md` (faza 3), przeliczenie kart (faza 4) i wagi AI (faza 5).
**Metoda:** wyszukiwanie Exa (`web_search_exa`) i czytanie stron źródłowych (`web_fetch_exa`). Pełna lista zapytań jest w sekcji „Log wyszukiwania” na końcu.

**Oznaczenia źródeł:**
- **[wydawca]** — wydawca albo autorzy gry: artykuły projektantów WotC, oficjalne zasady, wypowiedzi projektantów Blizzarda i Ravensburgera (także cytowane w prasie).
- **[społeczność]** — strony graczy, blogi, fora, analizy danych społeczności.
- **[recenzowana]** — praca opublikowana w recenzowanym czasopiśmie albo w materiałach konferencji.
- **[preprint]** — arXiv albo inny tekst bez potwierdzonej recenzji.

Liczby w tym dokumencie pochodzą ze wskazanych stron. Wnioski dla ITCardBrawl są oznaczone osobno jako **wniosek** albo **założenie własne**.

---

## Magic: The Gathering

### Statystyki względem kosztu many („vanilla test”)

- **[wydawca]** Mark Rosewater, „Let's Start at the Very Beginning” (2003): „vanilla” to w żargonie R&D stwór bez tekstu zasad, mający tylko siłę (power) i wytrzymałość (toughness). Takie karty z założenia mają poziom średni lub słaby. Przykład: 2/2 za 1 manę byłoby zbyt silne, a 2/2 za 2 (Grizzly Bears) jest za słabe na turnieje. Koszty są całkowite, więc nie da się wycenić karty „pomiędzy”. https://magic.wizards.com/en/news/making-magic/lets-start-very-beginning-2003-07-14
- **[społeczność]** MagicProfessor, „Evaluating Creatures in MTG Limited” (2024): stwór bez zdolności jest „dobry”, gdy siła + wytrzymałość ≈ 2 × koszt. Na skrajach reguła się zmienia: przy koszcie 1–2 i 6–7 stwór „jest winien” punkt więcej niż podwojony koszt, a powyżej 7 suma prawie przestaje rosnąć. Każda zwykła zdolność (first strike, vigilance, trample…) dodaje ok. ½ many, a dobre unikanie blokady (flying) pełną manę. Stwór 1/1 za 1 jest niegrywalny, bo rzadko wymienia się za kartę przeciwnika. Duży stwór zwykle daje przewagę kart, bo przeciwnik musi zużyć na niego więcej niż jedną kartę. Niezrównoważony rozkład (1/6, 6/1) jest sytuacyjny. https://magicprofessor.blog/card-evaluation/creatures/
- **[społeczność, analiza danych]** Magic Data Science, „The Empirical Vanilla Test in MTG” (TCGplayer, 2022/2025): regresja liniowa na wszystkich stworach „French vanilla”, czyli takich, które mają tylko słowa kluczowe. Wyniki:
  - +1 siły ≈ **0,78** MV (mana value);
  - +1 wytrzymałości ≈ **0,53** MV;
  - flying ≈ **0,75** MV;
  - first strike ≈ **0,6**, double strike prawie **1,5**;
  - defender ma wartość ujemną;
  - wyraz wolny 0,59 MV, który autor interpretuje jako „ile warta jest sama karta”;
  - wyższa rzadkość = taniej za te same cechy;
  - koszty spadają o ok. 0,023 MV rocznie (power creep).

  Autor zastrzega, że R&D jest ograniczone do całkowitych kosztów i nie zawsze jest spójne. https://www.tcgplayer.com/content/article/The-Empirical-Vanilla-Test-in-MTG/340a3f58-9926-43d7-a1f2-d613adbd9cfc/

### Wycena czarów: obrażenia, dobieranie, leczenie

- **[społeczność]** bravelion83, „The price is right” (MTG Salvation, 2015). Zestawienie kosztów z kart wydrukowanych przez WotC dla instantów „target creature or player”:
  - 2 obrażenia ≈ 1 mana;
  - 3 obrażenia ≈ 2 many (Lightning Bolt za 1 to wyjątek);
  - 4 obrażenia ≈ 4 many;
  - 5 obrażeń ≈ 6 many.

  Obrażenia tylko w gracza (Lava Axe) albo tylko w stwora to „drawback”, który obniża koszt. Możliwość trafienia i stwora, i gracza to „upside”. Dobieranie:
  - 1 karta ≈ 1 mana (instant);
  - 2 karty ≈ 3 many (sorcery);
  - 3 karty ≈ 4 many;
  - 4 karty ≈ 5 many.

  Inne obserwacje autora: różnica między N a N+1 many rośnie wraz z N. Suma dwóch efektów nie zawsze kosztuje sumę cen, a przy wysokim koszcie bywa tańsza. https://www.mtgsalvation.com/userblogs/the-lions-lair/42313-4-the-price-is-right-a-custom-card-design-guide
- **[wydawca]** Gavin Verhey, „The Meaning of Life” (2017): samo leczenie (life gain) „pomaga nie przegrać, ale nie pomaga wygrać”. Zużywa kartę i nie wpływa na planszę. Lepszy jest nawet stwór 1/1, który zablokuje tyle samo obrażeń. Leczenie ma sens jako dodatek do innego efektu. https://magic.wizards.com/en/news/feature/meaning-life-2017-03-09
- **[wydawca]** Mark Rosewater, „Psychological Reactions” (2025): początkujący przeceniają leczenie. Przykład: 2 many za 5 życia przegrywa z 2 manami za latającego 2/1, bo stwór „odrabia” to życie w trzech atakach i zostaje na planszy. WotC dodaje więc leczenie jako mały dodatek, np. do wejścia stwora. https://magic.wizards.com/en/news/making-magic/psychological-reactions

### Przewaga kart i tempo

- **[społeczność]** Tom Anderson, „What is Tempo?” (Card Kingdom, 2021): każda talia gra dwoma zasobami, kartami i maną. Przewaga kart to wydanie kart wydajniej niż przeciwnik, np. 1 karta zabija 2 albo 1 karta dobiera 2. Tempo to przewaga z wydawania many wydajniej. Trudno zyskać na obu naraz. https://blog.cardkingdom.com/what-is-tempo/
- **[społeczność]** Mike Flores, „Eight Core Principles of »Who's the Beatdown?«” (Star City Games, 2012): rola agresora albo kontroli zależy od pojedynku. Grając drugi, ma się „naturalną przewagę kart”, czyli darmową kartę. https://articles.starcitygames.com/articles/eight-core-principles-of-whos-the-beatdown/

### Przewaga pierwszego gracza i kompensata

- **[wydawca]** Magic: The Gathering Comprehensive Rules (7 sierpnia 2026), zasada 103.8a: w grze dwuosobowej gracz rozpoczynający pomija krok dobierania w pierwszej turze. Ręka startowa to normalnie 7 kart (103.5). https://media.wizards.com/2026/downloads/MagicCompRules%2020260807.pdf
- **[wydawca]** Reid Duke, „Play or Draw?” (WotC, 2015): grając pierwszy, ma się „znacznie więcej niż 50/50” przy równych warunkach. Przewaga tempa jest zwykle warta pominiętego dobrania. Gra częściej kończy się w turze zaczynającego, a przewaga rośnie, gdy partie są krótkie i szybkie. https://magic.wizards.com/en/news/feature/play-or-draw-2015-03-16
- **[społeczność, dane]** 17Lands, „Format Speed”: win rate zaczynającego w formatach draftowych MTG Arena wynosi od **51,9%** do **55,3%**, a średnio partia trwa 8,4–9,9 tury. Krótsze formaty dają większą przewagę zaczynającemu. https://www.17lands.com/format_speed
- **[społeczność, dane]** Portent Lab (2026): analiza 1,2 mln partii z 17Lands. Po korekcie na umiejętności przewaga zaczynającego wynosi **5–9,4 p.p.** W partiach do tury 7 zaczynający wygrywa 65–72%. W partiach trwających 10 tur lub dłużej lekko wygrywa grający drugi, bo dodatkowa karta „zdążyła się zwrócić”. https://portentlab.com/articles/mtg-draft-tips-from-data

**Podsumowanie MTG:**
- Wycena jest liniowa w statystykach.
- Atak jest droższy od zdrowia (0,78 vs 0,53 MV).
- Słowa kluczowe kosztują ok. 0,5–1 many.
- Sama karta ma wartość bazową ok. 0,6 MV.
- Krzywa spłaszcza się przy wysokich kosztach.
- Obrażenia tylko w gracza są tańsze niż obrażenia w dowolny cel.
- Leczenie jest słabe.
- Rekompensata „bez dobrania” nie wyrównuje przewagi zaczynającego (52–55%).

---

## Hearthstone

### „Vanilla test”: statystyki na punkt many

- **[społeczność]** Forum Blizzarda, „Card health amounts and why” (2024): reguła wartości minionów bez tekstu to „suma statystyk = 2 × koszt + 1”. Taunt jest wart +1 punkt: Sen'jin Shieldmasta to 3/5 Taunt za 4, a nie 4/5. Zdrowie jest cenione „trochę bardziej” niż atak. Wycenę efektu liczy się, odejmując od kosztu cenę statystyk. https://us.forums.blizzard.com/en/hearthstone/t/card-health-amounts-and-why-wild/129113?page=2
- **[społeczność]** Mr Wong's Hearthstone Blog: ten sam wzór „2n + 1”. Ocena zależy też od tego, czy karta wymienia się korzystnie („trading up”, 2-za-1). Minion z 1 zdrowia ginie od mocy bohatera. Dobieranie kart jest tak cenne, że karty z dobieraniem mają mocno obniżone statystyki: Gnomish Inventor ma 6 statystyk zamiast 9. https://mrwonghs.wordpress.com/category/uncategorized/page/3/
- **[społeczność]** Dot Esports, „Understanding Card Value and Stat Distribution” (2014): prostsza wersja testu to stosunek 2:1 statystyk do many. Rozkład też ma znaczenie: 4/5 jest lepsze od 5/4, bo przeżywa więcej popularnych źródeł obrażeń. https://dotesports.com/hearthstone/news/understanding-card-value-and-stat-distribution-29647

### Regresje kosztu kart (analizy społeczności)

- **[społeczność]** Elie Bursztein, „How to appraise Hearthstone card values” (2014). Założenia modelu:
  1. koszt jest proporcjonalny do siły;
  2. siła rośnie liniowo z kosztem (gdyby rosła wykładniczo, drogie karty miażdżyłyby tanie, a gdyby podliniowo, talie kontroli by przegrywały);
  3. cena efektu jest stała, niezależna od kosztu karty, i mnożona przez liczbę;
  4. każda karta ma wartość wewnętrzną (intercept), bo liczba dobrań jest ograniczona;
  5. koszt to suma atrybutów.

  Charge jest liczony jako mnożnik ataku. Przykład autora: Fireball (6 obrażeń za 4) jest wydajniejszy niż Pyroblast (10 za 10). https://elie.net/blog/hearthstone/how-to-appraise-hearthstone-card-values
- **[społeczność]** Elie Bursztein, „How to find undervalued Hearthstone cards automatically” (2014): metoda najmniejszych kwadratów na 134 kartach z premiery. Ceny w manie za punkt:

  | Efekt | Mana / punkt |
  |---|---|
  | Destroy Minion | 5,33 |
  | Draw Card | 1,84 |
  | Board Damage (obrażenia we wszystkie wrogie cele) | 1,84 |
  | Divine Shield | 1,40 |
  | Windfury | 1,19 |
  | Freeze | 1,02 |
  | Silence | 0,83 |
  | Damage (pojedynczy cel) | 0,82 |
  | Attack | 0,57 |
  | Taunt | 0,51 |
  | Health | 0,40 |
  | Heal / Self Hero-Heal | 0,34 |
  | Charge (za punkt ataku) | 0,33 |
  | Opponent Draw Card | −1,98 |
  | Discard | −1,25 |
  | Overload | −0,83 |
  | Self Hero Damage | −0,27 |

  Autor podkreśla, że punkt zdrowia jest o ok. 30% tańszy od punktu ataku. Model wskazał Soulfire jako najbardziej niedoszacowaną kartę, a Blizzard potem podniósł jej koszt. https://elie.net/blog/hearthstone/how-to-find-automatically-hearthstone-undervalued-cards
- **[społeczność]** Gregory Block, „Hearthstone: Mana cost” (CTOForADay, 2014): regresja na minionach bez mechanik daje atak **0,504**, zdrowie **0,439** i wyraz wolny −0,17, przy R² = 0,93. Na wszystkich minionach daje 0,549 i 0,530 przy R² = 0,76. Same statystyki wyjaśniają więc większość kosztu. http://ctoforaday.com/blog/2014/5/10/hearthstone-mana-cost

### Słowa kluczowe: Taunt, Charge, Rush

- **[wydawca, wywiad w prasie]** Ben Brode dla PC Gamer (2015): Charge jest „bardziej niepokojące” przy projektowaniu niż inne silne efekty, bo pozwala zabić przeciwnika w jednej turze, zanim ten zobaczy zagrożenie. https://www.pcgamer.com/hearthstones-ben-brode-on-rng-esports-and-the-rise-of-secret-paladin/
- **[wydawca, wywiad w prasie]** Ben Brode dla Ars Technica (2018): Rush (atak tylko w miniony w turze zagrania) wprowadzono jako „gorszą wersję Charge”, żeby uniknąć nieinteraktywnych obrażeń w twarz, które czyniły Charge trudnym do zbalansowania. https://arstechnica.com/gaming/2018/04/see-20-newly-revealed-cards-from-hearthstones-witchwood-expansion/

### Przewaga pierwszego gracza i The Coin

- **[wydawca, cytat z oficjalnego forum]** Ben Brode o The Coin (cytowany przez BlizzPro, 2013):
  - w alfie wygranie rzutu monetą dawało **20%** większą szansę wygranej;
  - Blizzard dał już drugiemu graczowi dodatkową kartę, a celem była przewaga **5–10%**, „jak w szachach”;
  - testowano +5 zdrowia dla drugiego gracza, co przybliżało do celu, ale „granie drugim wciąż było odczuwane jak przegrywanie”, bo zawsze miał mniej many;
  - The Coin pozwala raz odpowiedzieć kartą za 3 na zagranie za 2.

  Wynik po wprowadzeniu: **52,2%** wygranych zaczynającego we wszystkich ligach i **50,4%** w Master League. https://blizzpro.com/2013/09/12/ben-brode-talks-the-coin/
- **[społeczność]** Hearthstone Wiki, „The Coin”: drugi gracz dostaje 4 karty startowe zamiast 3 oraz The Coin, czyli 0 many i +1 kryształ na jedną turę. Testowano też start z 35 zdrowia. https://hearthstone.wiki.gg/wiki/The_Coin
- **[społeczność, prasa]** Ars Technica (2014): w 219 partiach turniejowych zaczynający wygrał 50,23%. Według prezentacji Blizzarda z BlizzConu przewaga zaczynającego wynosi ok. 6% w niskich ligach i 2,6% w Master. https://arstechnica.com/gaming/2014/08/do-you-want-to-go-first-balancing-hearthstone-and-other-turn-based-games/
- **[społeczność, dane]** BlizzPro (2016):
  - w Arenie zaczynający wygrywa 54,44% wobec 45,54%;
  - w Constructed (Vicious Syndicate, ponad 200 tys. partii) 51,65%;
  - talie aggro i midrange mają większą przewagę pierwszego ruchu (+2,51 do +7,26 p.p.), a talie kontroli są prawie wyrównane.

  https://blizzpro.com/2016/07/16/the-coin-a-valid-balancing-mechanic-or-utterly-broken/

**Podsumowanie Hearthstone:**
- Budżet statystyk to 2 × koszt + 1.
- Atak jest nieco droższy od zdrowia (0,50–0,57 vs 0,40–0,44).
- Taunt kosztuje ok. 0,5 many, czyli ok. 1 punkt statystyk.
- Charge rośnie z atakiem i jest uznawany za najtrudniejszy do zbalansowania.
- Dobranie karty kosztuje ok. 1,8 many.
- Obrażenia: ok. 0,8 many/pkt w pojedynczy cel i ok. 1,8 many/pkt we wszystkich wrogów.
- Leczenie jest tanie (0,34/pkt).
- Sama dodatkowa karta nie wystarcza jako kompensata. Dopiero 4. karta + The Coin dały ok. 52%.

---

## Disney Lorcana

### Zasady wpływające na wycenę

- **[wydawca]** Ravensburger, Quick Start Rules:
  - wygrywa pierwszy gracz, który zdobędzie **20 lore** (punktów); lore zdobywa się głównie postaciami wysyłanymi na questy;
  - ink (zasób) powstaje z kart: raz na turę można położyć do inkwell dowolną kartę z symbolem atramentu, a ta karta jest już tylko zasobem;
  - start z 7 kartami, talia ma co najmniej 60 kart, najwyżej 4 kopie karty i najwyżej 2 kolory atramentu;
  - **pierwszy gracz pomija dobieranie w pierwszej turze**;
  - postać nie może działać w turze zagrania („atrament musi wyschnąć”);
  - koniec talii przegrywa przy następnym dobraniu.

  https://cdn.ravensburger.com/lorcana/quickstart-en (ta sama treść jest w https://files.disneylorcana.com/S3_Quick_Start_Book_EN.pdf)
- **[wydawca, wywiad w prasie]** Steve Warner (współprojektant) dla Game Rant (2024): projektanci zaczęli od „vanilla curve”, czyli zestawu statystyk dla każdego kosztu. Potem decydowali, ile statystyk kosztuje dana zdolność, a na koniec dużo testowali i korygowali koszt w górę lub w dół. https://gamerant.com/disney-lorcana-interview-steve-warner-balance-design/
- **[wydawca, wywiad w prasie]** Ryan Miller (współprojektant) dla Player2 (2024): to, czy kartę można zamienić na atrament, jest dźwignią balansu. Karty kontroli, np. bezpośrednie obrażenia postaci czy wymuszenie odrzucenia, często nie mogą być atramentem. https://www.player2.net.au/2024/08/exclusive-we-interview-lorcana-designer-ryan-miller/

### Koszt atramentu względem statystyk i zdolności

- **[społeczność, analiza danych]** David Hashe, „Predicting character card ink costs using linear regression” (2025): regresja na postaciach z zestawów 1–6. Koszt = −1,067 + **0,477** × (siła + wytrzymałość) + **0,622** × lore. Przy R² = 0,95 trafia 87% kart bez zdolności z dokładnością do ½ atramentu. Siła i wytrzymałość mają praktycznie ten sam współczynnik (0,469 vs 0,484). Ceny słów kluczowych:

  | Słowo kluczowe | Atrament |
  |---|---|
  | Evasive | 1,10 |
  | Rush | 0,84 |
  | Ward | 0,72 |
  | Resist (za punkt) | 0,55 |
  | Bodyguard | 0,40 |
  | Challenger (za punkt) | 0,32 |
  | Support (× siła) | 0,24 |

  Autor ostrzega, że zaokrąglenie współczynników do 0,1 obniża trafność z 87% do 66%. https://dhashe.com/predicting-character-card-ink-costs-using-linear-regression-in-disney-lorcana.html
- **[społeczność]** Martin „Whippet” Zeile, „Vanilla Card Math and Beyond” (Lorcania, 2023): suma siły, wytrzymałości i lore na wczesnych kartach bez zdolności wynosi:

  | Koszt | Suma statystyk |
  |---|---|
  | 1 | 5 |
  | 2 | 6 |
  | 3 | 8 |
  | 4 | 9 |
  | 5 | 11 |

  Wzór (2 × koszt) + 1 pasuje od kosztu 4. Tanie karty (1–3) dostają premię +1 do +2. Evasive szacuje na ok. 3 punkty, a Challenger +2 na ok. 1 punkt. https://lorcania.com/articles/vanilla-card-math-and-beyond-in-disney-lorcana

### Przewaga pierwszego gracza

- **[wydawca, wywiad w prasie]** Ryan Miller dla TheGamer (2024): wewnętrzne szacunki Ravensburgera to ok. **53%** wygranych zaczynającego. Według danych Pixelborn jest to 60% wśród top-100 graczy i 53% we wszystkich partiach. https://www.thegamer.com/does-disney-lorcana-have-a-coin-flip-problem/
- **[społeczność, dane]** databorn.ink (2026): ważona przewaga zaczynającego to ok. **54,4%** (duels.ink podaje 53,9%). W meczach BO3 jest mniejsza (52,8%) niż w pojedynczych grach (53,4%). https://databorn.ink/calculator/

### Czym model Lorcany różni się od MTG i Hearthstone

- **Wygrywa się punktami (lore), a nie obrażeniami.** Postać ma trzy statystyki, a lore jest najdroższą z nich (0,62 vs 0,48 za punkt siły lub wytrzymałości).
- **Siła i wytrzymałość kosztują tyle samo.** Wynika to z regresji Hashe'a. W MTG i HS atak jest droższy od zdrowia.
- **Zasób kosztuje kartę.** Każdy atrament to karta wyjęta z ręki, a wydawca reguluje balans także tym, czy karta może być atramentem.
- **Brak bezpośredniego „face damage”.** Rush w Lorcanie pozwala tylko wyzwać postać (jak Rush w HS), więc kosztuje 0,84, czyli mniej niż Evasive.
- **Kompensata dla drugiego gracza** jest taka sama jak w MTG, czyli brak dobrania na start. Przewaga zaczynającego wynosi ok. 53–54%.

---

## Prace naukowe

Szukane obszary to wycena kart, balans gier karcianych i balans mierzony symulacją AI (self-play, algorytmy ewolucyjne). Przeszukano arXiv, materiały AAAI/AIIDE, IEEE CEC, czasopisma i repozytoria uczelni. **Nie znaleziono recenzowanej pracy, która publikowałaby tabelę cen słów kluczowych albo uniwersalny wzór kosztu karty.** Tabele cen pochodzą z analiz społeczności (Bursztein, Hashe, Magic Data Science, zob. wyżej). Literatura zajmuje się głównie **pomiarem balansu przez symulację** i automatycznym strojeniem atrybutów. To bezpośrednio dotyczy faz 2, 5 i 6.

1. **[recenzowana]** Jaffe, Miller, Andersen, Liu, Karlin, Popović, „Evaluating Competitive Game Balance with Restricted Play”, AIIDE 2012. https://ojs.aaai.org/index.php/AIIDE/article/view/12513
   - Balans sprowadzają do win rate'ów agentów z ograniczeniami grających przeciw agentom standardowym.
   - Siłę akcji mierzy agent, któremu zabroniono tej akcji.
   - Uczciwość warunków startowych mierzy agent, który zawsze wybiera dany warunek (np. białe w szachach).
   - **Dla S-06:** win rate per frakcja × strona zaczynająca to wprost ich miara uczciwości startu. Siłę pojedynczej zdolności lub karty można zmierzyć, porównując AI z zakazem jej użycia z AI bez zakazu. To uzupełnienie metryk per karta z fazy 2.
2. **[recenzowana]** Mahlmann, Togelius, Yannakakis, „Evolving Card Sets Towards Balancing Dominion”, IEEE CEC 2012. https://julian.togelius.com/Mahlmann2012Evolving.pdf (DOI 10.1109/cec.2012.6256441)
   - Algorytm genetyczny dobiera zestawy kart oceniane w 1000 symulowanych partii agentów AI.
   - Autorzy przyznają, że ewolucja prawdopodobnie znalazła zestawy, które wykorzystują słabości lub błędy agentów.
   - Siła agentów nie była przechodnia.
   - **Dla S-06:** balans zmierzony jednym AI jest balansem „pod to AI”. Strojenie wag AI i kart na tej samej symulacji może dopasować karty do słabości AI. Potwierdza to zastrzeżenie z researchu.
3. **[recenzowana]** García-Sánchez, Tonda, Mora, Squillero, Merelo, „Automated Playtesting in Collectible Card Games using Evolutionary Algorithms: a Case Study in HearthStone”, Knowledge-Based Systems (zaakceptowany manuskrypt, 2018). https://iris.polito.it/retrieve/e384c430-163c-d4b2-e053-9f05fe0a1d67/2018-kbsys.pdf
   - Algorytm ewolucyjny buduje talie grane przez AI przeciw taliom ludzi.
   - Karty, które zbyt często pojawiają się w wyewoluowanych taliach, oraz talie z bardzo wysokim win rate'em są kandydatami do przeglądu przez eksperta.
   - Ograniczenie: AI jest zachłanne (greedy), więc wyniki nie przenoszą się wprost na grę ludzi.
   - **Dla S-06:** częstość wyboru karty przez optymalizator to sygnał karty niedoszacowanej. Przyda się przy przyszłym kreatorze talii.
4. **[recenzowana]** Budijono i in., „LUDUS: An Optimization Framework to Balance Auto Battler Cards”, AAAI 2022. https://doi.org/10.1609/aaai.v36i11.21550
   - Automatyczne testy gry połączone z globalnym przeszukiwaniem parametrów każdej karty.
   - Przybliżenie przez próbkowanie zmniejsza liczbę potrzebnych symulacji.
   - Autorzy definiują metryki „zdrowia” metagry.
   - **Dla S-06:** potwierdza podejście „wzór + symulacja”. Automatyczne przeszukiwanie jest poza zakresem S-06 (plan: „What We're NOT Doing”).
5. **[preprint]** de Mesentier Silva, Canaan, Lee, Fontaine, Togelius, Hoover, „Evolving the Hearthstone Meta”, arXiv:1907.01623 (2019). https://arxiv.org/abs/1907.01623
   - Algorytm ewolucyjny zmienia koszt, atak i zdrowie kart (w zakresie ±3), żeby win rate'y pojedynków zbliżyły się do 50%.
   - Wersja wielokryterialna (NSGA-II) dodatkowo minimalizuje liczbę zmian.
   - W mierze wielkości zmiany koszt many ma wagę 2, a atak i zdrowie wagę 1. Uzasadnienie autorów: minion droższy o 1 manę ma zwykle +1/+1.
   - Proponują metryki per karta: win rate, gdy karta została dobrana (WRD), i gdy została zagrana (WRP). Osłabienie kart z wysokim WRD mocniej obniża win rate talii.
   - Ocena 300 partiami na osobnika (100 na pojedynek) była zaszumiona.
   - **Dla S-06:** (a) „+1 koszt ≈ +1/+1” to niezależne potwierdzenie budżetu ok. 2 statystyk na punkt kosztu; (b) WRD i WRP to gotowe metryki per karta do śladu z fazy 2; (c) setki partii na komórkę to za mało na ciasną bramkę.
6. **[preprint]** Volz, Rudolph, Naujoks, „Demonstrating the Feasibility of Automatic Game Balancing”, arXiv:1603.03795 (2016). https://arxiv.org/abs/1603.03795
   - Wielokryterialny algorytm ewolucyjny balansuje talie Top Trumps według celów symulacyjnych: uczciwości (win rate równych graczy ≈ 50%) i emocji (np. liczby lew).
   - Pojedynczy cel nie wystarcza.
   - **Dla S-06:** obok bramki 40–60% warto raportować miary przebiegu partii (liczba tur, `EndReason`), co faza 2 już planuje.
7. **[preprint]** Miernik, Kowalski, „Evolving Evaluation Functions for Collectible Card Game AI”, arXiv:2105.01115 (2021). https://arxiv.org/abs/2105.01115
   - Ewolucja funkcji oceny stanu gry karcianej (Legends of Code and Magic), m.in. jako wektora wag liniowej kombinacji cech, czyli tak jak `PositionEvaluator`.
   - Wynik zależy od tego, z kim gra przeciwnik w funkcji dopasowania (słabym, silnym, poprzednim najlepszym).
   - **Dla S-06 (faza 5):** wagi liniowe to uznana reprezentacja. Strojenie wag tylko przeciw `RandomPlayer` grozi dopasowaniem do słabego przeciwnika, więc bramka ≥ 80% powinna zostać tylko kontrolą siły.
8. **[preprint]** praca o przeszukiwaniu w Legends of Code and Magic, arXiv:2609.06816 (2026). http://arxiv.org/abs/2609.06816v1
   - Przy **identycznych taliach** obu stron zaczynający wygrywał 54–56%, a drugi 46,6%.
   - Strona zaczynająca była zmieniana co partię, a ziarna były rozłączne.
   - Autorzy podają przedziały Wilsona 95%. Przy 1000 partiach win rate „może przesunąć się o ±3 p.p.”.
   - **Dla S-06:** (a) przewaga zaczynającego występuje nawet w lustrzanym pojedynku AI–AI, więc trzeba ją mierzyć osobno, co plan robi; (b) przy 250 partiach na komórkę niepewność jest większa niż ±3 p.p., więc cel strojenia 45–55% wewnątrz bramki 40–60% jest uzasadniony.
9. **[preprint]** Świechowski, Tajmajer, Janusz, „Improving Hearthstone AI by Combining MCTS and Supervised Learning Algorithms”, arXiv:1808.04794 (2018). https://arxiv.org/abs/1808.04794
   - W pojedynku tego samego MCTS ze stałym przypisaniem talii (agresywna zawsze pierwsza) pierwszy gracz wygrywał 73,5%.
   - Autorzy przypisują to głównie różnicy talii.
   - **Dla S-06:** frakcja i strona zaczynająca mieszają się, jeśli nie rozdzieli się ich w pomiarze. To argument za siatką 2×2 (frakcja × kto zaczyna).
10. **[preprint, miejsce publikacji niepotwierdzone]** Zhang, Guo, Zhang, Cai, „A Human-Centered Automated Framework for Integrating AI in Balancing Numerical Generation for Collectible Card Games” (2025). https://faculty.washington.edu/weicaics/paper/papers/TairanZGZC2025.pdf
    - Siłę karty mierzą rozkładem nagrody z MCTS w stanach, w których kartę zagrano, a nie samym win rate'em.
    - Rozkład bywa dwumodalny: karta jest świetna w części sytuacji, a w pozostałych przeciętna.
    - Grano N = 1000 partii na parę talii.
    - Balans czarów zależy głównie od jednego parametru, kosztu many.
    - **Dla S-06:** sam win rate per karta to miara zgrubna. Karty drogie często tylko „utrwalają” wygrywającą pozycję.

**Wniosek z literatury:** prace naukowe nie dają gotowych cen. Dają za to metodę, którą plan S-06 już realizuje: jawny model kosztu jako hipotezę, symulację AI kontra AI z rozdzieleniem strony zaczynającej, metryki per karta (WRD/WRP) i świadomość, że wynik zależy od AI.

---

## Mapowanie na ITCardBrawl

Fakty o ITCardBrawl pochodzą z kodu:
- `MatchRules.java`: 30 HP, budżet do 10, talia 30, ręka startowa 5, plansza 5, ręka 10.
- `AbilityKeyword.java`, `EffectKind.java`, `EffectScope.java`.
- `MatchEngine.java`:
  - atakujący wybiera cel;
  - obie postacie zadają sobie obrażenia jednocześnie, a obrażenia zostają na postaci;
  - GUARD wymusza cel i blokuje atak w gracza;
  - RUSH pozwala atakować w turze zagrania, także gracza;
  - uwięziona postać (TRAP) nie atakuje, nie może być celem ataku i nie liczy się jako GUARD.

Model walki jest więc najbliższy Hearthstone. HS też ma 30 kart w talii i 30 zdrowia bohatera (Zhang i in. 2025, opis eksperymentu).

| Element ITCardBrawl | Najbliższy odpowiednik | Cena w źródłach | Różnica / uwaga |
|---|---|---|---|
| **Atak** postaci | HS Attack; MTG power | HS 0,50–0,57 many/pkt (Block, Bursztein); MTG 0,78 MV (Magic Data Science) | W HS i MTG atak jest droższy od zdrowia; w Lorcanie nie (0,477 = 0,477). |
| **Zdrowie** postaci | HS Health; MTG toughness | HS 0,40–0,44/pkt; MTG 0,53 MV | Obrażenia zostają na postaci, jak w HS. |
| **Budżet statystyk** | „2 × koszt + 1” (HS); „≈ 2 × koszt” (MTG); Lorcana 2c+1 od kosztu 4, premia dla 1–3 | — | Krzywa MTG spłaszcza się powyżej 7. U nas atak i zdrowie ≤ 10 (`CardCatalog`). |
| **Wartość samej karty** | Intercept Bursztein; MTG intercept 0,59 MV | ok. ½ many | U nas ręka ma limit 10, a nadmiar przepada. |
| `GUARD` | HS Taunt; Lorcana Bodyguard | Taunt 0,51 many lub +1 pkt statystyk; Bodyguard 0,40 | U nas GUARD blokuje też atak w gracza, jak Taunt w HS. |
| `RUSH` | **HS Charge** (atak także w bohatera), nie HS/Lorcana Rush | Charge 0,33 many **za punkt ataku** (Bursztein); Lorcana Rush 0,84 (tylko postacie) | Nazwa myli: nasz RUSH to Charge. Blizzard uważa Charge za najtrudniejszy do zbalansowania (Brode). Cena powinna rosnąć z atakiem. |
| `ON_PLAY_DAMAGE N` | HS Battlecry: obrażenia w bohatera; MTG Lava Axe | Damage 0,82/pkt (dowolny cel, Bursztein); obrażenia tylko w gracza to „drawback” (MTG Salvation) | U nas tylko w przeciwnika (nie w postać), więc taniej niż `DAMAGE TARGET`. |
| `HEAL N` (własne HP) | HS Self Hero-Heal; MTG life gain | 0,34/pkt (Bursztein); WotC: leczenie strategicznie słabe | Leczenie nie przekracza 30 HP (`MatchRules.MAX_HP`), więc część może przepaść. |
| `OVERTIME` (1 obrażenie/turę) | Efekt powtarzalny „na koniec tury” | **Brak bezpośredniego źródła.** MTG: efekt powtarzalny (zdolność aktywowana) kosztuje więcej niż jednorazowy (MTG Salvation) | Wartość zależy od tego, ile tur postać przeżyje. Do wyceny jako 1 pkt × przewidywana liczba tur. **Założenie własne.** |
| `PLAYER_DAMAGE` (OPPONENT) | MTG burn tylko w gracza; HS Damage | 0,82/pkt (HS); MTG 5 obrażeń tylko w gracza taniej niż 5 w dowolny cel | Jak `ON_PLAY_DAMAGE`, ale bez ciała postaci. |
| `DAMAGE` TARGET | HS Damage; MTG burn „creature or player” | 0,82/pkt; MTG 2 obr. ≈ 1, 3 ≈ 2, 4 ≈ 4, 5 ≈ 6 many | Cena za punkt rośnie z kwotą (MTG). Cel to postać **lub** gracz, czyli „upside”. |
| `DAMAGE` ENEMY_BOARD / ENEMY_ALL | HS Board Damage | 1,84/pkt (ok. 2,2 × pojedynczy cel) | ENEMY_ALL dodaje gracza, więc drożej niż ENEMY_BOARD. Plansza ma najwyżej 5 postaci. |
| `DAMAGE` ALL_BOARDS | Symetryczny „board wipe” | **Brak ceny w źródłach** | Rani też własne postacie, czyli „drawback” (MTG Salvation). Tańsze niż ENEMY_BOARD. **Założenie własne.** |
| `HEAL` TARGET / OWN_BOARD / OWN_ALL / ALL_BOARDS | HS Heal | 0,34/pkt | Leczenie postaci działa tylko na obrażone postacie. Zakres mnożony jak przy obrażeniach. **Założenie własne.** |
| `DRAW N` | HS Draw Card; MTG draw | HS 1,84 many/kartę; MTG 2 karty ≈ 3, 3 ≈ 4, 4 ≈ 5 many | U nas limit ręki 10 i przegrana przy pustej talii (`MatchEngine`), więc DRAW skraca partię. |
| `BUDGET_GAIN N` (ta tura) | HS The Coin (0 many, +1 na jedną turę) | Brak ceny liczbowej; The Coin to oficjalna kompensata za ruch drugi | Jednorazowe przyspieszenie. Wartość maleje wraz z kosztem docelowej karty (Chang, CoolStuffInc, fragment z wyników wyszukiwania: https://www.coolstuffinc.com/a/understanding-mana-curves-mana-acceleration [społeczność]). |
| `BUDGET_MAX N` | MTG rampa (Rampant Growth, Cultivate) | Brak ceny w przeczytanych źródłach | Trwały +1 budżetu na turę, mało wart przy budżecie bliskim 10. **Założenie własne.** |
| `ATTACK` / `HEALTH` ± (modyfikator) | HS buff/debuff | Brak ceny dla efektów czasowych | Przy `duration` > 0. Cena za punkt jak statystyka × liczba postaci × czas. **Założenie własne.** |
| `GUARD` / `RUSH` nadane (modyfikator) | Nadanie Taunt/Charge | Jak słowo kluczowe | Mnożone przez liczbę postaci w zakresie i czas trwania. |
| `TRAP` (TARGET) | HS Freeze (czasowe wyłączenie) | Freeze 1,02 (Bursztein) | U nas TRAP trwa `duration` tur i działa w obie strony (postaci nie można też zaatakować). Bliżej „czasowego Destroy” (5,33) przy długim trwaniu. **Wymaga pomiaru.** |
| `EffectScope` | Zasięg efektu | HS: pojedynczy cel 0,82 vs wszyscy wrogowie 1,84 (×≈2,2) | Mnożnik zasięgu ok. 2 dla planszy przeciwnika (plansza ≤ 5). Dla zakresu obejmującego własną stronę „drawback”. |
| `duration` (0–5 tur) | Brak bezpośredniego odpowiednika z ceną | **Brak źródła** | Liniowo z liczbą tur, z dyskontem (efekt może przestać działać, gdy postacie zginą). **Założenie własne, do weryfikacji symulacją.** |
| **Brak dobrania w turze 1** zaczynającego | MTG 103.8a; Lorcana Quick Start | Przewaga zaczynającego mimo to 52–55% (MTG), 53–54% (Lorcana) | U nas tak samo, więc należy oczekiwać przewagi zaczynającego. Plan ma warunkową kartę-monetę (`neu-coffee`) w fazie 6, co odpowiada The Coin w HS. |

**Różnice ITCardBrawl, które zmieniają przeniesienie cen:**
- **30 HP**, jak w HS, a nie 20 jak w MTG. Obrażenie w gracza jest więc względnie mniej warte niż w MTG. Ceny HS przenoszą się lepiej.
- **Budżet do 10, +1 na turę, pełne uzupełnienie** (`MatchEngine`), jak mana w HS. Zasób nie kosztuje karty, w przeciwieństwie do MTG i Lorcany. Nie ma many kolorowej, więc MTG-owe „dopłaty kolorem” (części many) nie istnieją. Każda zmiana ceny to pełny punkt kosztu albo zmiana statystyk.
- **Ręka startowa 5, ręka do 10, plansza do 5.** Efekty na planszę mają górny limit 5 celów, a DRAW przy pełnej ręce przepada.
- **Atak w gracza bez blokowania** (atakujący wybiera, chyba że jest GUARD), jak w HS. Dlatego `RUSH` ma siłę HS-owego Charge.
- **Koszty całkowite 1–10**, jak u wszystkich trzech wydawców. Wzór da wartości ułamkowe, a karta ma koszt całkowity, stąd potrzeba tolerancji.

---

## Wnioski dla wzoru

Poniższe założenia proponuję do `context/foundation/card-pricing.md` (faza 3). Każde ma źródło albo jest oznaczone jako założenie własne do weryfikacji symulacją.

1. **Model liniowy addytywny.** Wartość karty = wartość bazowa karty + Σ (cena atrybutu × wielkość). Źródła: Bursztein (założenia 1–5), regresje Block, Magic Data Science i Hashe. Wszystkie dają R² ≈ 0,93–0,95 na kartach bez tekstu. Efekty o stałej cenie są mnożone przez kwotę.
2. **Budżet statystyk ≈ 2 punkty (atak + zdrowie) na punkt kosztu, plus ok. 1 punkt wartości samej karty.** Źródła:
   - HS: „2 × koszt + 1”;
   - MTG: „≈ 2 × koszt”;
   - Lorcana: 2c+1 od kosztu 4;
   - Silva i in.: +1 koszt ≈ +1/+1.

   Proponowana forma: `koszt ≈ (atak + zdrowie − 1) / 2` dla postaci bez zdolności. Dla kosztu 10 daje to 21 punktów, czyli więcej niż limit walidacji (atak i zdrowie ≤ 10 każde, łącznie ≤ 20). Wzór musi więc mieć **spłaszczenie krzywej przy wysokich kosztach**, co odpowiada obserwacji MagicProfessor powyżej 7. Albo karty za 9–10 muszą mieć zdolności. **Decyzja dla fazy 3.**
3. **Atak a zdrowie.** MTG i HS cenią atak wyżej (o 30–45%), Lorcana tak samo. Nasza walka jest jak w HS (jednoczesna wymiana obrażeń, atak w gracza). Proponuję więc **atak nieco droższy od zdrowia**, np. stosunek ok. 1,0 : 0,75 w punktach statystyk. To leży w środku zakresu HS (0,57 : 0,40) i MTG (0,78 : 0,53). Faza 5 może wtedy wyprowadzić `ATTACK_WEIGHT`/`HEALTH_WEIGHT` z tych samych liczb (dziś 1,0 : 0,7). **Założenie do akceptacji autora.** Alternatywa: równe ceny jak w Lorcanie, prostsze dla autora kart.
4. **Ceny słów kluczowych postaci** (w punktach statystyk, 1 pkt kosztu ≈ 2 pkt statystyk):
   - `GUARD` ≈ **1 pkt** (Taunt +1 wg forum; 0,51 many wg Burszteina; Bodyguard 0,40);
   - `RUSH` **rośnie z atakiem**, ok. **0,5–0,6 pkt za punkt ataku**. Cena Charge u Burszteina to 0,33 many/atak, czyli ok. 0,6 pkt statystyk. Blizzard uważa Charge za najgroźniejszy dla balansu, więc nie należy go zaniżać;
   - `ON_PLAY_DAMAGE N` ≈ **1,5 pkt za obrażenie** (0,82 many/pkt w HS), z rabatem, bo trafia tylko gracza (MTG: obrażenia tylko w gracza to „drawback”). Proponuję ok. **1,2–1,5 pkt/obrażenie**;
   - `HEAL N` ≈ **0,6 pkt za HP** (0,34 many/pkt w HS), czyli najtańszy efekt (WotC: leczenie słabe);
   - `OVERTIME` ≈ 1 obrażenie × przewidywana liczba tur życia postaci. **Brak źródła**, proponuję start od ceny `ON_PLAY_DAMAGE 2` i weryfikację metryką per karta z symulacji.
5. **Ceny efektów wydarzeń** (w punktach kosztu, z HS jako głównym odniesieniem):
   - `DAMAGE` TARGET ≈ 0,8 kosztu/pkt, z rosnącą ceną krańcową dla dużych kwot (MTG: 2 ≈ 1, 3 ≈ 2, 4 ≈ 4, 5 ≈ 6);
   - `PLAYER_DAMAGE` taniej niż `DAMAGE` TARGET, bo tylko w gracza;
   - zasięg ENEMY_BOARD ≈ ×2,2 ceny pojedynczego celu (Board Damage 1,84 vs 0,82); ENEMY_ALL trochę drożej;
   - zasięgi obejmujące własną stronę (ALL_BOARDS) z rabatem, bo to „drawback”;
   - `DRAW` ≈ 1,8 kosztu/kartę (HS). MTG daje mniej za kolejne karty (2 ≈ 3, 3 ≈ 4), więc proponuję cenę malejącą dla kolejnych kart;
   - `HEAL` ≈ 0,35 kosztu/pkt, a zasięg jak przy obrażeniach;
   - `BUDGET_GAIN`, `BUDGET_MAX`, `TRAP`, modyfikatory z `duration`: **brak liczbowych cen w źródłach**. Ceny proponuje faza 3 jako założenia własne, a weryfikuje symulacja (metryki per karta). `TRAP` ma odniesienie do Freeze (1,02) przy krótkim czasie, z ceną rosnącą z `duration`.
6. **Czas trwania (`duration`).** Brak źródła. Proponuję cenę modyfikatora za turę × liczbę tur, z dyskontem dla dalszych tur, np. ostatnie tury liczone częściowo, bo postacie mogą zginąć. **Założenie własne.**
7. **Krzywa nieliniowa na skrajach.** Karty za 1 nie mogą mieć „pełnej” wartości: 1/1 za 1 jest słabe, bo się nie wymienia (MagicProfessor). Karty za 9–10 mają spłaszczony budżet statystyk (MTG). Lorcana daje premię tanim kartom (+1/+2). Proponuję jawną tabelę budżetu na koszt (1–10) zamiast jednego mnożnika, z tolerancją ±½ kosztu, bo koszty są całkowite, a regresje trafiają ok. 87% kart w ±½ (Hashe).
8. **Karty dominowane i spójność.** Te same zdolności i efekty muszą mieć tę samą cenę na każdej karcie. To założenie 3 Burszteina (stała cena efektu), a jego metoda znajdowała odstające karty jako niedoszacowane lub przeszacowane. Odpowiada to zaplanowanemu `CardPricing.dominates` i tolerancji w `CardPricingTests`.
9. **Wzór nie zastępuje pomiaru.** Wszystkie źródła mówią to samo:
   - Warner: vanilla curve, potem dużo gry i korekty;
   - Bursztein: model pomija stan planszy;
   - Mahlmann, García-Sánchez: wynik zależy od AI.

   Wzór jest hipotezą, a symulacja komputer–komputer (fazy 2 i 6) ją weryfikuje. Metryki per karta warto liczyć jak WRD i WRP (Silva i in.).
10. **Przewaga zaczynającego.** Sam brak dobrania w turze 1 zostawia zaczynającemu 52–55% w MTG i 53–54% w Lorcanie. W HS dopiero 4. karta + The Coin dały ok. 52%. W lustrzanym AI–AI w LoCM jest to 54–56%. Należy więc **oczekiwać** przewagi zaczynającego w naszej symulacji. Warunkowa karta-moneta z fazy 6 ma bezpośredni precedens w The Coin (wydawca). Cel Blizzarda to przewaga 5–10 p.p. („jak w szachach”). Nasz cel strojenia 45–55% na komórkę jest od niego ostrzejszy.
11. **Liczba partii.** Przy 1000 partiach win rate waha się o ok. ±3 p.p. (arXiv:2609.06816), a 300 partii na osobnika było zaszumione (Silva i in.). 250 partii na komórkę (plan) daje więc szeroki przedział. Margines między celem 45–55% a bramką 40–60% jest konieczny.

### Decyzje autora (akceptacja fazy 1, 2026-11-22)

Autor zaakceptował powyższe wnioski z następującymi rozstrzygnięciami. Faza 3 (`card-pricing.md`) przyjmuje je jako wiążące:

- **Pkt 2 (wysokie koszty):** karty za 9–10 muszą mieć zdolności albo wyższe statystyki niż karty tańsze (w granicach walidacji: atak i zdrowie ≤ 10). Nie wprowadzamy samego spłaszczenia, które pozwoliłoby na „gołe” karty za 9–10 o słabszym budżecie.
- **Pkt 3 (atak a zdrowie):** stosunek **1,0 : 0,75**. Atak jest ważniejszy od zdrowia. Faza 5 wyprowadza z niego `ATTACK_WEIGHT`/`HEALTH_WEIGHT`.
- **Pkt 4–6 (ceny bez źródła):**
  - `OVERTIME`: start od ceny `ON_PLAY_DAMAGE 2`, jak zaproponowano;
  - `ALL_BOARDS`: start od proponowanego rabatu względem zasięgu tylko wrogiego;
  - `TRAP`: wyceniany **wyżej** niż odniesienie Freeze (1,02), bo w praktyce jest usunięciem postaci (removal);
  - `duration`, `BUDGET_GAIN`, `BUDGET_MAX`: zostają założeniami własnymi fazy 3, weryfikowanymi symulacją.
- **Pkt 11 (szum pomiaru):** przy 250 partiach na komórkę 95% przedział ufności to ok. ±6 p.p. (1,96·√(0,25/250)), czyli jest szerszy niż cel 45–55%. Dlatego **45–55% jest celem orientacyjnym**, nie warunkiem zaliczenia. W fazie 6 dążymy do wyniku jak najbliżej tego zakresu, ale twardą bramką jest tylko 40–60%. Kryterium 6.2 planu („każda komórka 45–55%”) czytamy zgodnie z tą decyzją.

---

## Log wyszukiwania

Wszystkie zapytania wysłano przez Exa (`mcp__plugin_exa_exa__web_search_exa`), a strony czytano przez `mcp__plugin_exa_exa__web_fetch_exa`. „Przydatne” oznacza wyniki, z których pochodzi twierdzenie w dokumencie.

| # | Obszar | Zapytanie (skrót treści) | Wyników | Przydatnych |
|---|---|---|---|---|
| 1 | MTG | Wizards of the Coast designer article on how creature stats relate to mana cost and the "vanilla" creature curve | 8 | 3 (Rosewater 2003, TCGplayer Empirical Vanilla Test, MagicProfessor) |
| 2 | MTG | Magic card advantage and tempo explained, designer or classic strategy article | 8 | 2 (Card Kingdom, SCG Flores) |
| 3 | MTG | Comprehensive rules: player who plays first skips the draw step | 6 | 2 (Comprehensive Rules PDF, Reid Duke „Play or Draw?”) |
| 4 | MTG | R&D article on how burn spells and card draw are costed | 8 | 1 (MTG Salvation „The price is right”) |
| 5 | MTG | 17lands win rate on the play vs on the draw, first player advantage statistics | 6 | 2 (17Lands Format Speed, Portent Lab) |
| 6 | MTG | Mark Rosewater on why life gain is weak and costed cheaply | 5 | 2 (Verhey 2017, Rosewater 2025); kryterium zakończenia, bez nowych zasad poza leczeniem |
| 7 | Hearthstone | Vanilla test explained: minion stats per mana cost, keywords Taunt, Charge, Divine Shield | 8 | 3 (forum Blizzarda, Mr Wong, Dot Esports) |
| 8 | Hearthstone | Regression analysis of Hearthstone card costs: mana value of attack, health, keywords | 8 | 2 (Bursztein cz. 1, CTOForADay) |
| 9 | Hearthstone | Bursztein „How to find undervalued Hearthstone cards automatically” coefficients | 5 | 1 (Bursztein cz. 2, tabela cen) |
| 10 | Hearthstone | The Coin, win rate going first vs second, Blizzard developer explanation | 8 | 3 (BlizzPro/Brode 2013, Hearthstone Wiki, Ars Technica 2014; także BlizzPro 2016) |
| 11 | Hearthstone | Designer (Ben Brode) explains how card mana costs are designed, Charge, card draw | 8 | 2 (Ars Technica 2018 Rush vs Charge, PC Gamer 2015) |
| 12 | Lorcana | Official quick start rules: inkwell, first player skips draw, 20 lore | 6 | 1 (Quick Start Rules, kilka wersji) |
| 13 | Lorcana | Card evaluation: vanilla test, strength/willpower/lore per ink, keyword costs | 8 | 2 (Hashe regresja, Lorcania) |
| 14 | Lorcana | Going first win rate statistics, first player advantage | 6 | 2 (TheGamer/Miller, databorn.ink) |
| 15 | Lorcana | Designer interview on how card costs and ink are balanced, differences from Magic | 6 | 2 (Game Rant/Warner, Player2/Miller) |
| 16 | Prace naukowe | Automated balancing of CCG using AI self-play simulation and evolutionary algorithms | 10 | 5 (Silva i in. 2019, García-Sánchez i in., Volz i in., Zhang i in. 2025, Ludus AAAI 2022) |
| 17 | Prace naukowe | Jaffe et al. „Evaluating Competitive Game Balance with Restricted Play” AIIDE 2012 | 5 | 1 |
| 18 | Prace naukowe | Mahlmann et al. „Evolving card sets towards balancing Dominion” IEEE CEC 2012 | 5 | 1 |
| 19 | Prace naukowe | Measuring first-player advantage and card strength through AI simulations (LoCM, Hearthstone, MCTS) | 8 | 3 (Świechowski i in. 2018, arXiv:2609.06816, Miernik i Kowalski 2021) |

**Kryterium zakończenia:** zapytania 6 i 19 przyniosły tylko potwierdzenia znanych zasad (leczenie jest słabe, przewaga zaczynającego istnieje), a nie nowe składniki wzoru. Luki, których kolejne zapytania nie zamknęły, czyli brak cen źródłowych:
- `duration`;
- `BUDGET_GAIN` i `BUDGET_MAX`;
- `ALL_BOARDS`;
- `OVERTIME`.

Te ceny są w „Wnioskach dla wzoru” oznaczone jako założenia własne.
