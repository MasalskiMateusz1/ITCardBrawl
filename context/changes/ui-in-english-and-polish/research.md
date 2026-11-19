---
date: 2026-11-19T17:01:50+01:00
researcher: Claude (Opus 5.5) for Mateusz Masalski
git_commit: d657f6b78ea14346f29130305eeebbe0c3bde59d
branch: main
repository: a-cards
topic: "Runtime English/Polish language switch for the UI and the game content served by the API"
tags: [research, codebase, i18n, frontend, angular, card-catalog, match-view]
status: complete
last_updated: 2026-11-19
last_updated_by: Claude (Opus 5.5) for Mateusz Masalski
---

# Research: Runtime English/Polish language switch for the UI and the game content

**Date**: 2026-11-19T17:01:50+01:00
**Researcher**: Claude (Opus 5.5) for Mateusz Masalski
**Git Commit**: d657f6b
**Branch**: main
**Repository**: a-cards

## Research Question

The user wants the existing app available in English and Polish, switchable in-app at runtime (decision recorded in this session on 2026-11-19, after match-setup-table Phase 3). Scope chosen by the user: **UI and game content** — Angular screens plus faction/card names, descriptions and texts that the backend serves from `factions.json`. What exists today, where does text originate, what must change, and which decisions remain open for `/10x-plan`?

## Summary

- **No i18n exists anywhere in the inspected code.** Frontend: no `@angular/localize`, Transloco or ngx-translate in `node_modules`, no `i18n` block in `angular.json`, no `LOCALE_ID`/`$localize` in `frontend/src`. Backend: no `LocaleResolver`, `MessageSource`, `messages*.properties`, `Accept-Language` handling or `spring.web.locale`/`spring.messages` properties (grep of `src/main`, `pom.xml`, `application*.properties`).
- **Frontend UI text**: about 36 distinct Polish strings (manual tally), hard-coded in templates and TS across 5 components (sign-in, lobby, new-match, match-table, app shell) plus `<html lang="pl">` in `frontend/src/index.html:2`. Error messages are stored as finished strings in signals (e.g. `frontend/src/app/new-match/new-match.ts:29,54`), so a message already on screen would not re-translate after a switch unless it is stored as a key.
- **Game content**: all of it comes from `src/main/resources/game/factions.json` — 3 factions (`name`, `description`) and 30 cards (`name`, `text`), single-language strings with no language key (`factions.json:2-55`). Card names are already a mix of English job titles and Polish phrases; every card `text` is Polish.
- **Decisive backend constraint**: a stored `Match` snapshots catalog objects, not ids — `MatchSide` holds `Faction`, `CardInstance` holds `Card` (`MatchSide.java:8`, `CardInstance.java:6`), and `MatchView` reads `card.name()`, `card.text()`, `faction.name()` straight from those objects (`MatchView.java:37-38,62-65`). To show an ongoing match in a newly chosen language, the view must resolve text by id from the catalog for the requested language at render time (ids are present; `MatchView.of` already receives the catalog — `MatchView.java:20`).
- **No server-side place to store a preference**: no DB/JPA dependency in `pom.xml`, `MeResponse(provider, displayName, avatarUrl)` has no preference field (`MeController.java:48`), and the frontend uses no `localStorage`/`sessionStorage`/`document.cookie` in `frontend/src`.
- **Product docs are silent on language**: PRD, roadmap, tech-stack and shape-notes contain no language/i18n decision, non-goal or parked item. Polish is the de-facto language (one archived review fix set `lang="pl"`). This change adds scope not yet in the PRD/roadmap.
- **Test impact**: 23 frontend assertions or element lookups use literal Polish UI strings across 4 spec files (anchors under Test impact), plus 1 brand check in `app.spec.ts:28`; backend tests assert Polish catalog names in 2 places and about 25 Polish validation messages (`CardCatalogTests`).

## Detailed Findings

### Frontend UI strings (Angular 22, zoneless, standalone)

Per component (all hard-coded Polish):

