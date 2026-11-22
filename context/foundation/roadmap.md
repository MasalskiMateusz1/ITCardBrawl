---
project: ItCardBrawl
version: 1
status: draft                    # draft | active | locked
created: 2026-11-16
updated: 2026-11-21
prd_version: 1
main_goal: learn
top_blocker: skills
milestone_id: first-playable-match
milestone_seq: 1
milestone_status: open           # open | done
---

# Roadmap: ItCardBrawl

> Derived from `context/foundation/prd.md` (v1) + `context/foundation/tech-stack.md` + auto-researched codebase baseline.
> Edit-in-place; the next milestone continues this file. Archive only when an independent roadmap replaces it.
> Slices below are listed in dependency order. The "At a glance" table is the index.

## Milestone

**M-1: Pierwsza grywalna partia** — Status: open

- **Intent:** Zalogowany gracz rozgrywa w przeglądarce pełną partię jedną z 2 frakcji przeciwko sensownie grającemu komputerowi — od wyboru frakcji do wyniku — na wdrożonej wersji gry. Przy okazji autor przechodzi przez cały stos (SPA, API, logowanie zewnętrzne, wdrożenie), wprowadzając po jednej nowej technologii na raz.
- **Source materials:** `context/foundation/prd.md` (v1), z kontekstem z `context/foundation/tech-stack.md`.
- **Done when:** every F-NN and S-NN below is `done`.
- **Scope anchors:** FR-001 – FR-008, US-01, Non-Functional Requirements, Guardrails, Access Control.

## Vision recap

ItCardBrawl to przeglądarkowa gra karciana dla ludzi z IT na wieczór po pracy: łączy branżowy humor z mechaniką odzwierciedlającą realia pracy w IT — karty postaci (Junior Dev, Senior) i wydarzeń podnoszą **stres** przeciwnika zamiast zadawać obrażenia. Przegrywa ten, kto osiągnie limit stresu (30) albo musi dobrać kartę z pustej talii (30 kart). Projekt jest również dla autora sposobem nauki budowania aplikacji — stąd cel sekwencjonowania `learn`.

## North star

**S-03: Gracz rozgrywa pełną partię kartami postaci przeciwko komputerowi aż do wyniku** — to dosłownie główne kryterium sukcesu z PRD (US-01); dopiero gdy ta pętla działa, karty wydarzeń, mądrzejszy przeciwnik i zbalansowane talie mają sens.

> „North star” (gwiazda przewodnia) oznacza tu najmniejszy wycinek od początku do końca, którego dostarczenie dowodzi, że gra działa jako gra — jest umieszczony tak wcześnie, jak pozwalają jego zależności, bo wszystko inne ma znaczenie tylko wtedy, gdy on działa.

## At a glance

| ID   | Change ID                  | Outcome (user can …)                                                       | Prerequisites | PRD refs                                  | Status   |
| ---- | -------------------------- | -------------------------------------------------------------------------- | ------------- | ----------------------------------------- | -------- |
| F-01 | spa-api-walking-skeleton   | (foundation) aplikacja w przeglądarce rozmawia z API przez jedno źródło    | —             | NFR (przeglądarki), Guardrails            | done     |
| F-02 | deploy-pipeline-skeleton   | (foundation) każdy merge do main jest automatycznie wdrażany na serwer     | F-01          | NFR (bez instalacji), Guardrails          | done     |
| S-01 | external-login-gate        | gracz loguje się zewnętrznym kontem; niezalogowany trafia na logowanie     | F-01          | FR-001, Access Control, NFR (dane konta)  | done     |
| S-02 | match-setup-table          | gracz wybiera frakcję, rozpoczyna partię i widzi stół ze stresem obu stron | S-01          | FR-002, FR-003, FR-007, US-01             | done     |
| S-03 | first-full-match           | gracz rozgrywa pełną partię postaciami przeciw komputerowi aż do wyniku    | S-02          | FR-004, FR-006, FR-008, US-01             | done     |
| S-04 | event-cards                | gracz i komputer zagrywają karty wydarzeń o przewidywalnych efektach       | S-03          | FR-005, US-01                             | done     |
| S-05 | sensible-computer-opponent | gracz mierzy się z komputerem, który gra sensownie i pokazuje powód ruchu  | S-03          | FR-003, FR-006, US-01, Guardrails         | done     |
| S-06 | full-faction-decks-balance | gracz gra jedną z 2 pełnych, zbalansowanych talii po 30 kart z humorem IT  | S-04, S-05    | FR-002, US-01, NFR (≤ 15 min), Guardrails | in-progress |

