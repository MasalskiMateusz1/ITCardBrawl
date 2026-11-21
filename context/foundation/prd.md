---
project: "ItCardBrawl"
version: 1
status: draft
created: 2026-11-16
context_type: greenfield
product_type: web-app
target_scale:
  users: small
  qps: "# TODO: qps — see Open Questions"
  data_volume: "# TODO: data_volume — see Open Questions"
timeline_budget:
  mvp_weeks: 6
  hard_deadline: null
  after_hours_only: true
---

# ItCardBrawl

## Vision & Problem Statement

ItCardBrawl nie rozwiązuje konkretnego problemu — to gra karciana dla rozrywki. Osoba pracująca w IT wieczorem po pracy gra dziś w dowolną inną grę albo zajmuje się innym hobby; ItCardBrawl konkuruje o ten wolny czas.

Wgląd: ogólne gry karciane nie dają trzech rzeczy naraz — (1) żartów i sytuacji, które są zabawne tylko dla kogoś z branży IT, (2) mechaniki, która sama odzwierciedla realia pracy w IT (a nie tylko grafiki o IT), (3) krótkiej partii w przeglądarce bez instalacji. Gra ma łączyć humor i rozpoznawalność IT z taktyczną rozgrywką. Kontekst autora: projekt jest również dla autora sposobem nauki budowania aplikacji.

## User & Persona

**Główna persona:** osoba pracująca w IT — developer, tester lub PM — która wieczorem po pracy szuka krótkiej rozrywki i od razu rozpoznaje branżowe żarty i sytuacje bez tłumaczenia.

Moment sięgnięcia po produkt: wieczór po pracy.

Tryb gry dla tej persony: najpierw solo przeciwko komputerowi; multiplayer z innymi ludźmi ewentualnie w przyszłości.

## Success Criteria

Pierwszy przepływ MVP (zablokowany):

1. Gracz się loguje.
2. Wybiera jedną z 2 frakcji.
3. Rozpoczyna partię przeciwko przeciwnikowi komputerowemu.
4. Gracz i przeciwnik na zmianę zagrywają karty: postacie (np. Junior Dev, Senior) oraz wydarzenia/spelle (np. „Nagła zmiana założeń projektowych”).
5. Karty zadają obrażenia HP przeciwnika (albo leczą własne HP).
6. Ten, czyje HP spadnie do 0, albo kto nie może dobrać karty z pustej talii — przegrywa; gracz widzi wynik partii.

### Primary
- Zalogowany gracz rozgrywa pełną partię dowolną z 2 frakcji przeciwko przeciwnikowi komputerowemu, od wyboru frakcji do wyniku (wygrana/przegrana rozstrzygnięta przez spadek HP do 0 albo koniec kart w talii).

### Secondary
- Znajomi z IT grają i śmieją się z kart — rozpoznają żarty bez tłumaczenia.
- Gracz z własnej woli wraca na kolejną partię.
- Autor nauczył się budować aplikację dzięki temu projektowi.

### Guardrails
- Jedna partia mieści się w krótkim czasie (≤ 15 minut).
- Żadna frakcja nie wygrywa zawsze — obie mają realną szansę na zwycięstwo.
- Dane logowania gracza są bezpieczne.
- Gra działa w przeglądarce bez instalacji.
- Przeciwnik komputerowy gra sensownie — nie losowo (z rundy Sokratesa, FR-003).

## User Stories

### US-01: Gracz rozgrywa partię przeciwko komputerowi

- **Given** zalogowany gracz
- **When** wybiera jedną z 2 frakcji i rozpoczyna partię
- **Then** na zmianę z komputerem zagrywa karty postaci i wydarzeń, a partia kończy się, gdy HP którejś strony spadnie do 0 albo nie może ona dobrać karty z pustej talii; gracz widzi wynik

#### Acceptance Criteria
- Gracz widzi zagrania komputera i ich skutek (FR-006).
- HP obu stron jest widoczne przez całą partię (FR-007).
- Ekran wyniku pozwala od razu zagrać ponownie (FR-008).

## Functional Requirements

### Konto
- FR-001: Gracz może założyć konto i się zalogować. Priority: must-have
  > Socrates: Counter-argument considered: "w v1 nic nie jest zapisywane na koncie, więc logowanie to sama bariera i koszt z 6 tygodni."
  > Resolution: kept — logowanie jest celem nauki autora i fundamentem pod przyszły progres / PvP.

### Przygotowanie partii
- FR-002: Gracz może wybrać jedną z 2 frakcji (z gotową, predefiniowaną talią) przed partią. Priority: must-have
  > Socrates: Counter-argument considered: "gotowe talie szybko się nudzą — każda partia jest podobna."
  > Resolution: kept — różnorodność między partiami daje losowanie kart z talii.
- FR-003: Gracz może rozpocząć partię przeciwko przeciwnikowi komputerowemu. Priority: must-have
  > Socrates: Counter-argument considered: "jeśli komputer gra losowo / głupio, taktyka traci sens i frajda znika."
  > Resolution: kept — przeciwnik komputerowy musi grać sensownie; dopisane jako guardrail.

### Rozgrywka
- FR-004: Gracz może zagrywać karty postaci (np. Junior Dev, Senior). Priority: must-have
  > Socrates: Counter-argument considered: "postacie zostające na stole (wiele tur) wymagają planszy i dużo zasad."
  > Resolution: kept — postacie na stole to rdzeń taktyki gry.
