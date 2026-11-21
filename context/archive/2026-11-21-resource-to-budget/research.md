---
date: 2026-11-21T17:19:38+01:00
researcher: Claude (claude-opus-5-5)
git_commit: ffe2d90
branch: main
repository: ITCardBrawl
topic: "Zmiana nazwy zasobu na zagrywanie kart (resource / „Zasób”) na budżet we wszystkich warstwach"
tags: [research, match-engine, match-view, match-table, i18n, factions-json, rename]
status: complete
last_updated: 2026-11-21
last_updated_by: Claude (claude-opus-5-5)
---

# Research: zasób → budżet

**Date**: 2026-11-21T17:19:38+01:00
**Researcher**: Claude (claude-opus-5-5)
**Git Commit**: ffe2d90
**Branch**: main
**Repository**: ITCardBrawl

## Research Question

Gdzie w kodzie, danych kart, kontrakcie JSON, tekstach, SPA, testach i dokumentach występuje zasób na zagrywanie kart (`resource`, „Zasób”), żeby zaplanować pełną zmianę nazwy na „budżet” (`budget`) analogicznie do `stress-to-hp` — bez zmiany mechaniki (`context/changes/resource-to-budget/change.md:12`).

## Summary

- Stan to dwie liczby na stronę: `MatchSide.resourceCurrent`, `resourceMax` (`src/main/java/com/example/itcardbrawl/game/MatchSide.java:10`), z limitem `MatchRules.RESOURCE_CAP = 10` (`MatchRules.java:20`). Start: zaczynający 1/1, druga strona 0/0 (`MatchFactory.java:65-66`).
- Silnik używa zasobu w 7 sprawdzonych miejscach `MatchEngine.java`: `canPlay` (`:83-84`), filtr efektów postaci (`:115`), efekt `RESOURCE` (`:139-142`), efekt `RESOURCE_MAX` (`:143-146`), kontrola kosztu z komunikatem „Za mało zasobu na zagranie karty” (`:287-288`), wydatek (`:305`), wzrost na turę (`:447-448`); plus kopia robocza `SideState` (`:568-585`). Mechanika jest czystą zmianą nazwy — brak inwersji jak w stress-to-hp.
- Kontrakt zewnętrzny: `MatchView.ResourceView(current, max)` (`api/MatchView.java:76-79`) serializowany jako `player.resource` i `opponent.resource` (`:84,91,100,107`, także w `replay[*]`); `EffectView.kind` wysyła `"RESOURCE"` / `"RESOURCE_MAX"`; komunikat błędu `IllegalMoveException` trafia do klienta jako HTTP 400 (`api/MatchController.java:108-110`).
- Dane kart: dokładnie 2 efekty w 2 kartach `factions.json` — `neu-coffee` (`:45`, `RESOURCE` 2) i `neu-sprint-planning` (`:49`, `RESOURCE_MAX` 1). Pole `cost` nie jest nazwane przez zasób i zostaje.
- Teksty backendu: 3 łańcuchy — `EffectKind.java:77` „Zyskaj %d pkt zasobu” / „Gain %d resource”, `:78-79` „Zwiększ maksymalny zasób o %d” / „Increase maximum resource by %d”, `MatchEngine.java:288` „Za mało zasobu na zagranie karty”. `MatchLogFormatter`, `MatchEvent`, `AbilityKeyword` nie mówią o zasobie (sprawdzony grep).
- SPA tylko wyświetla: typy `match.ts:35-36,77-80,138,151`, szablon `match-table.html:21-23,176-178` (klasa `resource`), i18n `matchTable.resource` (`messages.ts:37`, `pl.ts:36` „Zasób”, `en.ts:36` „Resource”). Brak reguły CSS `.resource`. Dostępność karty liczy backend (`playable`), front nie czyta zasobu w logice (`match-table.ts:123`).
- PRD **nie opisuje** zasobu na zagrywanie: jedyne „zasobem” w `prd.md:119` to „jedynym zasobem przegranej jest HP” — inne znaczenie, do zachowania. Zakres „PRD” z `change.md` jest więc pusty, chyba że plan doda nowe zdanie o budżecie.
- Naiwny grep `resource|zas[oó]b` nigdy nie będzie pusty (Spring `ClassPathResource`, katalog `src/main/resources`, „zasobow statycznych”); kryterium sukcesu wymaga grepu z wykluczeniami. Dziś wariant z wykluczeniami (niżej) zwraca 183 linie w `src` + `frontend/src`.

## Detailed Findings

### Model, reguły i silnik