## Streams

Navigation aid — groups items that share a Prerequisites chain. Canonical ordering still lives in the dependency graph below; this table is the proposed reading order across parallel tracks.

| Stream | Theme                 | Chain                                  | Note                                                                                       |
| ------ | --------------------- | -------------------------------------- | ------------------------------------------------------------------------------------------ |
| A      | Szkielet i logowanie  | `F-01` → `S-01`                        | Pierwsze przejście przez cały stos — jedna nowa technologia na krok (cel `learn`).         |
| B      | Rdzeń gry             | `S-02` → `S-03` → `S-04` → `S-06`      | Dołącza do strumienia A przy `S-01`; zawiera gwiazdę przewodnią `S-03`.                    |
| C      | Przeciwnik komputerowy| `S-05`                                 | Odgałęzia się od B przy `S-03`; równolegle z `S-04`; łączy się z B ponownie przy `S-06`.   |
| D      | Wdrożenie             | `F-02`                                 | Dołącza do A przy `F-01`; może biec równolegle z całym rdzeniem gry.                       |

## Baseline

What's already in place in the codebase as of `2026-11-16` (auto-researched + user-confirmed).
Foundations below assume these are present and do NOT re-scaffold them.

- **Frontend:** absent — SPA zaplanowane (Angular w `frontend/`, decyzja 2026-11-16), brak kodu.
- **Backend / API:** partial — szkielet Spring Boot 4.1.2 (webmvc, Java 21) w `src/main/java/com/example/itcardbrawl/ItcardbrawlApplication.java`; brak kontrolerów i kodu domeny.
- **Data:** absent — brak sterownika bazy, ORM, migracji; v1 nie zapisuje postępu (PRD §Access Control).
- **Auth:** partial — zależności security + oauth2-client w `pom.xml`; brak rejestracji dostawcy i konfiguracji łańcucha zabezpieczeń.
- **Deploy / infra:** absent — brak Dockerfile i workflow CI (tech-stack.md: self-host + GitHub Actions, auto-deploy po merge).
- **Observability:** absent — brak narzędzi logowania/śledzenia błędów poza domyślnymi.

## Foundations

### F-01: Szkielet SPA ↔ API

- **Outcome:** (foundation) aplikacja przeglądarkowa uruchamia się lokalnie i wyświetla odpowiedź z API, przy czym przeglądarka widzi jedno źródło (wywołania API przechodzą przez proxy), zgodnie z kształtem zapisanym w tech-stack.md.
- **Change ID:** spa-api-walking-skeleton
- **PRD refs:** NFR (działa w czterech głównych przeglądarkach bez instalacji), Guardrails (gra działa w przeglądarce bez instalacji)
- **Unlocks:** S-01 (logowanie potrzebuje ekranu i API w jednym źródle), F-02 (jest co budować i wdrażać)
- **Prerequisites:** —
- **Parallel with:** —
- **Blockers:** —
- **Unknowns:**
  - — (rozstrzygnięte 2026-11-16: Angular, w folderze `frontend/` tego repo — zob. tech-stack.md §Post-bootstrap notes)
- **Risk:** Pierwsza nowa technologia (SPA) wprowadzana samodzielnie, bez logowania i gry — zgodnie z blokadą `skills`; zbyt rozbudowany szkielet (routing, stan, komponenty „na zapas”) to dryf — tylko tyle, ile potrzebuje S-01.
- **Status:** done

### F-02: Szkielet wdrożenia

- **Outcome:** (foundation) API i SPA są budowane w kontenerze i automatycznie wdrażane na serwer po każdym merge do main; wdrożona wersja otwiera się w przeglądarce.
- **Change ID:** deploy-pipeline-skeleton
- **PRD refs:** NFR (bez instalacji, cztery przeglądarki), Guardrails (gra działa w przeglądarce bez instalacji)
- **Unlocks:** ścieżka weryfikacji „każdy kolejny wycinek sprawdzalny na wdrożonej wersji”; ścieżka weryfikacji Kryteriów sukcesu §Secondary (znajomi z IT grają we wdrożoną grę) dla S-03 i S-06; produkcyjny adres zwrotny logowania dla S-01
- **Prerequisites:** F-01
- **Parallel with:** S-01, S-02, S-03, S-04, S-05, S-06
- **Blockers:** —
- **Unknowns:**
  - Na jakim serwerze / hostingu działa self-host i pod jaką domeną? — Owner: user. Block: yes.
