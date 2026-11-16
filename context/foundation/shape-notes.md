---
project: "ItCardBrawl"
context_type: greenfield
created: 2026-11-16
updated: 2026-11-16
checkpoint:
  current_phase: 8
  phases_completed: [1, 2, 3, 4, 5, 6, 7]
  gray_areas_resolved:
    - topic: "context type"
      decision: "greenfield — existing pom.xml/Main.java is an empty IDE template, not a real system"
    - topic: "player value"
      decision: "IT humor & recognizability + tactical gameplay; also a personal learning project for the author"
    - topic: "primary persona"
      decision: "people working in IT (developers, testers, PMs)"
    - topic: "game mode"
      decision: "solo vs computer first; multiplayer possibly later"
    - topic: "insight / differentiator"
      decision: "IT-only jokes + mechanics mirroring IT work + short browser session without install"
    - topic: "access model"
      decision: "login required (no guest); flat model, single player role; login method TBD"
    - topic: "MVP opponent"
      decision: "computer opponent in v1; human PvP deferred to v2 (resolves conflict with phase-3 'full PvP' wish)"
    - topic: "MVP scope vs timeline"
      decision: "keep 2 factions; commit to 6-week after-hours MVP, cost acknowledged"
    - topic: "decks & progress"
      decision: "v1: 2 factions with predefined decks, no saved progress; later: new cards/factions, neutral faction, card unlocks for playing"
    - topic: "loss resource"
      decision: "stress only in v1; tech debt dropped from v1"
    - topic: "win condition"
      decision: "opponent reaches stress limit OR cannot draw from empty deck"
    - topic: "login method"
      decision: "Google/GitHub external accounts"
    - topic: "balance baseline"
      decision: "stress limit 30; 30-card deck per faction"
  frs_drafted: 8
  quality_check_status: accepted
---

# Shape notes

## Seed idea

> gra karciana o IT, grana w przegladarce i ewentualnie w przyszlosci mobilna wersja

## Vision & Problem Statement

ItCardBrawl nie rozwiązuje konkretnego problemu — to gra karciana dla rozrywki. Osoba pracująca w IT wieczorem po pracy gra dziś w dowolną inną grę albo zajmuje się innym hobby; ItCardBrawl konkuruje o ten wolny czas.

Wgląd: ogólne gry karciane nie dają trzech rzeczy naraz — (1) żartów i sytuacji, które są zabawne tylko dla kogoś z branży IT, (2) mechaniki, która sama odzwierciedla realia pracy w IT (a nie tylko grafiki o IT), (3) krótkiej partii w przeglądarce bez instalacji. Gra ma łączyć humor i rozpoznawalność IT z taktyczną rozgrywką.

Kontekst autora: projekt jest również dla autora sposobem nauki budowania aplikacji.

## User & Persona

**Główna persona:** osoba pracująca w IT — developer, tester lub PM — która wieczorem po pracy szuka krótkiej rozrywki i od razu rozpoznaje branżowe żarty i sytuacje bez tłumaczenia.

Moment sięgnięcia po produkt: wieczór po pracy.

Tryb gry dla tej persony: najpierw solo przeciwko komputerowi; multiplayer z innymi ludźmi ewentualnie w przyszłości.

## Access Control

- Logowanie wymagane — bez konta nie da się grać (brak trybu gościa).
- Płaski model: jedna rola „gracz”; wszyscy zalogowani mają te same możliwości. Brak roli admina w aplikacji — karty i zasady autor zmienia poza grą.
- W pierwszej wersji konto służy do logowania; gracz gra gotowymi taliami frakcji, bez zapisywanego postępu. System progresu (odblokowywanie kart za granie) — później.
- Niezalogowany użytkownik wchodzący do gry jest kierowany do logowania / rejestracji.
- Metoda logowania: konto zewnętrzne Google lub GitHub (bez własnych haseł w grze).

## Success Criteria

Pierwszy przepływ MVP (zablokowany):

1. Gracz się loguje.
2. Wybiera jedną z 2 frakcji.
3. Rozpoczyna partię przeciwko przeciwnikowi komputerowemu.
4. Gracz i przeciwnik na zmianę zagrywają karty: postacie (np. Junior Dev, Senior) oraz wydarzenia/spelle (np. „Nagła zmiana założeń projektowych”).
5. Karty podnoszą poziom stresu przeciwnika (zamiast zadawania obrażeń życiu).
6. Kto osiągnie limit stresu albo nie może dobrać karty z pustej talii — przegrywa; gracz widzi wynik partii.

### Primary
- Zalogowany gracz rozgrywa pełną partię dowolną z 2 frakcji przeciwko przeciwnikowi komputerowemu, od wyboru frakcji do wyniku (wygrana/przegrana rozstrzygnięta przez limit stresu albo koniec kart w talii).

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
- FR-007: Gracz może w każdej chwili widzieć poziom stresu swój i przeciwnika. Priority: must-have
  > Socrates: Counter-argument considered: "ukryty stan przeciwnika dodałby napięcia."
  > Resolution: kept — przy grze taktycznej jawność stanu jest ważniejsza.
