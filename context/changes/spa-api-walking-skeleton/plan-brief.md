# Szkielet SPA ↔ API — Plan Brief

> Full plan: `context/changes/spa-api-walking-skeleton/plan.md`

## What & Why

Element F-01 roadmapy: cienka aplikacja Angular w `frontend/` uruchamia się lokalnie i pokazuje odpowiedź z API Spring Boota, a przeglądarka widzi jedno źródło (proxy dev servera). Dzięki temu S-01 (logowanie zewnętrznym kontem) dostaje gotowy ekran, API i łańcuch security do rozszerzenia. Autor przy okazji poznaje Angulara jako jedyną nową technologię w tym kroku.

## Starting Point

Backend to czysty scaffold Spring Boot 4.1.2 (Java 21) bez kontrolerów, za to ze starterami Spring Security i OAuth2 client w `pom.xml`. Nie ma własnego `SecurityFilterChain`, więc dziś wszystko jest zablokowane domyślnym form loginem. Frontendu nie ma.

## Desired End State

`http://localhost:4200` pokazuje „API: ok”, a w DevTools żądanie idzie na `localhost:4200/api/ping` (bez CORS). Gdy Spring nie działa, strona pokazuje „API niedostępne”. `GET /api/ping` jest publiczny, każdy inny `/api/**` bez logowania zwraca 401. Testy po obu stronach przechodzą.

## Key Decisions Made

| Decision | Choice | Why (1 sentence) |
| -------- | ------ | ---------------- |
| Security w szkielecie | Minimalny `SecurityFilterChain`: `/api/ping` permitAll, reszta authenticated, bez form login/basic, entry point 401 | Default-deny od pierwszego dnia; S-01 tylko dokłada `oauth2Login` do istniejącego łańcucha. |
| Pakowanie / granica z F-02 | Tylko proxy deweloperskie (`proxy.conf.json` → `:8080`) | Najmniejszy zakres; sposób serwowania SPA na produkcji to decyzja F-02. |
| Zakres Angulara | `ng new` bez routera i SSR, standalone/zoneless, jeden serwis z HttpClient | Router i struktura dojdą w S-01; więcej teraz to dryf „na zapas”. |
| Testy | MockMvc (backend) + Vitest z `HttpTestingController` (frontend) + ręczna kontrola proxy | Szybkie, deterministyczne testy; E2E osobno przez `/10x-e2e-setup`. |
| Kontrakt API | `GET /api/ping` → `200 {"status":"ok"}` | Najprostszy dowód połączenia; prefiks `/api` jest kluczem proxy. |

## Scope

**In scope:**
- `PingController` + `SecurityConfig` + testy MockMvc
- Workspace Angular 22 w `frontend/`, proxy, `PingService`, root component (ładowanie/sukces/błąd), test Vitest
- Krótki `frontend/README.md` z instrukcją uruchomienia

**Out of scope:**
- OAuth2, sesja, CSRF (S-01)
- Serwowanie SPA z jara, Docker, CI (F-02)
- Router, stan, biblioteki UI, E2E, pełna macierz 4 przeglądarek

## Architecture / Approach

```
przeglądarka ──► localhost:4200 (ng serve) ──/api/**──► localhost:8080 (Spring Boot)
                   └─ SPA: App → PingService → GET /api/ping      └─ SecurityFilterChain → PingController
```
Najpierw backend (faza 1, sprawdzalny bez Angulara), potem frontend (faza 2, bez zmian w backendzie).

## Phases at a Glance

| Phase | What it delivers | Key risk |
| ----- | ---------------- | -------- |
| 1. Backend — ping + minimalny łańcuch security | Publiczny `/api/ping`, reszta `/api/**` → 401, testy MockMvc | Domyślne 403 zamiast 401 lub przekierowanie, jeśli entry point nie zostanie ustawiony jawnie |
| 2. Frontend — szkielet Angular z proxy | `frontend/` z proxy, serwisem, widokiem stanu i testem Vitest | Pierwszy kontakt z Angularem; niedziałające proxy (URL absolutny zamiast względnego) |

**Prerequisites:** Node ≥ 24.15 (jest 24.21), JDK 21, dostęp do npm registry.
**Estimated effort:** ~1–2 sesje w 2 fazach.

## Open Risks & Assumptions

- Zakładamy, że Angular CLI 22 generuje domyślnie Vitest i aplikację zoneless (sprawdzone w `ng new --help` 2026-11-16); jeśli flagi się zmienią, dostosować polecenie `ng new`.
- „Jedno źródło” jest udowodnione tylko lokalnie; dowód na produkcji należy do F-02.

## Success Criteria (Summary)

- Gracz/autor otwiera `localhost:4200` i widzi „API: ok”, a żądanie idzie przez to samo źródło.
- Nieuwierzytelnione wywołania innych endpointów `/api/**` dostają 401, co daje gotową bazę pod logowanie w S-01.
- `./mvnw test` i testy Vitest przechodzą.