- **Risk:** Druga nowa technologia (kontener + CI) celowo osobno od logowania; wdrożenie wcześnie, bo przy celu `learn` i auto-deploy z tech-stack.md każdy kolejny wycinek uczy się na prawdziwym środowisku.
- **Hand-off z F-01:** `SecurityConfig` ma `anyRequest().authenticated()` dla wszystkich ścieżek — po spakowaniu SPA do jara trzeba dodać `permitAll` dla zasobów statycznych SPA (`/`, `/index.html`, bundle JS/CSS, `/favicon.ico`), inaczej dostaną 401 (impl-review F1).
- **Status:** done

## Slices

### S-01: Logowanie zewnętrznym kontem

- **Outcome:** gracz loguje się zewnętrznym kontem i widzi lobby ze swoją nazwą; niezalogowany użytkownik wchodzący do gry jest kierowany do logowania.
- **Change ID:** external-login-gate
- **PRD refs:** FR-001, Access Control, NFR (dane konta i logowania niedostępne dla osób trzecich), Guardrails (dane logowania bezpieczne)
- **Prerequisites:** F-01
- **Parallel with:** F-02
- **Blockers:** —
- **Unknowns:**
  - Który dostawca zewnętrznego konta (np. konto, które persona IT na pewno ma) i czy jeden, czy kilku? — Owner: user. Block: yes.
- **Risk:** Najtrudniejsza technicznie część stosu (przekierowania OAuth, sesja, proxy SPA) — wprowadzana zaraz po szkielecie, zanim pojawi się logika gry, żeby błędy logowania nie mieszały się z błędami zasad.
- **Status:** done

### S-02: Wybór frakcji i stół partii

- **Outcome:** zalogowany gracz wybiera jedną z 2 frakcji, rozpoczyna partię przeciwko komputerowi i widzi stół: swoją rękę, liczbę kart w taliach oraz stres obu stron (0/30).
- **Change ID:** match-setup-table
- **PRD refs:** FR-002, FR-003, FR-007, US-01
- **Prerequisites:** S-01, robocze talie startowe obu frakcji (mogą być tymczasowe — pełne talie w S-06)
- **Parallel with:** F-02
- **Blockers:** —
- **Unknowns:**
  - Podstawowe zasady tury: ile kart na start ręki, ile dobiera się na turę, kto zaczyna, czy zagranie karty kosztuje jakiś zasób? (PRD §Business Logic tego nie określa.) — Owner: user. Block: yes.
- **Risk:** Wydzielony z pętli gry, żeby model partii i stołu (stan widoczny cały czas — FR-007) powstał przed zasadami zagrań; bez zasad tury nie da się go zaplanować.
- **Status:** done

### S-03: Pełna partia postaciami (north star)

- **Outcome:** gracz zagrywa karty postaci na stół, komputer rozgrywa własne tury, gracz widzi każde zagranie komputera i jego skutek, stres rośnie, a partia kończy się przy stresie 30 albo pustej talii — gracz widzi wynik i może od razu zagrać ponownie.
- **Change ID:** first-full-match
- **PRD refs:** FR-004, FR-006, FR-008, US-01
- **Prerequisites:** S-02
- **Parallel with:** F-02
- **Blockers:** —
- **Unknowns:**
  - Zasady postaci na stole: kiedy postać podnosi stres (przy zagraniu czy co turę), czy postacie wchodzą ze sobą w interakcje (blokują, atakują), czy i kiedy schodzą ze stołu, czy jest limit miejsc? — Owner: user. Block: yes.
- **Risk:** Rdzeń taktyki gry (PRD: „postacie na stole to rdzeń taktyki”); komputer gra tu tylko poprawnymi ruchami — „sensowna” strategia jest osobnym ryzykiem w S-05, żeby nie blokowała dowodu, że pętla partii działa.
- **Status:** done

### S-04: Karty wydarzeń

- **Outcome:** gracz i komputer mogą zagrywać karty wydarzeń (np. „Nagła zmiana założeń projektowych”), których efekt jest opisany na karcie i przewidywalny przed zagraniem.
- **Change ID:** event-cards
- **PRD refs:** FR-005, US-01
- **Prerequisites:** S-03
- **Parallel with:** F-02, S-05
- **Blockers:** —
- **Unknowns:**
  - Katalog efektów wydarzeń (jakie rodzaje efektów istnieją w v1 — np. stres, dobieranie, wpływ na postacie)? — Owner: user. Block: no.
