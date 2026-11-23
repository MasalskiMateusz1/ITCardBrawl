<!-- IMPL-REVIEW-REPORT -->
# Implementation Review: Szkielet wdrożenia (F-02)

- **Plan**: context/changes/deploy-pipeline-skeleton/plan.md
- **Scope**: Full plan
- **Reviewed phases**: 1, 2, 3, 4, 5
- **Date**: 2026-11-19
- **Verdict**: REJECTED
- **Findings**: 1 critical, 4 warnings, 4 observations

## Verdicts

| Dimension | Verdict |
|-----------|---------|
| Plan Adherence | PASS |
| Scope Discipline | PASS |
| Safety & Quality | FAIL |
| Architecture | PASS |
| Pattern Consistency | PASS |
| Success Criteria | PASS |

Automated criteria (all phases) re-run 2026-11-19: `./mvnw verify` OK, `ng test` 5/5 files OK, `docker build` OK, no `.env.properties` in image, container healthy + `/api/ping` + `<app-root>`, CI default branch `main`, shellcheck OK, compose config OK, actionlint OK, run 44530077836 test/image/deploy success, GHCR manifest `:37fdce8…` OK, prod `/api/ping` OK.

Unplanned but benign changes: `.gitattributes` (`*.sh eol=lf`), `.gitignore` (server secrets/state), `mvnw` mode 755, `SecurityFlowTests` (CSRF via cookie, ed0992c).

## Findings

### F1 — Zakodowana ścieżka omija autoryzację /api

- **Severity**: ❌ CRITICAL
- **Impact**: 🔎 MEDIUM — real tradeoff; pause to reason through it
- **Dimension**: Safety & Quality
- **Location**: src/main/java/com/example/itcardbrawl/web/SpaForwardController.java:47-48 (używane w SecurityConfig.java:44-47)
- **Detail**: `pathWithinApplication` bierze surowe `getRequestURI()` (niezdekodowane), a Spring MVC dopasowuje handler po zdekodowanej ścieżce. `GET /%61pi/me` ma pierwszy segment `%61pi` → `spaRoutes()` daje permitAll → MVC kieruje do `MeController` bez uwierzytelnienia. Potwierdzone na produkcji: `/api/me` → 401, `/%61pi/me` → 500 (NPE na null principal). Dziś skutek to 500, ale każdy przyszły GET pod `/api`, który nie dereferencjonuje principala, byłby publiczny.
- **Fix**: Decydować na zdekodowanej, znormalizowanej ścieżce: w `pathWithinApplication` użyć `UrlPathHelper.defaultInstance.getPathWithinApplication(request)` (dekoduje i usuwa `;`-parametry) i dodać test regresji `get(URI.create("/%61pi/me"))` → 401 (oraz wariant SPA, np. `/%73ign-in` nadal 200).
  - Strength: Naprawia u źródła — kontroler i reguła bezpieczeństwa nadal współdzielą jedną funkcję, więc nie rozjadą się.
  - Tradeoff: Mała zmiana w jednym miejscu + 1–2 testy.
  - Confidence: MED — UrlPathHelper dekoduje tak jak warstwa MVC; StrictHttpFirewall już odrzuca `%2F`, `%2E`, `;`.
  - Blind spot: Nie sprawdzono wariantów wielkości liter (`/API/me`) — MVC jest case-sensitive, ale warto dodać to do testu.
- **Decision**: FIXED — `pathWithinApplication` używa `UrlPathHelper.defaultInstance.getPathWithinApplication`; testy `percentEncodedApiPathStaysProtected` (czerwony przed poprawką, NPE) i `percentEncodedSpaRouteStillForwardsToIndex` w SpaForwardTests

### F2 — Reguła tras SPA to deny-lista: nowe endpointy GET poza /api są publiczne

- **Severity**: ⚠️ WARNING
- **Impact**: 🔎 MEDIUM — real tradeoff; pause to reason through it
- **Dimension**: Safety & Quality
- **Location**: SpaForwardController.java:20, SecurityConfig.java:28
- **Detail**: Każdy GET, którego pierwszy segment nie jest `api|oauth2|login|error`, a ostatni nie ma kropki, dostaje permitAll. Dodanie np. actuatora (`/actuator/env`) albo kontrolera poza `/api` po cichu wystawi go publicznie.
- **Fix A ⭐ Recommended**: Utrwalić konwencję „cały backend pod /api” — test, który przez `RequestMappingHandlerMapping` sprawdza, że każdy handler poza `SpaForwardController` ma ścieżkę z prefiksem backendu, plus wpis w lessons.md.
  - Strength: Zachowuje prostą regułę i automatyczne nowe trasy Angulara; test łapie naruszenie w CI.
  - Tradeoff: Jeden dodatkowy test do utrzymania.
  - Confidence: HIGH — handler mapping jest dostępny w kontekście testowym.
  - Blind spot: Nie łapie endpointów spoza MVC (np. actuator na osobnym mapowaniu).
- **Fix B**: Allow-lista tras Angulara (`/sign-in`, przyszłe trasy) zamiast deny-listy.
  - Strength: Domyślnie odmawia — najbezpieczniejsze.
  - Tradeoff: Każda nowa trasa frontu wymaga zmiany w backendzie; łatwo zapomnieć → 401 przy odświeżeniu.
  - Confidence: MED — działa, ale wiąże front z backendem.
  - Blind spot: Parametryzowane trasy gry wymagają wzorców.