- `frontend/src/index.html:2` `<html lang="pl">`; `:5` `<title>ItCardBrawl</title>` (brand). `frontend/src/app/app.html:1` `<h1>ItCardBrawl</h1>` (brand).
- **sign-in**: `sign-in.html:5-6` „Zaloguj przez GitHub/Google”; `sign-in.ts:14-26` a `computed()` over query params yields „Logowanie nie powiodło się — spróbuj ponownie.” (`:17`), „Serwer gry niedostępny — spróbuj później.” (`:20`), „Wylogowano.” (`:23`).
- **lobby**: `lobby.html:5` „Witaj, {{ player.displayName }}” (interpolation), `:8` „Nowa partia”, `:10` „Wyloguj”; `lobby.ts:32` „Nie udało się wylogować — spróbuj ponownie.” (signal).
- **new-match**: `new-match.html:1` „Nowa partia”, `:4` „Wybierz frakcję”, `:21` „Rozpocznij partię”, `:28` „Wróć do lobby”; `new-match.ts:29` and `:54` error messages (signal).
- **match-table**: `match-table.html` — aria-labels „Przeciwnik” (`:2`) / „Ty” (`:29`), headings „Przeciwnik: …” (`:3`) / „Ty: …” (`:30`), „Stres”, „Zasób”, „Kart na ręce”, „Kart w talii”, „Tura {{n}} —” + ternary „Twoja tura”/„tura przeciwnika” (`:25-26`), „Twoja ręka” (`:46`), „Koszt: {{cost}}” (`:52`), „Neutralna” (`:54`), „Wróć do lobby” (`:67`); `match-table.ts:7-10` `CARD_TYPE_LABELS` map (CHARACTER→„Postać”, EVENT→„Wydarzenie”) and `:34` load error (signal).
- No user-visible strings in `auth.guard.ts`, `auth.interceptor.ts`, `auth.service.ts`, `game.service.ts`, `game/match.ts`, `app.routes.ts`, `main.ts` (they hold routes, URLs and the query-param keys `error`/`unavailable`/`logout`). No CSS `content:` strings in `frontend/src/**/*.css` (grep, 0 matches).
- Faction `name`/`description` on `/new-match` and card `name`/`text` + faction names on `/match` come from the API (`new-match.html:14-15`, `match-table.html:50,56`), not from frontend code.

### Frontend wiring relevant to a language switch

- `frontend/src/app/app.config.ts:8-12` — providers: `provideBrowserGlobalErrorListeners()`, `provideHttpClient(withInterceptors([authInterceptor]))` (`:10`), `provideRouter(routes)`. A request interceptor (e.g. adding `Accept-Language` or a `lang` param) would join the interceptor array at `:10`.
- `frontend/src/app/auth.interceptor.ts:9-24` — functional interceptor; it only handles 401 responses and does not modify requests.
- API calls: `/api/me`, `/api/logout` (`auth.service.ts:23,28`); `/api/factions`, `/api/matches`, `/api/matches/current` (`game.service.ts:12,16,20`).
- Serving: production `Dockerfile` copies `frontend/dist/itcardbrawl/browser/` into Spring's `static/` before packaging (`Dockerfile:20`), so Spring serves a static `index.html` with no server-side templating. `<html lang>` therefore can only change at runtime via JS (`document.documentElement.lang`), unless separate builds per locale are produced. Dev uses `proxy.conf.json` for `/api`, `/oauth2`, `/login/oauth2` (`angular.json:57-59`).
- Packages: `@angular/*` ^22.2.0 (lock resolves core/common 22.2.6); `@angular/localize` is not installed — its only mention in `package-lock.json` is as an optional peer dependency of `@angular/build`. Whether Angular's built-in i18n can switch at runtime was **not verified in this repo** (package absent); per Angular's documented model it produces one build per locale, which would mean a reload to a different URL to switch — to be confirmed during planning if that option is considered.

### Backend game content

- `src/main/resources/game/factions.json` — `factions` (`:2-21`): `id`, `name`, `description`, `playable`; 3 entries (`developers` „Developerzy”, `testers` „Testerzy”, `neutral` „Neutralne”). `cards` (`:22-55`): `id`, `faction`, `name`, `type`, `cost`, `text`; 30 entries. `decks`: faction id → `{card, copies}`, 30 per playable faction.
- Records: `Faction(id, name, description, playable)` (`game/Faction.java:6`), `Card(id, faction, name, type, cost, text)` (`game/Card.java:6`), `CatalogData(factions, cards, decks)` (`game/CatalogData.java:9`). `CardType` serialises as enum names (CHARACTER/EVENT), so its label translation already lives in the frontend.
- Loading: one bean built at startup from `game/factions.json` via the Spring `JsonMapper` (`GameConfig.java:16-19`, `CardCatalogLoader.java:17,22-35`); invalid data stops the app.
- Validation touching text: blank faction `name` rejected (`CardCatalog.java:91-92`), blank card `name` rejected (`CardCatalog.java:114-115`). In the inspected `CardCatalog`, faction `description` and card `text` are not validated. Any per-language content needs these rules extended (e.g. name present for each supported language).

