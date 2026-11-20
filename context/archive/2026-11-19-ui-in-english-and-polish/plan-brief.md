# Interfejs i treść gry po angielsku i po polsku — Plan Brief

> Full plan: `context/changes/ui-in-english-and-polish/plan.md`
> Research: `context/changes/ui-in-english-and-polish/research.md`

## What & Why

Gracz przełącza język aplikacji między polskim a angielskim w trakcie działania — bez przeładowania i bez osobnych buildów. Przełączenie obejmuje cały interfejs oraz treść gry z API (nazwy i opisy frakcji, nazwy i teksty kart), żeby znajomi z IT spoza Polski mogli grać od pierwszej wizyty.

## Starting Point

Dziś wszystko jest po polsku i zaszyte na sztywno: ok. 36 tekstów w pięciu komponentach Angulara i jednojęzyczny `factions.json`. Nie ma żadnej i18n, preferencji użytkownika ani bazy danych; partia trzyma migawkę obiektów `Card`/`Faction` z katalogu.

## Desired End State

Pierwsza wizyta dostaje język przeglądarki; przełącznik PL | EN w nagłówku każdego ekranu (także logowania) natychmiast zmienia interfejs, `<html lang>` i treść z serwera — także w trakcie partii, bez utraty ręki czy wybranej frakcji. Wybór jest pamiętany w przeglądarce. Aplikacja nie wystartuje z niekompletnym tłumaczeniem katalogu.

## Key Decisions Made

| Decision | Choice | Why (1 sentence) | Source |
| --- | --- | --- | --- |
| Zakres | UI + treść gry z API | Gra ma być w pełni grywalna po angielsku, nie tylko jej menu. | Plan (wcześniej w sesji) |
| Domyślny język | Z `navigator.language` (`pl*` → PL, reszta → EN) | Obcojęzyczni znajomi dostają angielski bez klikania, Polacy bez zmian. | Plan |
| Pamięć wyboru | `localStorage` | Brak bazy; przeżywa odświeżenie i przekierowanie OAuth. | Plan |
| Przełącznik | Nagłówek app shell, każdy ekran | Można zmienić język przed logowaniem; jeden komponent. | Plan |
| Angielska treść kart | Placeholdery agenta | Zmiana kompletna od razu; S-06 i tak podmienia treść w obu językach. | Plan |
| Nazwy kart | Każde pole w obu językach (żargon może się powtarzać) | Jedna reguła, prosta walidacja, S-06 może lokalizować dowolną nazwę. | Plan |
| UI i18n | Własny `LanguageService` + typowane słowniki `pl`/`en` | ~36 tekstów, zero zależności, kompilator pilnuje kompletu. | Plan |
| Kształt danych | `{ "pl": …, "en": … }` w jednym `factions.json` | Tłumaczenia razem z kartą; id, koszty, talie z jednego źródła. | Plan |
| Transport języka | `Accept-Language` z interceptora → `localeResolver` (pl, en; domyślnie pl) | Standard HTTP, bez zmian URL-i i wywołań serwisów. | Plan |
| Brak tłumaczenia | Fail-fast przy starcie | Jak reszta katalogu — półprzetłumaczona talia nie trafi do graczy. | Plan |
| Dokumentacja | Notatka w S-06 roadmapy, PRD bez zmian | Kolejny slice treści nie przeoczy wymogu dwóch języków. | Plan |
| Partia w trakcie | `Card`/`Faction` niosą oba języki, widok wybiera przy renderze | Migawka w partii przestaje blokować przełączenie. | Research |

## Scope

**In scope:**
- Dwujęzyczny `factions.json` z angielskimi placeholderami i walidacją kompletu
- `/api/factions` i widok partii w języku z `Accept-Language`
- `LanguageService`, słowniki PL/EN, interceptor, przełącznik w nagłówku
- Przepięcie sign-in, lobby, new-match, match-table; ponowne pobranie treści po zmianie języka
- Testy w obu językach; notatka w S-06 roadmapy

**Out of scope:**
- Preferencja języka na koncie/serwerze, inne języki, pluralizacja/ICU
- Tłumaczenie wewnętrznych komunikatów backendu i nazwy marki
- Docelowa treść kart (S-06), aktualizacja PRD, nowe endpointy

## Architecture / Approach

SPA: `LanguageService` (signal + `localStorage` + `<html lang>`) → `texts()` w szablonach; interceptor dokleja `Accept-Language` do `/api/**`. Backend: bean `localeResolver` → `Locale` w kontrolerach → `Language` → `LocalizedText.in(language)` w `FactionSummary` i `MatchView`. Kształt JSON odpowiedzi bez zmian.

## Phases at a Glance

| Phase | What it delivers | Key risk |
| --- | --- | --- |
| 1. Dwujęzyczny katalog i API | `factions.json` PL/EN, walidacja, `Accept-Language` → treść w języku | Zmiana kształtu rekordów dotyka wielu fixtur testowych |
| 2. Warstwa tłumaczeń i przełącznik | Serwis języka, słowniki, interceptor, PL \| EN w nagłówku | jsdom ma `en-US` — specy muszą jawnie ustawiać język |
| 3. Tłumaczenie ekranów | Wszystkie ekrany na słownikach, refetch treści po zmianie | Podwójne żądanie przy wejściu, jeśli efekt języka źle zsynchronizowany |

**Prerequisites:** zarchiwizowany match-setup-table (stół i API partii istnieją).
**Estimated effort:** ~2–3 sesje w 3 fazach.

## Open Risks & Assumptions

- Angielskie teksty kart to placeholdery agenta — humor może wymagać korekty autora (S-06 i tak je podmienia).
- Zakładamy, że nikt nie potrzebuje języka na poziomie konta (różne urządzenia mogą startować z innym językiem).

## Success Criteria (Summary)

- Gracz z angielską przeglądarką gra od logowania do stołu w całości po angielsku.
- Przełączenie języka w dowolnym momencie (także w trakcie partii) zmienia wszystkie teksty bez utraty stanu.
- Katalog z brakującym tłumaczeniem zatrzymuje start aplikacji z czytelnym komunikatem.