- FR-008: Gracz może zobaczyć wynik partii (wygrana/przegrana) i od razu zagrać ponownie. Priority: must-have
  > Socrates: Counter-argument considered: "sam wynik bez dalszej akcji sprawia, że gracz odchodzi (Secondary: gracz wraca)."
  > Resolution: modified — ekran wyniku oferuje „zagraj ponownie”.

## User Stories

### US-01: Gracz rozgrywa partię przeciwko komputerowi

- **Given** zalogowany gracz
- **When** wybiera jedną z 2 frakcji i rozpoczyna partię
- **Then** na zmianę z komputerem zagrywa karty postaci i wydarzeń, a partia kończy się, gdy któraś strona osiągnie limit stresu albo nie może dobrać karty z pustej talii; gracz widzi wynik

#### Acceptance Criteria
- Gracz widzi zagrania komputera i ich skutek (FR-006).
- Poziom stresu obu stron jest widoczny przez całą partię (FR-007).
- Ekran wyniku pozwala od razu zagrać ponownie (FR-008).

## Product framing

- `product_type: web-app` — gra w przeglądarce.
- `target_scale.users: small` — autor i garstka znajomych. (Sokrates 100×: reguła gry solo vs komputer się nie zmienia; skala ma znaczenie dopiero przy PvP.)
- `target_scale.qps / data_volume` — nie podano (przy skali `small` zakładane jako niskie; do potwierdzenia przy wyborze stacku).

## Timeline budget

- `mvp_weeks: 6`
- `hard_deadline: null` — brak twardego terminu.
- `after_hours_only: true` — praca po godzinach.

## Timeline acknowledgment

Acknowledged on 2026-11-16: 6-week MVP requires sustained dedication; user accepted.

## Business Logic

Wygrywa ten, kto doprowadzi stres przeciwnika do limitu albo sprawi, że przeciwnik nie będzie mógł dobrać karty, bo jego talia jest pusta.

Dane wejściowe reguły widoczne dla gracza: wybrana frakcja z gotową talią, karty postaci i wydarzeń zagrywane na zmianę przez gracza i przeciwnika komputerowego oraz bieżący poziom stresu obu stron. Stres zastępuje klasyczne „życie” — karty podnoszą stres przeciwnika.

Wynik: partia kończy się w chwili, gdy któraś strona osiągnie limit stresu albo musi dobrać kartę z pustej talii; ta strona przegrywa. Gracz spotyka regułę przez cały przebieg partii (stale widoczny stres obu stron, widoczne skutki zagrań) i na ekranie wyniku.

W v1 jedynym zasobem przegranej jest stres; dług techniczny nie jest częścią pierwszej wersji. Limit stresu wynosi 30, a talia każdej frakcji liczy 30 kart.

## Non-Functional Requirements

- Jedna partia trwa ≤ 15 minut.
- Gra działa w ostatnich dwóch głównych wersjach Chrome, Firefox, Edge i Safari na komputerze, bez instalacji.
- Dane konta i logowania gracza nie są dostępne dla osób trzecich.

## Non-Goals

- **Brak PvP z ludźmi i matchmakingu** — v1 to wyłącznie gra przeciwko komputerowi (ani przez sieć, ani na jednym ekranie); PvP to wersja 2.
- **Brak budowania talii, kolekcji kart i odblokowań** — tylko gotowe talie 2 frakcji; progres przez odblokowywanie kart przyjdzie później.
- **Brak wersji mobilnej i trybu offline** — v1 to gra w przeglądarce na komputerze, wymagająca internetu.
- **Brak mikropłatności, reklam i panelu admina** — karty i zasady autor zmienia poza aplikacją; brak monetyzacji.

## Open Questions

Brak otwartych pytań — rozstrzygnięte w kontroli krzyżowej 2026-11-16:
- Metoda logowania → Google/GitHub.
- Limit stresu → 30; talia frakcji → 30 kart.

## Quality cross-check

Status: accepted (2026-11-16). Access Control, Business Logic, Project artifacts, Timeline-cost ack, Non-Goals — present. Preserved behavior — n/a (greenfield).

## Forward: technical-roadmap

- Możliwa przyszła wersja mobilna (z pomysłu startowego) — nie jest częścią pierwszej wersji.
- Dług techniczny jako mechanika — wypadł z v1 (tylko stres); możliwy powrót później.
- Dodawanie z czasem nowych kart i nowych frakcji, w tym frakcji neutralnej — po pierwszej wersji.
- System progresu: odblokowywanie nowych kart za granie — po pierwszej wersji (v1 = gotowe talie).
- PvP z innymi ludźmi (docelowo „w pełni rozwinięta mechanika PvP”) — wersja 2; pierwsza wersja to gra przeciwko komputerowi.