### Where text leaves the backend

- `GET /api/factions` → `FactionSummary(id, name, description)` (`MatchController.java:38-43,71`).
- `POST /api/matches` / `GET /api/matches/current` → `MatchView` (`MatchView.java:18-30`): `FactionRef(id, name)` for both sides (`:35-41`) and `HandCardView(instanceId, cardId, name, type, cost, text, neutral)` for the player's hand (`:59-66`). The opponent's hand and deck are sent as counts only (`:28-29`).
- Snapshot vs. lookup: `Match(id, turnNumber, activeSide, player, opponent)` (`Match.java:8`), `MatchSide(Faction faction, List<CardInstance> deck, List<CardInstance> hand, …)` (`MatchSide.java:8`), `CardInstance(int instanceId, Card card)` (`CardInstance.java:6`); `MatchFactory` copies `Card`/`Faction` references from the catalog (`MatchFactory.java:51-55,64`). `MatchView` reads text from these stored objects (`MatchView.java:37-38,62-65`).
- Polish strings in errors: `ResponseStatusException(BAD_REQUEST, "Nieznana lub niegrywalna frakcja", ex)` (`MatchController.java:53`). No `server.error.include-message` setting exists in `application*.properties` (grep: 0 matches for `error`), so with Spring Boot defaults the reason is presumably not in the error body — not verified at runtime. On the three game calls the frontend replaces any non-401 error with its own message (`new-match.ts:29,54`, `match-table.ts:34`), so backend error text does not reach the UI on the inspected paths. Remaining Polish strings in the backend (`MatchFactory.java:33,37`, `CardCatalog` validation, `CardCatalogLoader.java:28,32`) are internal/log-level and do not reach the UI.
- `MeController` returns OAuth data only (`MeController.java:16-31,48`); `SecurityConfig` responses (401, 204, `/sign-in?error` redirect) carry no text (`SecurityConfig.java:34,38-39`).

### Preference storage

- Server: no per-user persistence besides the in-memory `MatchStore` (`MatchStore.java:16`); no DB dependency in `pom.xml`; app code reads/writes no session attributes.
- Browser: no `localStorage`/`sessionStorage`/`document.cookie` usage in `frontend/src`.

### Test impact

- Frontend specs asserting or locating by literal Polish UI text: `sign-in.spec.ts:41-60` (5), `lobby.spec.ts:38,48,57,88,115` (5), `new-match.spec.ts:66,137,150,158` (4), `match-table.spec.ts:118-123,149,155,161,188,196` (9), `app.spec.ts:28` ('ItCardBrawl', brand). Several specs find buttons/links by visible text (e.g. `lobby.spec.ts:38`, `new-match.spec.ts:66`), so translation affects element lookup, not only text checks. `auth.guard.spec.ts` and `auth.interceptor.spec.ts` assert URLs and query params only.
- Frontend fixtures with Polish content data: `new-match.spec.ts:9,17,80`, `match-table.spec.ts:61,136`.
- Backend: `MatchControllerTests.java:99` asserts `$.player.faction.name == "Developerzy"`; `CardCatalogTests.java:67` asserts real-catalog names `["Developerzy","Testerzy"]`; fixtures build `Faction`/`Card` via constructors (`CardCatalogTests.java:32-34,54` and others), so a record shape change touches them; about 25 `withMessageContaining` assertions on Polish validation messages (internal messages — they can stay Polish). `MatchFactoryTests` asserts ids, sizes and structure only.

## Code References

- `frontend/src/index.html:2` — `<html lang="pl">`
- `frontend/src/app/app.config.ts:10` — HttpClient + interceptor array (plug-in point for a language header/param)
- `frontend/src/app/sign-in/sign-in.ts:14-26` — query-param → message `computed()`
- `frontend/src/app/match-table/match-table.ts:7-10` — card type label map
- `frontend/src/app/new-match/new-match.ts:29,54`, `lobby/lobby.ts:32`, `match-table/match-table.ts:34` — error text stored as strings in signals
- `src/main/resources/game/factions.json:2-55` — single-language content
- `src/main/java/com/example/itcardbrawl/game/Card.java:6`, `Faction.java:6` — content records
- `src/main/java/com/example/itcardbrawl/game/CardCatalog.java:91-92,114-115` — name validation
- `src/main/java/com/example/itcardbrawl/api/MatchView.java:20,37-38,62-65` — text read from snapshotted objects; catalog already passed in
- `src/main/java/com/example/itcardbrawl/api/MatchController.java:38-43,53,71` — factions endpoint, 400 reason
- `src/main/java/com/example/itcardbrawl/game/MatchSide.java:8`, `CardInstance.java:6`, `MatchFactory.java:51-55,64` — snapshot of catalog objects in a match
- `Dockerfile:20` — SPA bundled into Spring static resources

