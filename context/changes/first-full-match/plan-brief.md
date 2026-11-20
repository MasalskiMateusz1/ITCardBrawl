# Pełna partia postaciami przeciw komputerowi — Plan Brief

> Full plan: `context/changes/first-full-match/plan.md`
> Research: `context/changes/first-full-match/research.md`

## What & Why

S-03, north star roadmapy (US-01): gracz rozgrywa pełną partię przeciwko komputerowi, od wyboru frakcji do wyniku. Zagrywa postacie, atakuje nimi postacie przeciwnika albo jego stres, a komputer odpowiada własnymi turami. Dopiero gdy ta pętla działa, mają sens efekty wydarzeń (S-04), mądrzejszy komputer (S-05) i balans talii (S-06).

## Starting Point

S-02 zostawił rozdany, tylko do odczytu stół. Niemutowalny model `Match`/`MatchSide`, stałe zasad tury, których nikt nie wykonuje, `MatchStore` z samym `save` (follow-up F3 wymaga atomowego `update`) i karty bez ataku, HP i zdolności. Partia z zaczynającym komputerem stoi w miejscu.

## Desired End State

W `/match` gracz zagrywa karty, atakuje i kończy turę. Komputer od razu rozgrywa swoją turę losowymi poprawnymi ruchami, a log w języku interfejsu pokazuje każdy krok i jego skutek. Partia kończy się przy stresie ≥ 30 albo przy dobieraniu z pustej talii. Wynik linkuje do wyboru frakcji.

## Key Decisions Made

| Decision | Choice | Why (1 sentence) | Source |
| --- | --- | --- | --- |
| Postacie | Atak + HP, część ze zdolnością, maks. 5 na stronę | Postacie na stole to rdzeń taktyki (PRD). | Research |
| Walka | Atak w postać (obrażenia wzajemne) albo bezpośrednio w stres; HP ≤ 0 → cmentarz | Daje wybór między walką o stół a presją na stres. | Research + Plan |
| Gotowość | Atak od następnej tury, raz na turę (Szarża atakuje od razu) | Obrońca ma turę na reakcję. | Plan |
| Obrażenia | Czyszczone na koniec każdej tury | Decyzja użytkownika. | Research |
| Zdolności | Po zagraniu +N stresu, Po zagraniu −N własnego stresu, Ochrona, Szarża, Nadgodziny | Mały zestaw sprawdza mechanizm zdolności end-to-end. | Plan |
| Wartości kart | Szkic agenta (atak/HP ~ koszt) zatwierdzony przez użytkownika | Odblokowuje grywalną pętlę; balans w S-06. | Plan |
| Wydarzenia | Koszt + czas trwania; `0` → od razu cmentarz; zagrywalne bez efektu do S-04 | Ręka się nie zapycha, model jest gotowy na efekty. | Research + Plan |
| Limit ręki | 10; nadmiarowa dobrana karta spalona na cmentarz | Decyzja użytkownika. | Plan |
| Stres | Rzeczywista wartość bez sufitu (np. 33/30); koniec przy ≥ 30 | Decyzja użytkownika. | Plan |
| Koniec partii | Stres ≥ 30 natychmiast albo pusta talia przy dobieraniu na początku tury | Reguła PRD, doprecyzowana przez użytkownika. | Research |
| Komputer | Losowy poprawny ruch, powtarzany, aż brak ruchów; potem sam kończy turę | „Na razie losowo”; sensowność w S-05. | Research + Plan |
| Tura komputera | Cała wewnątrz żądania „Zakończ turę”, w jednej atomowej aktualizacji | Brak stanów pośrednich i podwójnych tur. | Plan |
| Log | Cała partia; zdania pl/en składane w backendzie | Decyzja użytkownika. | Research + Plan |
| Nieaktualne żądanie | Ruch niesie `matchId` + `turn`; niezgodność → 409 | Podwójny klik nie kończy następnej tury gracza. | Plan |
| Zagraj ponownie | Powrót do `/new-match` | Decyzja użytkownika. | Research |
| Odtwarzanie tury komputera | Krok na każdy ruch komputera, co ~700 ms; przyciski nieaktywne do końca (Faza 6) | Natychmiastowa tura pozwalała podwójnemu klikowi zakończyć kolejną turę gracza (nieudane 5.8). | Plan (Faza 6) |
| Kształt kroku | `replay: [{turn, status, result, player, opponent, logSize}]`; log tylko raz, w stanie końcowym | Mała odpowiedź, a `GET /current` zachowuje kontrakt z `replay: []`. | Plan (Faza 6) |
| Pomijanie odtwarzania | Brak przycisku „Pomiń” | Decyzja użytkownika. | Plan (Faza 6) |
| Zmiana języka w trakcie odtwarzania | Odtwarzanie trwa, odświeżenie czeka do końca | Decyzja użytkownika. | Plan (Faza 6) |
| Odtwarzanie pierwszej tury komputera | Jednorazowe przekazanie odpowiedzi startu przez `GameService` | Bez stanu na serwerze; po przeładowaniu widać tylko stan końcowy. | Plan (Faza 6) |
| Wskaźnik | Etykieta „Ruch przeciwnika…” w pasku tury | Nieaktywne przyciski bez powodu wyglądają na zawieszenie. | Plan (Faza 6) |

