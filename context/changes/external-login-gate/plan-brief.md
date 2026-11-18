# Logowanie zewnętrznym kontem (external-login-gate) — Plan Brief

> Full plan: `context/changes/external-login-gate/plan.md`
> Research: `context/changes/external-login-gate/research.md`

## What & Why

Element roadmapy S-01: gracz loguje się kontem GitHub albo Google i widzi lobby ze swoją nazwą, a niezalogowany trafia na ekran logowania (PRD FR-001, §Access Control — brak trybu gościa, brak własnych haseł). To najtrudniejsza technicznie część stosu (przekierowania OAuth, sesja, proxy SPA), więc wchodzi przed logiką gry, żeby błędy logowania nie mieszały się z błędami zasad.

## Starting Point

Szkielet F-01: Spring Boot 4.1.2 z łańcuchem security odpowiadającym `401` (bez `oauth2Login`) i publicznym `/api/ping`; Angular 22 bez routera, pokazujący tylko status pinga przez proxy `/api`. Zależności oauth2-client są już w `pom.xml`.

## Desired End State

`localhost:4200/` bez sesji prowadzi na `/sign-in` z przyciskami GitHub i Google. Po zgodzie gracz wraca do lobby z „Witaj, {nazwa}”, awatarem i przyciskiem „Wyloguj”. Nieudane logowanie, niedostępny serwer i wylogowanie mają własne komunikaty na ekranie logowania. Tokeny dostawców nigdy nie trafiają do przeglądarki, a sekrety nigdy do repo.

## Key Decisions Made

| Decision | Choice | Why (1 sentence) | Source |
| --- | --- | --- | --- |
| Dostawcy | GitHub + Google | Konta, które persona IT na pewno ma; oba wbudowane w `CommonOAuth2Provider`. | Research (decyzja użytkownika) |
| Kształt auth | Spring `oauth2Login` + sesja w cookie, jedno źródło przez proxy | Brak tokenów w przeglądarce i brak CORS. | Research / tech-stack |
| Sekrety lokalnie | Git-ignorowany `.env.properties` (import Springa) + `.example` w repo; env vars nadal działają | Działa z IDE i `./mvnw` bez ustawiania zmiennych w każdej konsoli. | Plan |
| `/api/me` | `{provider, displayName, avatarUrl}`, przy okazji wystawia cookie `XSRF-TOKEN` | Jeden endpoint, który guard i tak woła przed każdą mutacją; awatar dla ładniejszego lobby. | Plan |
| Nawigacja | Router + `CanActivateFn` (lobby `''`, `sign-in`) | Prawdziwe URL-e i przygotowanie pod S-02. | Plan |
| Widok pinga | Usunięty z UI, endpoint zostaje w backendzie | Lobby/guard dowodzą łączności; `/api/ping` przyda się F-02 jako health check. | Plan |
| Backend niedostępny | Guard → `/sign-in?unavailable` z komunikatem | Jedno miejsce na komunikaty, zastępuje usunięty status API. | Plan |
| Wygasła sesja | Interceptor 401 (poza sondą `/api/me`) → `/sign-in` już w S-01 | Endpointy gry z S-02/S-03 dostają obsługę za darmo. | Plan |
| Błąd logowania / wylogowanie | `?error` → komunikat błędu; wylogowanie `POST /api/logout` → `204` → `/sign-in?logout` „Wylogowano.” | Wszystkie stany na jednym ekranie, testowalne parametrem. | Plan |
| Testy | MockMvc + Vitest, bez E2E | Pokrywa każdą decyzję bez prawdziwych dostawców; jedna nowa technologia na krok. | Plan |

## Scope

**In scope:**
- `oauth2Login`, `csrf.spa()`, wylogowanie przez API, `GET /api/me` z fallbackami nazwy
- Konfiguracja sekretów i atrapy klientów dla testów
- Router, `AuthService`, guard, interceptor, ekrany sign-in i lobby, proxy `/oauth2` + `/login/oauth2`
- Instrukcja rejestracji aplikacji u dostawców

**Out of scope:**
- Baza/trwałe konta, email GitHuba, wylogowanie u dostawcy
- Produkcyjne callbacki i serwowanie SPA przez Springa (F-02)
- Przekierowanie zalogowanego z `/sign-in`, E2E/Playwright, „zapamiętaj mnie”

## Architecture / Approach

Przeglądarka → `localhost:4200` (ng serve). Ścieżki `/api`, `/oauth2`, `/login/oauth2` idą przez proxy (z zachowanym `Host`) do Springa na `:8080`. Spring prowadzi przepływ OAuth, trzyma sesję (`JSESSIONID`) i CSRF (`XSRF-TOKEN`) i wystawia `/api/me`. SPA tylko nawiguje (`<a href>` do startu przepływu) i czyta `/api/me` w guardzie. Mutacje (wylogowanie) dostają nagłówek XSRF automatycznie z `provideHttpClient()`.

## Phases at a Glance

| Phase | What it delivers | Key risk |
| --- | --- | --- |
| 1. Backend — oauth2Login, /api/me, CSRF, wylogowanie | Działający przepływ OAuth i API profilu, pokryte MockMvc | Kontekst testów nie ładuje się bez atrap; plik testowy w złym miejscu przesłania główną konfigurację |
| 2. Frontend — bramka logowania | Router, guard, sign-in, lobby; pełne logowanie obydwoma dostawcami | Złe klucze proxy / `changeOrigin` psują `redirect_uri` |
| 3. Frontend — cykl sesji | Wylogowanie z potwierdzeniem, interceptor 401 | Brak cookie `XSRF-TOKEN` przed POST → `403` |

**Prerequisites:** F-01 (done); zarejestrowane aplikacje GitHub OAuth i Google OAuth client z callbackami `http://localhost:4200/login/oauth2/code/{github,google}` (krok ręczny w fazie 1).
**Estimated effort:** ~3 sesje, po jednej na fazę.

## Open Risks & Assumptions

- Rejestracja u Google wymaga skonfigurowania ekranu zgody; w trybie testowym trzeba dodać własne konto jako testera.
- Zakładamy, że GitHub `name` bywa null (niepotwierdzone w docs) — fallback na `login` i tak jest tani.
- Interceptor 401 nie ma w S-01 ręcznego scenariusza (jedyną mutacją jest wylogowanie, które przechodzi bez sesji) — pokrywają go testy jednostkowe.

## Success Criteria (Summary)

- Niezalogowany gracz zawsze trafia na ekran logowania; po zalogowaniu GitHubem lub Google widzi lobby ze swoją nazwą.
- Nieudane logowanie, niedostępny serwer i wylogowanie dają czytelny komunikat.
- `./mvnw test`, testy Vitest i build przechodzą; w repo nie ma żadnego sekretu.