## Architecture Insights

- **The split is clean**: UI chrome is 100% frontend; game content is 100% backend (`factions.json`); backend error texts do not reach the UI on the inspected paths. A UI-only translation layer and a content-only translation layer can be planned independently and joined by one "current language" value passed from SPA to API.
- **Language must be a request parameter, not server state**, given no preference storage exists: the SPA keeps the chosen language (in memory, optionally persisted in the browser) and sends it on each content request (header such as `Accept-Language`, or a query param); the API resolves text per request. Spring MVC's `LocaleResolver`/`Accept-Language` support is available in the framework but unused today.
- **Resolve content by id at view time**: because `MatchView` currently reads from snapshotted `Card`/`Faction` objects, switching language mid-match requires `MatchView` (and `FactionSummary`) to look up localized name/text by `card.id()`/`faction.id()` in the catalog for the requested language. Keeping the match model language-neutral avoids re-creating matches on switch.
- **Store keys, not finished strings, for messages that can stay on screen** (signals at `new-match.ts:29,54`, `lobby.ts:32`, `match-table.ts:34`; `sign-in.ts` already derives from query keys), so they re-render in the new language.
- **Content shape options for `factions.json`** (to decide in the plan): per-field language maps (`"name": {"pl": …, "en": …}`), separate files per language (`factions.pl.json`/`factions.en.json` sharing ids, decks and costs), or a base file plus a translations overlay keyed by id. Validation must guarantee completeness for every supported language at startup, consistent with the existing fail-fast catalog.
- **`CardType` labels and other enums stay in the frontend** dictionary (already the pattern: `match-table.ts:7-10`).

## Historical Context (from prior changes)

- `context/archive/2026-11-16-spa-api-walking-skeleton/reviews/impl-review.md:36-44` — F2 fixed `lang="en"` → `lang="pl"` because the UI copy was Polish. Supported: this is the only recorded language decision, and it treats Polish as the UI language.
- `context/archive/2026-11-18-external-login-gate/plan.md:18-20,266` — Polish sign-in/lobby copy fixed as contract and asserted in tests (`:188,220,282,307`).
- `context/archive/2026-11-19-match-setup-table/plan.md:40,241,249` and `plan-brief.md:26` — card decks are agent placeholders defined once in `factions.json`, to be replaced by S-06 "bez zmian kodu"; Polish table copy fixed as test contract.
- `context/foundation/roadmap.md:176,183,185` — S-06 owns the final card content ("praca autorska", Owner: user). No roadmap slice, parked item or open question mentions language (`roadmap.md:48-51,203-218`).
- `context/foundation/prd.md:28,49` — the audience is defined by profession ("bez tłumaczenia" = jokes need no explaining), not by language; no NFR, non-goal or open question about language (`prd.md:105-109,129-139`).

## Related Research

- `context/archive/2026-11-18-external-login-gate/research.md` — no language findings (only an incidental `/en/` docs URL).

## Open Questions

Product decisions for the user (none is answered by docs or code):

1. **Default language** on first visit: Polish (today's behaviour), or detected from the browser (`navigator.language`)?
2. **Remember the choice?** Browser storage (`localStorage`) across visits, or only for the session/tab? (No server-side storage exists; adding one is outside the current stack.)
3. **Where the switch lives**: on every screen including `/sign-in` (app shell header), or only after login?
4. **Who writes the English card/faction texts now?** The agent drafting placeholder translations (like the current Polish placeholders), knowing S-06 will replace all card content — and S-06 will then need content in both languages. Card names that are already English (e.g. "Junior Developer") may stay identical in both.
5. **Scope of "content"**: translate card names too, or only descriptions/texts (some names are deliberate English IT jargon)?
6. **Docs**: update PRD/roadmap to record bilingual UI/content as a requirement (affects S-06's content task), or keep it as this change's decision only?

Technical choices for `/10x-plan` (with evidence above): frontend approach (small signal-based dictionary service vs. a library such as Transloco; built-in Angular i18n implies per-locale builds — unverified here), language transport (`Accept-Language` vs. `?lang=`), and `factions.json` shape (per-field maps vs. per-language files vs. overlay).
