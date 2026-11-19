# Szkielet wdrożenia (F-02) — plan implementacji

## Overview

Gra ma być automatycznie wdrażana na publiczny serwer po każdym merge do `main`, a wdrożona wersja ma się otwierać w przeglądarce pod własną domeną z HTTPS. API i SPA są budowane w jednym obrazie Dockera (SPA w jarze Springa), GitHub Actions testuje każdy PR i push, a z `main` publikuje obraz w GHCR i wdraża go przez SSH na VPS (Caddy + Let's Encrypt), z automatycznym rollbackiem i publicznym smoke testem. Roadmapa: F-02 (`deploy-pipeline-skeleton`), PRD: NFR „bez instalacji”, Guardrails.

## Current State Analysis

- Brak `Dockerfile`, `.dockerignore`, `.github/workflows/` i `context/foundation/infrastructure.md` — infrastruktura nie istnieje.
- `SecurityConfig.java:18-19` — `permitAll` tylko dla `/api/ping`, `anyRequest().authenticated()` dla reszty; spakowane SPA (`/`, `index.html`, bundle) dostałoby 401 (hand-off z impl-review F-01, roadmapa F-02).
- Trasy Angulara (`frontend/src/app/app.routes.ts`): `''` (Lobby, guard), `sign-in`, `**` → `''`. Odświeżenie `/sign-in` na serwerze bez fallbacku do `index.html` da 401/404.
- `ng build` → `frontend/dist/itcardbrawl/browser/` (`index.html` zawiera `<app-root>`).
- OAuth: sekrety z env/`.env.properties` (`application.properties`), callbacki tylko dev `http://localhost:4200/login/oauth2/code/{github,google}`. Za reverse proxy z TLS bez obsługi nagłówków `X-Forwarded-*` Spring zbuduje `redirect_uri` z `http://`.
- Testy używają atrapowych rejestracji OAuth (`src/test/resources/config/application.properties`) — `mvnw verify` w CI nie potrzebuje sekretów.
- Repo: lokalna gałąź `master`, `origin` = `github.com/MasalskiMateusz1/ITCardBrawl` jest **pusty** (`git ls-remote` nic nie zwraca). Lokalnie: Java 21, Docker 29, Node 24, `gh` zalogowany.
- `frontend/package.json` deklaruje `packageManager: npm@12.1.0`; testy frontu to Vitest przez `ng test`.

## Desired End State

- Merge do `main` → workflow: testy (Maven + Angular) → obraz `ghcr.io/masalskimateusz1/itcardbrawl:<sha>` → deploy na VPS → smoke `https://<domena>/api/ping` (`{"status":"ok"}`) i `https://<domena>/` (200 z `<app-root>`). Czerwony krok = brak wdrożenia albo automatyczny powrót do poprzedniej wersji.
- PR → te same testy, bez budowania obrazu i bez wdrożenia.
- Na `https://<domena>/` niezalogowany gracz trafia na `/sign-in`, loguje się GitHubem lub Google (produkcyjne aplikacje OAuth) i widzi lobby ze swoją nazwą; odświeżenie `/sign-in` działa.
- Runbook w repo pozwala od zera postawić serwer i odtworzyć konfigurację.

### Key Discoveries:

- `SecurityConfig.java:18-19` — default-deny do rozszerzenia o zasoby SPA (`context/archive/2026-11-16-spa-api-walking-skeleton/reviews/impl-review.md:26-34`).
- `PingController.java` — `/api/ping` → `{"status":"ok"}`, publiczny; zarezerwowany jako health check (`context/archive/2026-11-18-external-login-gate/plan.md:210`).
- `SecurityFlowTests.java` — wzorzec testów MockMvc z dekodowaniem `redirect_uri`; do powtórzenia dla profilu `prod` i nagłówków `X-Forwarded-*`.
- `frontend/proxy.conf.json` — zarezerwowane prefiksy backendu: `/api`, `/oauth2`, `/login/oauth2`; fallback SPA nie może ich przechwytywać.

## What We're NOT Doing

- Zero-downtime / blue-green — akceptujemy kilka sekund przerwy przy restarcie kontenera.
- Trwałe sesje — sesje są w pamięci, każde wdrożenie wylogowuje graczy (akceptowalne w M-1).
- Automatyzacja konfiguracji serwera (cloud-init, Ansible) — serwer stawiany ręcznie według runbooka.
- Obserwowalność produkcyjna (śledzenie błędów, metryki, alerty) — w roadmapie Parked.
- Środowisko staging i podgląd PR-ów.
- Ręczny workflow do redeployu dowolnego SHA — ręczny rollback to polecenie z runbooka na serwerze.
- Serwowanie SPA przez Caddy — SPA jest w jarze Springa.
- Zmiany w logice logowania S-01 poza konfiguracją produkcyjną.

## Implementation Approach

Od środka na zewnątrz, każda faza weryfikowalna samodzielnie: najpierw Spring umie serwować SPA i działać za proxy TLS (testy MockMvc), potem obraz działa lokalnie, potem CI daje zielone testy na GitHubie, potem serwer działa z ręcznie wdrożonym obrazem, a na końcu CD spina wszystko automatycznie. Kroki SSH w workflow piszemy zwykłym `ssh`/`scp` (bez akcji firm trzecich) — cel `learn`.

## Critical Implementation Details

**Dispatchery Spring Security.** `authorizeHttpRequests` obejmuje też dispatche FORWARD i ERROR: forward na `/index.html` musi być dozwolony, a brakujący plik statyczny (np. stary chunk JS) powinien dać 404, nie 401 — dlatego dispatch `ERROR` (strona `/error`) ma być `permitAll`.

**Kolejność w `deploy.sh`.** Poprzedni tag trzeba zapisać **przed** podmianą, a pierwszy deploy (brak poprzedniego tagu) nie ma do czego wracać — skrypt kończy się wtedy błędem bez rollbacku. Smoke z CI jest drugą linią obrony: jego porażka wywołuje `deploy.sh rollback` przez SSH.

**Logowanie do GHCR na serwerze.** Serwer pobiera obraz, logując się tokenem `GITHUB_TOKEN` z bieżącego joba (przekazanym przez stdin SSH, `packages: read`) — na serwerze nie leży długowieczny token. Obrazy poprzednich wersji zostają w lokalnym cache Dockera, więc rollback nie wymaga logowania.

## Faza 1: Spring serwuje SPA

### Overview

Spring zwraca zbudowane SPA z `classpath:/static/`, przekierowuje (forward) trasy Angulara na `index.html` i poprawnie buduje adresy `https://` za reverse proxy w profilu `prod`.

### Changes Required:

#### 1. Fallback tras SPA

**File**: `src/main/java/com/example/itcardbrawl/web/SpaForwardController.java` (nowy)

**Intent**: Żądania GET na trasy SPA (np. `/sign-in`, przyszłe trasy gry) zwracają `index.html`, żeby odświeżenie i głębokie linki działały.

**Contract**: GET dla ścieżek bez kropki w ostatnim segmencie, poza prefiksami `/api`, `/oauth2`, `/login`, `/error` → `forward:/index.html`. `/` obsługuje mechanizm welcome page Springa (albo ten sam kontroler). Pliki z kropką (bundle, `favicon.ico`) obsługuje domyślny handler zasobów.

#### 2. Reguły bezpieczeństwa dla zasobów SPA

**File**: `src/main/java/com/example/itcardbrawl/config/SecurityConfig.java`

**Intent**: Publiczny dostęp do statycznych plików SPA i jego tras (auth sprawdza guard Angulara przez `/api/me`); API zostaje chronione, default-deny zostaje.

**Contract**: `permitAll` dla: GET `/`, `/index.html`, `/favicon.ico`, plików statycznych w katalogu głównym (`/*.js`, `/*.css`, `/media/**`), tras SPA zgodnych z regułą z punktu 1, oraz `DispatcherType.FORWARD` i `DispatcherType.ERROR`. `/api/ping` → `permitAll` bez zmian; `anyRequest().authenticated()` bez zmian. Reszta łańcucha (oauth2Login, csrf spa, logout, entry point 401) bez zmian.

#### 3. Profil produkcyjny

**File**: `src/main/resources/application-prod.properties` (nowy)

**Intent**: Za Caddy (TLS terminowany na proxy) Spring ma brać schemat i host z `X-Forwarded-*`, a cookie sesji ma być `Secure`.

**Contract**: `server.forward-headers-strategy=framework`, `server.servlet.session.cookie.secure=true`. Profil włączany zmienną `SPRING_PROFILES_ACTIVE=prod` (w compose, faza 4).

#### 4. Testy

**File**: `src/test/java/com/example/itcardbrawl/web/SpaForwardTests.java` (nowy), `src/test/java/com/example/itcardbrawl/config/ProdProfileTests.java` (nowy)

**Intent**: Zablokować regresję reguł z punktów 1–3.

**Contract**: bez logowania: GET `/sign-in` → 200 i forward na `/index.html`; GET `/api/me` → 401; GET `/nie-istnieje.js` → 404 (nie 401); POST `/sign-in` → nie 200. Profil `prod` (`@ActiveProfiles("prod")`): `GET /oauth2/authorization/github` z `X-Forwarded-Proto: https` i `X-Forwarded-Host: gra.example.com` → `redirect_uri=https://gra.example.com/login/oauth2/code/github`. Testy nie wymagają zbudowanego frontu — tam, gdzie potrzebny jest plik, użyć `src/test/resources/static/index.html` jako atrapy.

### Success Criteria:

#### Automated Verification:

- Testy backendu przechodzą: `./mvnw verify`
- Testy frontu przechodzą: `cd frontend && npx ng test --watch=false`

#### Manual Verification:

- Po skopiowaniu `frontend/dist/itcardbrawl/browser/*` do `target/classes/static/` i `./mvnw spring-boot:run` strona `http://localhost:8080/sign-in` ładuje SPA, a odświeżenie jej nie daje 401

**Implementation Note**: Po zielonej weryfikacji automatycznej zatrzymaj się na ręczne potwierdzenie przed fazą 2.

---

## Faza 2: Obraz kontenera

### Overview

Jeden wieloetapowy `Dockerfile` buduje SPA, jar z SPA w `static/` i lekki obraz uruchomieniowy.

### Changes Required:

#### 1. Dockerfile

**File**: `Dockerfile` (nowy, katalog główny)

**Intent**: Powtarzalny build całej aplikacji bez lokalnego Node/Mavena.

**Contract**: etapy: (1) `node:24` — `npm ci` + `npm run build` w `frontend/`; (2) `eclipse-temurin:21-jdk` — `./mvnw -B package -DskipTests` z wynikiem etapu 1 skopiowanym do `src/main/resources/static/` przed buildem (testy biegną w osobnym jobie CI); (3) `eclipse-temurin:21-jre` — sam jar, użytkownik nie-root, `EXPOSE 8080`, `HEALTHCHECK` odpytujący `http://localhost:8080/api/ping`. Warstwy cache: `package*.json`/`pom.xml` kopiowane przed źródłami. Wersja npm zgodna z `packageManager` (`npm@12.1.0`) — doinstalować w etapie 1, jeśli obraz Node ma starszą.

#### 2. .dockerignore

**File**: `.dockerignore` (nowy)

**Intent**: Mały kontekst buildu i brak sekretów w obrazie.

**Contract**: wykluczyć co najmniej `target/`, `frontend/node_modules/`, `frontend/dist/`, `frontend/.angular/`, `.git/`, `.idea/`, `.claude/`, `context/`, `.env.properties`.

### Success Criteria:

#### Automated Verification:

- Obraz się buduje: `docker build -t itcardbrawl:local .`
- W obrazie nie ma sekretów: `docker run --rm --entrypoint sh itcardbrawl:local -c "find / -name .env.properties 2>/dev/null"` nic nie zwraca
- Kontener startuje i jest zdrowy: `docker run -d --name icb -p 8080:8080 -e GITHUB_CLIENT_ID=x -e GITHUB_CLIENT_SECRET=x -e GOOGLE_CLIENT_ID=x -e GOOGLE_CLIENT_SECRET=x itcardbrawl:local`, a po ~30 s `docker inspect -f '{{.State.Health.Status}}' icb` = `healthy`
- `curl -s http://localhost:8080/api/ping` zwraca `{"status":"ok"}`, a `curl -s http://localhost:8080/` zawiera `<app-root>`

#### Manual Verification:

- `http://localhost:8080/` w przeglądarce przekierowuje na `/sign-in` i pokazuje przyciski logowania (logowanie z atrapowymi sekretami nie musi działać)

**Implementation Note**: Po zielonej weryfikacji automatycznej zatrzymaj się na ręczne potwierdzenie przed fazą 3.

---

## Faza 3: CI — testy na PR i main

### Overview

Gałąź `main` staje się domyślną na GitHubie, a każdy PR i push uruchamia testy backendu, frontu i build produkcyjny SPA.

### Changes Required:

#### 1. Gałąź `main`

**File**: (git, bez plików)

**Intent**: Zgodność z roadmapą i tech-stack.md („merge do main”).

**Contract**: `git branch -m master main`, `git push -u origin main`; na GitHubie `main` jest gałęzią domyślną (pierwsza wypchnięta gałąź w pustym repo). Ochrona gałęzi (wymagany zielony check `test`) — opcjonalnie, opisane w runbooku.

#### 2. Workflow

**File**: `.github/workflows/ci.yml` (nowy)

**Intent**: Szybka informacja zwrotna przed merge i bramka przed wdrożeniem (job `test` jest wymagany przez joby z fazy 5).

**Contract**: `on: pull_request` (do `main`) i `push` (na `main`). Job `test` (ubuntu-latest): `actions/setup-java` (temurin 21, cache maven) → `./mvnw -B verify`; `actions/setup-node` (24, cache npm, `frontend/package-lock.json`) → `npm ci`, `npx ng test --watch=false`, `npm run build` w `frontend/`. `permissions: contents: read`. `concurrency` per gałąź z anulowaniem starszych uruchomień dla PR.

### Success Criteria:

#### Automated Verification:

- `gh run list --workflow ci.yml --branch main --limit 1` pokazuje zakończone uruchomienie ze statusem `success`
- `gh api repos/MasalskiMateusz1/ITCardBrawl --jq .default_branch` zwraca `main`

#### Manual Verification:

- Testowy PR z celowo zepsutym testem pokazuje czerwony check `test`; po cofnięciu zmiany check jest zielony

**Implementation Note**: Po zielonej weryfikacji automatycznej zatrzymaj się na ręczne potwierdzenie przed fazą 4.

---

## Faza 4: Serwer i skrypt wdrożenia

### Overview

Pliki serwera w repo (compose, Caddyfile, skrypt wdrożenia z automatycznym rollbackiem) i runbook; autor ręcznie stawia VPS, domenę i produkcyjne aplikacje OAuth, a następnie ręcznie wdraża obraz z fazy 2 przez GHCR.

### Changes Required:

#### 1. Compose

**File**: `deploy/compose.yaml` (nowy)

**Intent**: Definicja środowiska produkcyjnego: aplikacja + reverse proxy.

**Contract**: usługa `app`: `image: ghcr.io/masalskimateusz1/itcardbrawl:${APP_TAG}`, `env_file: app.env`, `restart: unless-stopped`, healthcheck z obrazu, **bez** publikowanych portów. Usługa `caddy`: `caddy:2`, porty `80`, `443` (+ `443/udp`), montuje `./Caddyfile`, wolumeny `caddy_data`/`caddy_config`, `APP_DOMAIN` ze środowiska. `APP_TAG` i `APP_DOMAIN` w pliku `.env` obok compose (zarządzany przez `deploy.sh`/runbook, nie w repo).

#### 2. Caddyfile

**File**: `deploy/Caddyfile` (nowy)

**Intent**: TLS z Let's Encrypt i przekazanie ruchu do Springa z nagłówkami `X-Forwarded-*`.

**Contract**: `{$APP_DOMAIN} { reverse_proxy app:8080 }` (Caddy domyślnie ustawia `X-Forwarded-Proto/Host`).

#### 3. Skrypt wdrożenia

**File**: `deploy/deploy.sh` (nowy)

**Intent**: Jedno idempotentne polecenie wdrożenia z automatycznym powrotem do poprzedniej wersji, gdy nowa nie stanie się zdrowa.

**Contract**: `deploy.sh <tag>`: zapisuje bieżący `APP_TAG` do `.previous_tag`, ustawia nowy `APP_TAG` w `.env`, `docker compose pull app`, `docker compose up -d`, czeka do 90 s na `health=healthy` kontenera `app`; przy porażce przywraca poprzedni tag, `up -d`, kończy kodem ≠ 0 (pierwszy deploy bez poprzedniego tagu: tylko błąd). `deploy.sh rollback`: przywraca tag z `.previous_tag` i czeka na zdrowie. Na końcu sukcesu: `docker image prune` z zachowaniem bieżącego i poprzedniego obrazu. `set -euo pipefail`, działa w katalogu skryptu (`/opt/itcardbrawl`).

#### 4. Runbook

**File**: `deploy/README.md` (nowy)

**Intent**: Odtwarzalna, ręczna instrukcja postawienia produkcji (cel `learn`).

**Contract**: sekcje: (1) VPS — wybór (Ubuntu LTS, ≥1 GB RAM), aktualizacje, firewall (22/80/443), użytkownik `deploy` z kluczem SSH w grupie `docker`, instalacja Docker Engine + compose plugin, katalog `/opt/itcardbrawl`; (2) domena — rekord A na IP serwera; (3) produkcyjne aplikacje OAuth — nowa GitHub OAuth App i nowy klient Google z callbackami `https://<domena>/login/oauth2/code/{github,google}`; (4) `app.env` na serwerze (`SPRING_PROFILES_ACTIVE=prod`, 4 sekrety OAuth, `chmod 600`) i `.env` (`APP_DOMAIN`, `APP_TAG`); (5) sekrety/zmienne GitHuba dla fazy 5 (`DEPLOY_HOST`, `DEPLOY_USER`, `DEPLOY_SSH_KEY`, `DEPLOY_KNOWN_HOSTS`, zmienna `APP_DOMAIN`, environment `production`); (6) ręczny deploy i ręczny rollback (`./deploy.sh <sha>`, `./deploy.sh rollback`), podgląd logów (`docker compose logs -f app`); (7) opcjonalna ochrona gałęzi `main`.

#### 5. Dokumentacja projektu

**File**: `.env.properties.example`, `context/foundation/tech-stack.md`

**Intent**: Wskazać, że produkcja ma osobne aplikacje OAuth, i zapisać wybory infrastrukturalne.

**Contract**: w `.env.properties.example` komentarz kierujący do `deploy/README.md` dla produkcji; w `tech-stack.md` §Post-bootstrap notes wpis z datą: VPS + Docker Compose + Caddy, GHCR, deploy przez SSH, SPA w jarze.

### Success Criteria:

#### Automated Verification:

- Skrypt przechodzi lint: `docker run --rm -v "$PWD/deploy:/mnt" koalaman/shellcheck:stable /mnt/deploy.sh`
- Compose jest poprawny: `APP_TAG=x APP_DOMAIN=example.com docker compose -f deploy/compose.yaml config` (z pustym `deploy/app.env` utworzonym tymczasowo) kończy się kodem 0

#### Manual Verification:

- VPS, DNS i produkcyjne aplikacje OAuth skonfigurowane według runbooka
- Obraz z fazy 2 wypchnięty ręcznie do GHCR i `./deploy.sh <tag>` na serwerze kończy się sukcesem; `https://<domena>/api/ping` zwraca `{"status":"ok"}` z ważnym certyfikatem
- Logowanie GitHubem i Google na `https://<domena>/` prowadzi do lobby z nazwą gracza
- `./deploy.sh nieistniejacy-tag` (albo tag obrazu, który nie startuje) kończy się błędem, a działająca wersja zostaje lub wraca — `/api/ping` nadal odpowiada

**Implementation Note**: Po zielonej weryfikacji automatycznej zatrzymaj się na ręczne potwierdzenie przed fazą 5.

---

## Faza 5: CD z main

### Overview

Push na `main` po zielonych testach buduje obraz, publikuje go w GHCR, wdraża na VPS, sprawdza publiczny adres i przy porażce wraca do poprzedniej wersji.

### Changes Required:

#### 1. Job obrazu

**File**: `.github/workflows/ci.yml`

**Intent**: Wersjonowany obraz tylko z kodu, który przeszedł testy.

**Contract**: job `image`: `needs: test`, tylko `push` na `main`; `permissions: packages: write`; `docker/login-action` (GHCR, `GITHUB_TOKEN`) + `docker/build-push-action` z tagami `ghcr.io/masalskimateusz1/itcardbrawl:${{ github.sha }}` i `:latest`, cache warstw `type=gha`. Nazwa obrazu małymi literami.

#### 2. Job wdrożenia

**File**: `.github/workflows/ci.yml`

**Intent**: Automatyczne wdrożenie i jego weryfikacja z zewnątrz.

**Contract**: job `deploy`: `needs: image`, `environment: production`, `concurrency: { group: production, cancel-in-progress: false }`, `permissions: packages: read`. Kroki (zwykłe `ssh`/`scp` z kluczem z `DEPLOY_SSH_KEY` i `DEPLOY_KNOWN_HOSTS`): (1) `scp` `deploy/compose.yaml`, `deploy/Caddyfile`, `deploy/deploy.sh` do `/opt/itcardbrawl`; (2) `docker login ghcr.io` na serwerze z `GITHUB_TOKEN` przez stdin; (3) `./deploy.sh ${{ github.sha }}`, a po zmianie Caddyfile przeładowanie Caddy; (4) smoke z runnera: do ~60 s ponawiać `curl https://${APP_DOMAIN}/api/ping` aż do `{"status":"ok"}`, potem `curl https://${APP_DOMAIN}/` musi zawierać `<app-root>`; (5) `if: failure()` po kroku 4 → `ssh ... ./deploy.sh rollback`. Job kończy się na czerwono przy każdej porażce.

### Success Criteria:

#### Automated Verification:

- Workflow przechodzi walidację: `docker run --rm -v "$PWD:/repo" -w /repo rhysd/actionlint:latest`
- Po merge do `main` najnowsze uruchomienie `ci.yml` ma joby `test`, `image`, `deploy` zakończone `success` (`gh run view --json jobs`)
- Obraz `:<sha>` istnieje w GHCR: `docker manifest inspect ghcr.io/masalskimateusz1/itcardbrawl:<sha>` kończy się kodem 0

#### Manual Verification:

- Widoczna zmiana (np. tekst w lobby) zmergowana do `main` pojawia się na `https://<domena>/` bez ręcznych kroków
- PR nie uruchamia jobów `image` ani `deploy`
- Celowo zepsuta wersja (np. aplikacja nie startuje) zmergowana na próbę: job `deploy` jest czerwony, a `https://<domena>/api/ping` nadal odpowiada poprzednią wersją; potem naprawa przez kolejny merge
- Pełna ścieżka na wdrożonej wersji w czterech przeglądarkach (Chrome, Firefox, Safari/WebKit, Edge): logowanie → lobby → wylogowanie

**Implementation Note**: Po zielonej weryfikacji automatycznej zatrzymaj się na ręczne potwierdzenie.

---

## Testing Strategy

### Unit Tests:

- Brak nowej logiki domenowej — warstwę Springa pokrywają testy MockMvc z fazy 1.

### Integration Tests:

- MockMvc: forward tras SPA, 401 dla API bez logowania, 404 dla brakujących plików, `redirect_uri` z `https://` w profilu `prod` za nagłówkami `X-Forwarded-*`.
- Kontener lokalnie (faza 2): healthcheck, `/api/ping`, `/` z `<app-root>`.
- Smoke w CD (faza 5): publiczna ścieżka DNS → Caddy → TLS → Spring.

### Manual Testing Steps:

1. Odświeżenie `/sign-in` na wdrożonej wersji ładuje SPA.
2. Logowanie GitHubem i Google na produkcji kończy się lobby z nazwą gracza; wylogowanie wraca do `/sign-in`.
3. Celowo zepsuty deploy kończy się automatycznym rollbackiem.
4. Weryfikacja w czterech przeglądarkach (NFR PRD).

## Performance Considerations

- VPS ≥ 1 GB RAM — JVM w kontenerze; obraz nie jest budowany na serwerze. Ewentualne ograniczenie pamięci JVM (`-XX:MaxRAMPercentage`) dopiero przy problemach.
- Cache warstw Dockera (`type=gha`) i cache maven/npm w `setup-*` skracają pipeline.

## Migration Notes

- Brak danych do migracji. Po fazie 3 lokalna gałąź to `main` — inne klony trzeba przełączyć (`git branch -m master main`).
- Każde wdrożenie restartuje aplikację i kasuje sesje w pamięci (gracze logują się ponownie).
- Dev bez zmian: `ng serve` + proxy i `.env.properties` z callbackami `localhost:4200`.

## References

- Roadmapa: `context/foundation/roadmap.md` — F-02 (Outcome, Unknowns, hand-off z F-01)
- Tech stack: `context/foundation/tech-stack.md` — `deployment_target: self-host`, `ci_default_flow: auto-deploy-on-merge`
- Hand-off F-01: `context/archive/2026-11-16-spa-api-walking-skeleton/reviews/impl-review.md:26-34`
- Logowanie S-01: `context/archive/2026-11-18-external-login-gate/plan.md`
- Łańcuch bezpieczeństwa: `src/main/java/com/example/itcardbrawl/config/SecurityConfig.java:15-33`
- Wzorzec testów: `src/test/java/com/example/itcardbrawl/config/SecurityFlowTests.java`

## Progress

> Convention: `- [ ]` pending, `- [x]` done. Append ` — <commit sha>` when a step lands. Do not rename step titles. See `references/progress-format.md`.

### Phase 1: Spring serwuje SPA

#### Automated

- [x] 1.1 Testy backendu przechodzą: `./mvnw verify` — cac0a06
- [x] 1.2 Testy frontu przechodzą: `cd frontend && npx ng test --watch=false` — cac0a06

#### Manual

- [x] 1.3 Lokalnie `http://localhost:8080/sign-in` ładuje SPA z jara, odświeżenie nie daje 401 — cac0a06

### Phase 2: Obraz kontenera

#### Automated

- [x] 2.1 Obraz się buduje: `docker build -t itcardbrawl:local .` — ce7041a
- [x] 2.2 W obrazie nie ma sekretów (`.env.properties`) — ce7041a
- [x] 2.3 Kontener startuje i jest `healthy` — ce7041a
- [x] 2.4 `/api/ping` zwraca `{"status":"ok"}`, a `/` zawiera `<app-root>` — ce7041a

#### Manual

- [x] 2.5 `http://localhost:8080/` przekierowuje na `/sign-in` z przyciskami logowania — ce7041a

### Phase 3: CI — testy na PR i main

#### Automated

- [x] 3.1 Ostatnie uruchomienie `ci.yml` na `main` zakończone `success` — d571eeb
- [x] 3.2 Gałąź domyślna na GitHubie to `main` — d571eeb

#### Manual

- [x] 3.3 PR z zepsutym testem ma czerwony check `test`, po cofnięciu zielony — d571eeb

### Phase 4: Serwer i skrypt wdrożenia

#### Automated

- [x] 4.1 `deploy.sh` przechodzi shellcheck — d6299ae
- [x] 4.2 `docker compose -f deploy/compose.yaml config` kończy się kodem 0 — d6299ae

#### Manual

- [x] 4.3 VPS, DNS i produkcyjne aplikacje OAuth skonfigurowane według runbooka — d6299ae
- [x] 4.4 Ręczny `./deploy.sh <tag>` działa, `https://<domena>/api/ping` odpowiada z ważnym certyfikatem — d6299ae
- [x] 4.5 Logowanie GitHubem i Google na produkcji prowadzi do lobby — d6299ae
- [x] 4.6 Nieudany deploy zostawia lub przywraca działającą wersję — d6299ae

### Phase 5: CD z main

#### Automated

- [x] 5.1 Workflow przechodzi actionlint — b3e0281
- [x] 5.2 Joby `test`, `image`, `deploy` zakończone `success` po merge do `main` — b3e0281
- [x] 5.3 Obraz `:<sha>` istnieje w GHCR — b3e0281

#### Manual

- [x] 5.4 Zmiana zmergowana do `main` pojawia się na produkcji bez ręcznych kroków — b3e0281
- [x] 5.5 PR nie uruchamia jobów `image` ani `deploy` — b3e0281
- [x] 5.6 Zepsuta wersja: `deploy` czerwony, produkcja działa na poprzedniej wersji — b3e0281
- [x] 5.7 Logowanie → lobby → wylogowanie działa w czterech przeglądarkach — b3e0281