- **Decision**: FIXED via Fix A — `BackendRoutesTests` (break-check: tymczasowy `/status/info` → czerwony) + wpis w context/foundation/lessons.md

### F3 — Zmiana Caddyfile może zostać po cichu niezastosowana

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: .github/workflows/ci.yml:123-135, 147-152
- **Detail**: Suma porównywana jest z plikiem na serwerze, który `scp` nadpisuje przed wdrożeniem. Gdy `deploy.sh` albo `caddy reload` zawiedzie, następne uruchomienie widzi tę samą sumę i nie przeładuje — nowy, nieprzetestowany Caddyfile wejdzie dopiero przy restarcie kontenera (np. reboot VPS).
- **Fix**: Usunąć porównanie sum i zawsze wykonywać `caddy reload` po udanym `deploy.sh` (operacja idempotentna i tania).
- **Decision**: FIXED — porównanie sum usunięte, reload po każdym udanym deploy.sh (actionlint OK)

### F4 — Brak rotacji logów kontenerów

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: deploy/compose.yaml:7-27
- **Detail**: Domyślny sterownik `json-file` bez limitów — na małym VPS logi w końcu zapełnią dysk.
- **Fix**: Dodać `logging: {driver: json-file, options: {max-size: "10m", max-file: "3"}}` dla `app` i `caddy`.
- **Decision**: FIXED differently — na życzenie: rotacja dzienna, retencja 30 dni przez logrotate hosta (`deploy/logrotate-docker`, instalacja w runbooku §1; Docker nie rotuje po czasie). Zweryfikowane `logrotate --debug` na Ubuntu 24.04. Wymaga ręcznej instalacji na istniejącym serwerze.

### F5 — Obraz caddy:2 nigdy nie jest aktualizowany

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: deploy/compose.yaml:16, deploy/deploy.sh:118
- **Detail**: `deploy.sh` robi tylko `docker compose pull app`; publiczny terminator TLS zostaje na wersji pobranej przy pierwszym starcie.
- **Fix**: Przypiąć konkretną wersję (np. `caddy:2.10`) i opisać w runbooku ręczną aktualizację (`docker compose pull caddy && docker compose up -d caddy`).
- **Decision**: FIXED — `caddy:2.11` (najnowsza linia minor, 2.11.9 w Docker Hub) w compose.yaml; sekcja „Aktualizacja Caddy” w runbooku. Najbliższe wdrożenie pobierze `caddy:2.11` i odtworzy kontener Caddy.

### F6 — Caddy przepuszcza nagłówki Forwarded / X-Forwarded-Prefix

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: deploy/Caddyfile:4, src/main/resources/application-prod.properties:3
- **Detail**: `ForwardedHeaderFilter` honoruje też `Forwarded` (RFC 7239) i `X-Forwarded-Prefix`, których Caddy nie usuwa; klient może zmienić własne przekierowania/`redirect_uri`. Skutek tylko dla siebie (dostawcy OAuth odrzucą niezgodny callback).
- **Fix**: W Caddyfile `reverse_proxy app:8080 { header_up -Forwarded; header_up -X-Forwarded-Prefix }`.
- **Decision**: FIXED — `header_up -Forwarded`, `-X-Forwarded-Prefix`, `-X-Forwarded-Port` (`caddy validate` 2.11 OK)

### F7 — Higiena CI: token GHCR zostaje na serwerze, akcje przypięte tagami

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: .github/workflows/ci.yml:138-141, 63-75
- **Detail**: `docker login` zostawia wygasły `GITHUB_TOKEN` w `~deploy/.docker/config.json` (mylące błędy przy ręcznym pull). Akcje `docker/*` przypięte tagami, a job deploy ma klucz SSH do użytkownika z grupy `docker` (root-equivalent).
- **Fix**: Krok `if: always()` z `ssh prod docker logout ghcr.io`; przypiąć akcje `docker/*` do SHA.
- **Decision**: FIXED — krok „Wylogowanie serwera z GHCR” (`if: always()`); docker/setup-buildx v3.12.0, login v3.7.0, build-push v6.19.2 przypięte do SHA (bez zmiany wersji major; actionlint OK)

### F8 — Zerwane SSH może przerwać deploy.sh w połowie

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: deploy/deploy.sh:35, 112-142
- **Detail**: Przy anulowaniu joba / zerwaniu sieci zapis do stderr może dać SIGPIPE/SIGHUP po `set_tag`, przed przywróceniem — `.env` i `.previous_tag` niespójne.
- **Fix**: Ignorować HUP i PIPE (`trap '' HUP PIPE`) na początku skryptu.
- **Decision**: FIXED — `trap '' HUP PIPE` + `log()` z `|| true` (inaczej EPIPE na echo kończyłby skrypt przez `set -e`). Symulacja: z poprawką exit 0 i skrypt dochodzi do końca, bez niej exit 129. shellcheck OK.

### F9 — Mylący komentarz o concurrency na main

- **Severity**: 💡 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Pattern Consistency
- **Location**: .github/workflows/ci.yml:12-15
- **Detail**: Komentarz „main: każde uruchomienie kończy się” — GitHub i tak anuluje *oczekujące* uruchomienie, gdy w kolejce pojawi się nowsze; pośrednie commity mogą nie zostać wdrożone (nieszkodliwe, wygrywa najnowszy).
- **Fix**: Poprawić komentarz: „main: uruchomienie w toku nie jest anulowane; oczekujące zastępuje najnowsze”.
- **Decision**: FIXED