- FR-005: Gracz może zagrywać karty wydarzeń/spelli (np. „Nagła zmiana założeń projektowych”). Priority: must-have
  > Socrates: Counter-argument considered: "wydarzenia mogą być zbyt losowe — karta odwracająca partię zabiera kontrolę i taktykę."
  > Resolution: kept — efekty kart wydarzeń mają być przewidywalne (gracz wie, co karta zrobi, zanim ją zagra).
- FR-006: Przeciwnik komputerowy może rozgrywać własne tury, a gracz widzi, co komputer zagrał i dlaczego zmienił się stan gry. Priority: must-have
  > Socrates: Counter-argument considered: "długie lub niezrozumiałe tury komputera spowalniają partię (guardrail czasu partii)."
  > Resolution: modified — dodano wymóg, że gracz widzi zagrania komputera i ich skutek.
- FR-007: Gracz może w każdej chwili widzieć HP swoje i przeciwnika. Priority: must-have
  > Socrates: Counter-argument considered: "ukryty stan przeciwnika dodałby napięcia."
  > Resolution: kept — przy grze taktycznej jawność stanu jest ważniejsza.
- FR-008: Gracz może zobaczyć wynik partii (wygrana/przegrana) i od razu zagrać ponownie. Priority: must-have
  > Socrates: Counter-argument considered: "sam wynik bez dalszej akcji sprawia, że gracz odchodzi (Secondary: gracz wraca)."
  > Resolution: modified — ekran wyniku oferuje „zagraj ponownie”.

## Non-Functional Requirements

- Jedna partia trwa ≤ 15 minut.
- Gra działa w ostatnich dwóch głównych wersjach czterech głównych przeglądarek na komputerze, bez instalacji.
- Dane konta i logowania gracza nie są dostępne dla osób trzecich.

## Business Logic

Wygrywa ten, kto doprowadzi HP przeciwnika do 0 (lub poniżej) albo sprawi, że przeciwnik nie będzie mógł dobrać karty, bo jego talia jest pusta.

Dane wejściowe reguły widoczne dla gracza: wybrana frakcja z gotową talią, karty postaci i wydarzeń zagrywane na zmianę przez gracza i przeciwnika komputerowego oraz bieżące HP obu stron. Każda strona zaczyna z 30 HP; obrażenia obniżają HP, a leczenie podnosi je najwyżej do 30.

Zagranie karty kosztuje budżet równy jej kosztowi (1–10). Zaczynający ma w pierwszej turze budżet 1/1, druga strona startuje z 0/0; na początku każdej następnej własnej tury maksymalny budżet rośnie o 1 (najwyżej do 10), a bieżący odnawia się do maksimum — więc druga strona ma w swojej pierwszej turze 1/1. Karty, na które nie starcza bieżącego budżetu, nie mogą zostać zagrane.

Wynik: partia kończy się w chwili, gdy któraś strona ma HP ≤ 0 albo musi dobrać kartę z pustej talii; ta strona przegrywa. HP może spaść poniżej zera i ta wartość jest pokazywana (np. −3/30). Gracz spotyka regułę przez cały przebieg partii (stale widoczne HP obu stron, widoczne skutki zagrań) i na ekranie wyniku.

W v1 jedynym licznikiem przegranej jest HP; dług techniczny nie jest częścią pierwszej wersji. Maksymalne (i startowe) HP wynosi 30, a talia każdej frakcji liczy 30 kart.

## Access Control

- Logowanie wymagane — bez konta nie da się grać (brak trybu gościa).
- Płaski model: jedna rola „gracz”; wszyscy zalogowani mają te same możliwości. Brak roli admina w aplikacji — karty i zasady autor zmienia poza grą.
- W pierwszej wersji konto służy do logowania; gracz gra gotowymi taliami frakcji, bez zapisywanego postępu. System progresu (odblokowywanie kart za granie) — później.
- Niezalogowany użytkownik wchodzący do gry jest kierowany do logowania / rejestracji.
- Metoda logowania: wyłącznie przez zewnętrzne konto (bez własnych haseł w grze).

## Non-Goals

- **Brak PvP z ludźmi i matchmakingu** — v1 to wyłącznie gra przeciwko komputerowi (ani przez sieć, ani na jednym ekranie); PvP to wersja 2.
- **Brak budowania talii, kolekcji kart i odblokowań** — tylko gotowe talie 2 frakcji; progres przez odblokowywanie kart przyjdzie później.
- **Brak wersji mobilnej i trybu offline** — v1 to gra w przeglądarce na komputerze, wymagająca internetu.
- **Brak mikropłatności, reklam i panelu admina** — karty i zasady autor zmienia poza aplikacją; brak monetyzacji.

## Open Questions

1. **Jaki jest oczekiwany ruch (`target_scale.qps`)?** — Nie podano w shape-notes (przy skali `small` zakładany jako niski). Owner: user. Do potwierdzenia przy wyborze stacku. Block: no.
2. **Jaka jest oczekiwana ilość danych (`target_scale.data_volume`)?** — Nie podano w shape-notes (przy skali `small` zakładana jako niska). Owner: user. Do potwierdzenia przy wyborze stacku. Block: no.