## Scope

**In scope:**
- Statystyki i zdolności kart w `factions.json` z walidacją.
- Silnik reguł (zagranie, atak, koniec i początek tury, zdolności, limity, koniec partii, log).
- Losowy komputer, atomowy `MatchStore.update` (F3), `MatchService`.
- 3 endpointy ruchów, rozszerzony `MatchView`, log pl/en.
- Grywalny stół, log i wynik we froncie.
- Odtwarzanie tury komputera krok po kroku (Faza 6).

**Out of scope:**
- Efekty wydarzeń (S-04).
- Strategia komputera i powód ruchu (S-05).
- Docelowy balans i treść kart (S-06).
- Trwały zapis partii, animacje, pomijanie odtwarzania, sufit stresu.

## Architecture / Approach

`MatchEngine` to czysta funkcja `(Match, Side, Move) → Match`, która rzuca `IllegalMoveException` dla złych ruchów. `RandomOpponent` losuje z `engine.legalMoves`. `MatchService` wykonuje ruch gracza, a po „Zakończ turę” także turę komputera, wewnątrz `MatchStore.update` (`computeIfPresent`; wyjątek oznacza brak zmian). `MatchController` mapuje wyjątki na 400 i 409. `MatchView` + `MatchLogFormatter` zamieniają strukturalne zdarzenia na zdania w języku żądania. SPA tylko wyświetla stan i wysyła ruchy.

## Phases at a Glance

| Phase | What it delivers | Key risk |
| --- | --- | --- |
| 1. Statystyki i zdolności kart | Atak/HP/zdolność/czas trwania w katalogu z walidacją | Rekord `Card` zmienia się w wielu testach |
| 2. Silnik reguł | Pełne zasady tury i walki z logiem, w testach jednostkowych | Kolejność kroków końca tury i natychmiastowy koniec partii |
| 3. Komputer i atomowy magazyn | Losowy przeciwnik, `update` (F3), `MatchService` | Pętla komputera bez końca (bezpiecznik 100 ruchów) |
| 4. API ruchów i widok | 3 endpointy, 400/404/409, log pl/en | Testy MockMvc zależne od losowej ręki |
| 5. Pętla gry we froncie | Grywalny stół, log, wynik | Dużo nowych stanów UI (wybór atakującego, Ochrona, pending) |
| 6. Odtwarzanie tury komputera | Kroki tury komputera z backendu, odtwarzane co ~700 ms; naprawa podwójnego kliku | Zegary i odświeżanie przy zmianie języka w komponencie stołu |

**Prerequisites:** S-02 zarchiwizowany (spełnione); działające logowanie OAuth lokalnie do testu ręcznego (`.env.properties`).
**Estimated effort:** ~5–6 sesji, 6 faz (Fazy 2 i 5 największe; Faza 6 ~1 sesja).

## Open Risks & Assumptions

- Balans szkicowych wartości jest zgadywany. Partia może trwać dłużej niż 15 min albo być jednostronna; korekta w S-06.
- Losowy komputer może wyglądać słabo, ale to świadome założenie do S-05.
- Wydarzenia bez efektu mogą mylić graczy. Log i opis karty pokazują tylko koszt i czas trwania.
- Log całej partii rośnie w każdej odpowiedzi, co jest akceptowalne w skali `small`.
- Bez pomijania tura komputera trwa kilka sekund (~700 ms × liczba ruchów). To może wydłużyć partię względem celu 15 min; obserwujemy to przy teście ręcznym.

## Success Criteria (Summary)

- Zalogowany gracz rozgrywa pełną partię do wyniku i wraca do wyboru frakcji.
- Każdy ruch komputera i jego skutek dla stresu jest widoczny w logu po polsku i po angielsku.
- Nieprawidłowy, podwójny albo nieaktualny ruch nie psuje stanu partii.