- **Risk:** Wydarzenia rozszerzają zasady z S-03, więc idą po nim; ryzyko to efekty zbyt losowe — PRD wymaga przewidywalności.
- **Status:** done

### S-05: Sensowny przeciwnik komputerowy

- **Outcome:** gracz mierzy się z komputerem, który wybiera zagrania na podstawie oceny sytuacji na stole (nie losowo), a przy każdym ruchu widzi krótki powód zagrania.
- **Change ID:** sensible-computer-opponent
- **PRD refs:** FR-003, FR-006, US-01, Guardrails (przeciwnik gra sensownie — nie losowo)
- **Prerequisites:** S-03
- **Parallel with:** F-02, S-04
- **Blockers:** —
- **Unknowns:**
  - Po czym poznamy, że komputer gra „sensownie” (kryterium akceptacji, np. wyraźnie częściej wygrywa z graczem grającym losowo)? — Owner: user. Block: no.
- **Risk:** Równolegle z S-04 — jeśli S-05 skończy się przed S-04, strategia komputera musi zostać rozszerzona o karty wydarzeń (wychwyci to S-06 przy balansowaniu).
- **Status:** done

### S-06: Pełne talie frakcji i balans

- **Outcome:** gracz wybiera jedną z 2 frakcji z pełną talią 30 kart z żartami rozpoznawalnymi dla ludzi z IT; obie frakcje mają realną szansę na zwycięstwo, a partia mieści się w ≤ 15 minutach.
- **Change ID:** full-faction-decks-balance
- **PRD refs:** FR-002, US-01, NFR (partia ≤ 15 minut), Guardrails (żadna frakcja nie wygrywa zawsze)
- **Prerequisites:** S-04, S-05
- **Parallel with:** F-02
- **Blockers:** —
- **Unknowns:**
  - Treść kart obu frakcji (nazwy, żarty, wartości) — Owner: user. Block: no. Nazwy, opisy i teksty kart i frakcji muszą powstać po polsku i po angielsku (`factions.json` trzyma każde pole jako `{ "pl", "en" }`, a walidacja katalogu wymaga obu języków — zmiana ui-in-english-and-polish).
  - Jak sprawdzamy balans i czas partii przed udostępnieniem znajomym? — Owner: user. Block: no.
- **Risk:** Balans ma sens dopiero przy kompletnych zasadach (wydarzenia) i sensownym przeciwniku, więc idzie na końcu; treść kart to praca autorska, której agent nie wymyśli za autora.
- **Status:** in-progress

## Backlog Handoff

| Roadmap ID | Change ID                  | Suggested issue title                                         | Ready for `/10x-plan` | Notes |
| ---------- | -------------------------- | ------------------------------------------------------------- | --------------------- | ----- |
| F-01       | spa-api-walking-skeleton   | Szkielet SPA rozmawiającego z API przez jedno źródło          | yes                   | Run `/10x-plan spa-api-walking-skeleton` (Angular, `frontend/`) |
| F-02       | deploy-pipeline-skeleton   | Automatyczne wdrożenie po merge do main (self-host)           | no                    | Czeka na F-01 i wybór serwera/domeny |
| S-01       | external-login-gate        | Logowanie zewnętrznym kontem i blokada gry dla niezalogowanych| no                    | Czeka na F-01 i wybór dostawcy logowania |
| S-02       | match-setup-table          | Wybór frakcji, start partii i stół ze stresem obu stron       | no                    | Czeka na S-01 i podstawowe zasady tury |
| S-03       | first-full-match           | Pełna partia postaciami przeciw komputerowi aż do wyniku      | no                    | North star; czeka na S-02 i zasady postaci na stole |
| S-04       | event-cards                | Karty wydarzeń o przewidywalnych efektach                     | no                    | Czeka na S-03 |
| S-05       | sensible-computer-opponent | Przeciwnik komputerowy grający sensownie, z powodem ruchu     | no                    | Czeka na S-03 |
| S-06       | full-faction-decks-balance | Pełne talie 2 frakcji (30 kart) i balans                      | no                    | Czeka na S-04 i S-05 |

This table is the clean handoff to Jira/Linear or any MCP-backed backlog. Include one row for every `F-NN` and `S-NN`. It should be compact enough to copy into issues, but it must not duplicate the detailed roadmap body.