- `MatchSide.java:10` — składniki rekordu `resourceCurrent`, `resourceMax`.
- `MatchRules.java:7` (javadoc: „zaczynający ma zasób 1/1 … Zasób rośnie o 1 na własną turę do {@link #RESOURCE_CAP}”), `:20` `RESOURCE_CAP = 10`.
- `CardCatalog.java:18` — `MAX_CARD_COST = MatchRules.RESOURCE_CAP`; stała kosztu zostaje, zmienia się tylko referencja.
- `MatchFactory.java:65-66` — lokalna `int resource = active ? 1 : 0`, przekazana dwukrotnie do `MatchSide`.
- `MatchEngine.java` — miejsca jak w Summary; `SideState` pola `:568`, `:570`, kopiowanie `:580-581`, zapis `:585`.
- `MatchEvent` — brak wariantu o zasobie; zmiany zasobu są widoczne w logu tylko przez `EffectApplied` z efektem `RESOURCE`/`RESOURCE_MAX`; wzrost na turę nie jest logowany.

### Dane kart i nazwy enumów

- `EffectKind.java:15-16` — `RESOURCE(true, 1, 10, SELF)`, `RESOURCE_MAX(...)`; enumy deserializowane po nazwie z `factions.json`, więc zmiana nazwy wymaga edycji 2 wpisów `kind` (`factions.json:45`, `:49`).
- Karty `neu-coffee` i `neu-sprint-planning` występują w taliach (`factions.json:69,73,91,95`) tylko przez id — bez zmian.

### Kontrakt JSON i błąd

- `MatchView.java:76-79` `ResourceView`, `:84` `PlayerView.resource`, `:100` `OpponentView.resource`; ten sam rekord w krokach `replay[*]`.
- `MatchController.java:108-110` — `IllegalMoveException` → 400 `ErrorView(message)`; SPA spec ma fixture `{ message: 'Za mało zasobu' }` (`match-table.spec.ts:1013`).

### Teksty i polska odmiana

- „budżet” jest rodzaju męskiego jak „zasób”, więc „maksymalny budżet”, „Budżet rośnie”, „bieżącym budżetem”, „Za mało budżetu na zagranie karty” podstawiają się poprawnie.
- Niezgrabne przy dosłownej podmianie: „Zyskaj %d pkt budżetu” (poprawne, ale ciężkie) i en „Gain %d budget” (budget nie jest rzeczownikiem policzalnym). Wymaga decyzji o brzmieniu (Open Questions).
- Liczba mnoga „zasoby” → „budżety” brzmiałaby źle; występuje tylko w nazwie testu SPA „…and resources” (`match-table.spec.ts:326`).

### SPA

- `match.ts:35-36` unia `'RESOURCE' | 'RESOURCE_MAX'`, `:77-80` `type ResourceView`, `:138`, `:151` `resource: ResourceView`.
- `match-table.html:21-23` (przeciwnik), `:176-178` (gracz): `<div class="resource">`, `texts().matchTable.resource`, `m.<side>.resource.current/max`.
- Specy: `match-table.spec.ts` — `:35`, `:38`, `:599` (`'Zyskaj 2 pkt zasobu'`), `:124`, `:127` (`hand(resource)`), `:166,178,210,521,558,572` (fixture `resource`), `:326`, `:333`, `:338` (`.resource dd`), `:412` (`'Resource'`), `:1013`; `new-match/new-match.spec.ts:28,40`.

### Testy backendu

- `MatchFactoryTests.java:100,111` (nazwy `…StartsWithOneResource…`), `:104-107,115-118` (akcesory).
- `MatchEngineTests.java` — nazwy `notEnoughResourceIsRejected` (`:51`), `endTurnSwitchesSideDrawsAndRaisesResource` (`:462`), `resourceStopsAtCap` (`:477`), `resourceEffectsStopAtCap` (`:767`); `SideBuilder.resource(int)` (`:931-933`) z ok. 45 wywołaniami w `:40-816`; `EffectKind.RESOURCE*` i `RESOURCE_CAP` w `:187-196,256-265,478-483,768-778`. `notEnoughResourceIsRejected` nie sprawdza treści komunikatu.
- `MatchControllerTests.java` — `containsOnlyKeys(... "resource" ...)` `:278,281`, JsonPath `$.player.resource.*` / `$.opponent.resource.max` `:283-294,452,657`, akcesory `:554-555`, komentarze `:450,544,669`.
- `MatchViewTests.java:180` — `.contains("resource")` w tekście en efektu `neu-coffee`.
- `RandomOpponentTests.java:175-176` (parametr `resource`), `MatchServiceTests.java:168` (komentarz).

### Fałszywe trafienia (wykluczyć z kryterium grep)

- `SpaForwardController.java:16` („zasobow statycznych”), `GameConfig.java:8,18` i `CardCatalogLoader.java:6,22-32` (Spring `Resource`/`ClassPathResource`), importy i użycia `ClassPathResource`/`ByteArrayResource` w testach (`MatchViewTests`, `MatchServiceTests`, `MatchFactoryTests`, `RandomOpponentTests`, `MatchLogFormatterTests`, `CardCatalogTests`).
- Proponowane kryterium (zwraca dziś 183 linie, po zmianie ma zwrócić 0):

```
grep -rniE 'resource|zas[oó]b|zasob' src frontend/src \
 | grep -vE 'ClassPathResource|ByteArrayResource|core\.io\.Resource|Resource resource\)|resource\.(getInputStream|getDescription)|zasobow statycznych|var resource = new ByteArrayResource|build\(\), resource\)'
```

## Code References

- `src/main/java/com/example/itcardbrawl/game/MatchSide.java:10` — `resourceCurrent`, `resourceMax`
- `src/main/java/com/example/itcardbrawl/game/MatchRules.java:7,20` — javadoc i `RESOURCE_CAP`
- `src/main/java/com/example/itcardbrawl/game/MatchFactory.java:65-66` — start 1/1 vs 0/0
- `src/main/java/com/example/itcardbrawl/game/MatchEngine.java:83-84,115,139-146,287-288,305,447-448,568-585` — użycia w silniku
- `src/main/java/com/example/itcardbrawl/game/EffectKind.java:15-16,77-79` — enumy i opisy
- `src/main/java/com/example/itcardbrawl/game/CardCatalog.java:18` — `MAX_CARD_COST`
- `src/main/java/com/example/itcardbrawl/api/MatchView.java:76-79,84,91,100,107` — `ResourceView`, pola `resource`
- `src/main/java/com/example/itcardbrawl/api/MatchController.java:108-110` — komunikat błędu do klienta
- `src/main/resources/game/factions.json:45,49` — 2 efekty
- `frontend/src/app/game/match.ts:35-36,77-80,138,151` — typy
- `frontend/src/app/match-table/match-table.html:21-23,176-178` — panel zasobu
- `frontend/src/app/i18n/messages.ts:37`, `pl.ts:36`, `en.ts:36` — etykieta
- `.github/workflows/ci.yml:3-8,56-58,86-88` — deploy przy każdym pushu na `main`

## Architecture Insights

- Ten sam kształt co stress-to-hp: zmiana nazw w `game` łamie kompilację `api`, więc backend (silnik, dane, widok, testy) musi przejść w jednej fazie, a SPA w drugiej; jeden push po SPA, bo CI wdraża każdy push na `main` (`ci.yml:3-8,56-58,86-88`) i SPA czytałoby nieistniejące `resource`.
- Brak bazy (`MatchStore` w pamięci) — bez migracji i wersjonowania API; SPA jest jedynym konsumentem i wychodzi w tym samym obrazie.
- Mniejszy zakres niż stress-to-hp: brak zdarzenia logu, brak logiki do odwrócenia, 2 wpisy w danych, 3 łańcuchy backendu, 1 etykieta i18n.

## Historical Context (from prior changes)

- `context/archive/2026-11-19-match-setup-table/plan.md:7,25,51,92,146` i `plan-brief.md:23` — wprowadzenie zasobu (1→10, +1 na turę, limit 10, widok `resource: {current,max}`); brak uzasadnienia nazwy „zasób” — supported (sprawdzony grep `context/archive/**`, brak alternatyw typu budżet/mana/energia).
- `context/archive/2026-11-20-event-cards/plan.md:58-59,65` — dodanie `RESOURCE` / `RESOURCE_MAX` (SELF, 1–10, limit `RESOURCE_CAP`).
- `context/archive/2026-11-19-ui-in-english-and-polish/plan.md:237` — przeniesienie etykiety „Zasób” do słownika i18n.
- `context/archive/2026-11-21-stress-to-hp/plan.md` — szablon: 3 fazy (backend → SPA + push → dokumenty), lokalny commit bez pusha po fazie 1, kryterium grep. Jego `reviews/impl-review.md` pokazał luki, które wrócą: brak asercji nowych pól w `replay[*]` (F1), teksty dosłownie z planu brzmiące źle (F2, F3, F5), literały zamiast stałych w testach (F7).
- `context/foundation/roadmap.md:131,207` — mówią o „zasobie zagrania” jako otwartym pytaniu; stress-to-hp nie edytował roadmapy ani shape-notes (historia zostaje).

## Related Research

- `context/archive/2026-11-21-stress-to-hp/research.md` — analogiczna zmiana nazwy licznika gracza.
- `context/archive/2026-11-20-first-full-match/research.md:37` — opis zasady zasobu.

## Open Questions

1. **Nazwy w kodzie i kontrakcie** — np. `budgetCurrent`/`budgetMax`, `BUDGET_CAP`, `BudgetView`, JSON `player.budget.{current,max}`, `EffectKind.BUDGET` / `BUDGET_MAX`. Do potwierdzenia w planie.
2. **Brzmienie tekstów** — „Zyskaj %d pkt budżetu” vs np. „Zwiększ budżet o %d”; en „Gain %d budget” vs „Gain %d budget points” / „+%d budget”; komunikat błędu „Za mało budżetu na zagranie karty” vs „Brak budżetu na zagranie karty”; etykieta UI „Budżet” / „Budget”.
3. **PRD** — PRD nie opisuje zasobu; czy plan ma dodać zdanie o budżecie do §Business Logic (nowa treść), czy pominąć PRD. „zasobem przegranej” w `prd.md:119` ma zostać lub zostać przeformułowane, żeby grep po PRD nie mylił.
4. **Nazwy kart** — „Kawa z automatu” („Paliwo projektu”) i „Planowanie sprintu” pasują do budżetu tematycznie; zmiana tekstów fabularnych poza zakresem „mechanika bez zmian” — do potwierdzenia, że zostają.