## Open Roadmap Questions

1. **Jaki jest oczekiwany ruch (`target_scale.qps`)?** — Nie podano w shape-notes (przy skali `small` zakładany jako niski). Owner: user. Block: no (do potwierdzenia przy F-02).
2. **Jaka jest oczekiwana ilość danych (`target_scale.data_volume`)?** — Nie podano w shape-notes (przy skali `small` zakładana jako niska). Owner: user. Block: no.
3. **Spisanie pełnych zasad gry (tura, zasób zagrania, postacie na stole, efekty wydarzeń) jako uzupełnienie PRD §Business Logic** — PRD określa warunki wygranej, ale nie przebieg tury. Owner: user. Block: S-02, S-03 (oraz kierunkowo S-04).

## Parked

- **PvP z ludźmi i matchmaking** — Why parked: PRD §Non-Goals (wersja 2); shape-notes Forward.
- **Budowanie talii, kolekcja kart i odblokowania / system progresu** — Why parked: PRD §Non-Goals; shape-notes Forward (po pierwszej wersji).
- **Wersja mobilna i tryb offline** — Why parked: PRD §Non-Goals; shape-notes Forward.
- **Mikropłatności, reklamy i panel admina** — Why parked: PRD §Non-Goals (karty i zasady autor zmienia poza aplikacją).
- **Dług techniczny jako mechanika** — Why parked: PRD §Business Logic (w v1 jedynym zasobem przegranej jest stres); shape-notes Forward.
- **Nowe karty i frakcje, w tym frakcja neutralna** — Why parked: shape-notes Forward (po pierwszej wersji).
- **Trwałe przechowywanie danych gracza** — Why parked: PRD §Access Control (v1 bez zapisywanego postępu); Baseline: data absent i niepotrzebne w M-1.
- **Obserwowalność produkcyjna (śledzenie błędów, metryki)** — Why parked: brak NFR, który by jej wymagał; cel `learn` + blokada `skills` — wrócić po F-02, gdy wdrożona gra trafi do znajomych.

## Milestone History

## Done

- **F-01: (foundation) aplikacja przeglądarkowa uruchamia się lokalnie i wyświetla odpowiedź z API, przy czym przeglądarka widzi jedno źródło (wywołania API przechodzą przez proxy), zgodnie z kształtem zapisanym w tech-stack.md.** — Archived 2026-11-18 → `context/archive/2026-11-16-spa-api-walking-skeleton/`. Lesson: —.
- **S-01: gracz loguje się zewnętrznym kontem i widzi lobby ze swoją nazwą; niezalogowany użytkownik wchodzący do gry jest kierowany do logowania.** — Archived 2026-11-18 → `context/archive/2026-11-18-external-login-gate/`. Lesson: —.
- **F-02: (foundation) API i SPA są budowane w kontenerze i automatycznie wdrażane na serwer po każdym merge do main; wdrożona wersja otwiera się w przeglądarce.** — Archived 2026-11-19 → `context/archive/2026-11-19-deploy-pipeline-skeleton/`. Lesson: —.
- **S-02: zalogowany gracz wybiera jedną z 2 frakcji, rozpoczyna partię przeciwko komputerowi i widzi stół: swoją rękę, liczbę kart w taliach oraz stres obu stron (0/30).** — Archived 2026-11-19 → `context/archive/2026-11-19-match-setup-table/`. Lesson: —.
- **S-03: gracz zagrywa karty postaci na stół, komputer rozgrywa własne tury, gracz widzi każde zagranie komputera i jego skutek, stres rośnie, a partia kończy się przy stresie 30 albo pustej talii — gracz widzi wynik i może od razu zagrać ponownie.** — Archived 2026-11-20 → `context/archive/2026-11-20-first-full-match/`. Lesson: —.
- **S-04: gracz i komputer mogą zagrywać karty wydarzeń (np. „Nagła zmiana założeń projektowych”), których efekt jest opisany na karcie i przewidywalny przed zagraniem.** — Archived 2026-11-21 → `context/archive/2026-11-20-event-cards/`. Lesson: —.
- **S-05: gracz mierzy się z komputerem, który wybiera zagrania na podstawie oceny sytuacji na stole (nie losowo), a przy każdym ruchu widzi krótki powód zagrania.** — Archived 2026-11-21 → `context/archive/2026-11-21-sensible-computer-opponent/`. Lesson: —.
