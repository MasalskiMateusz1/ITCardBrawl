# Szkielet wdrożenia (F-02) — Plan Brief

> Full plan: `context/changes/deploy-pipeline-skeleton/plan.md`

## What & Why

Każdy merge do `main` ma automatycznie trafiać na publiczny serwer, a wdrożona gra ma się otwierać w przeglądarce pod własną domeną z HTTPS (roadmapa F-02; PRD: gra w przeglądarce bez instalacji). Dzięki temu każdy kolejny wycinek (S-02…S-06) da się sprawdzić na prawdziwym środowisku, a znajomi z IT mogą zagrać.

## Starting Point

Działa lokalnie: Spring Boot API (logowanie GitHub/Google z S-01) i Angular SPA przez proxy `ng serve`. Nie ma Dockerfile'a, CI ani serwera; `SecurityConfig` blokuje wszystko poza `/api/ping`, więc spakowane SPA dostałoby 401. Repo na GitHubie jest puste, a lokalna gałąź to `master`.

## Desired End State

Merge do `main` → testy → obraz w GHCR → wdrożenie na VPS → smoke `https://<domena>/api/ping` i `/`. Gdy nowa wersja nie wstanie albo smoke zawiedzie, serwer sam wraca do poprzedniej wersji. Gracz otwiera `https://<domena>/`, loguje się produkcyjną aplikacją OAuth i widzi lobby.

## Key Decisions Made

| Decision | Choice | Why (1 sentence) |
| --- | --- | --- |
| Serwer | Tani VPS z Linuksem + Docker | Prawdziwy self-host zgodny z tech-stack.md, uczy całej ścieżki. |
| Domena / TLS | Własna domena + Caddy (Let's Encrypt) | HTTPS bez konfiguracji; Google OAuth wymaga https poza localhostem. |
| Mechanizm wdrożenia | GHCR + `docker compose pull` przez SSH | Serwer nic nie buduje, obrazy wersjonowane po SHA. |
| Gałąź | Zmiana `master` → `main` | Zgodność z roadmapą i tech-stack.md. |
| Pakowanie SPA | W jarze Springa (`static/`) | Jedno źródło i sesja w cookie, jak w dev; realizuje hand-off z F-01. |
| Bramka CI | Testy na PR i main, deploy tylko z main | Czerwony merge nigdy nie trafia na produkcję. |
| Przygotowanie serwera | Runbook + compose w repo, ręcznie | Każdy krok zrozumiały (cel `learn`), jedna nowa technologia naraz. |
| OAuth produkcyjny | Osobne aplikacje GitHub/Google | Wyciek sekretu dev nie zagraża produkcji; GitHub App ma jeden callback. |
| Rollback | Automatyczny (healthcheck w `deploy.sh` + smoke z CI) | Zepsuta wersja nie zostaje na produkcji. |
| Weryfikacja | Smoke `/api/ping` + `/` zawiera `<app-root>` | Sprawdza DNS → Caddy → TLS → Spring i spakowane SPA. |

## Scope

**In scope:**
- Serwowanie SPA przez Springa (permitAll, forward tras Angulara, profil `prod` za proxy)
- Wieloetapowy `Dockerfile`, `.dockerignore`
- Workflow GitHub Actions: test → image → deploy (z rollbackiem i smoke)
- `deploy/compose.yaml`, `Caddyfile`, `deploy.sh`, runbook `deploy/README.md`

**Out of scope:**
- Zero-downtime, trwałe sesje (deploy wylogowuje graczy), staging
- Automatyzacja serwera (cloud-init/Ansible), obserwowalność produkcyjna
- Redeploy dowolnego SHA z workflow (ręcznie z runbooka)

## Architecture / Approach

`GitHub Actions` → job `test` (Maven + Angular) → job `image` (build → `ghcr.io/masalskimateusz1/itcardbrawl:<sha>`) → job `deploy` (scp plików `deploy/` → `ssh deploy.sh <sha>` → smoke z runnera → przy porażce `deploy.sh rollback`). Na VPS: `Caddy :443` (TLS, `X-Forwarded-*`) → `app:8080` (Spring z SPA w `static/`, profil `prod`).

## Phases at a Glance

| Phase | What it delivers | Key risk |
| --- | --- | --- |
| 1. Spring serwuje SPA | SPA z jara, forward tras, `https` redirect_uri za proxy | Za szerokie `permitAll` osłabi default-deny |
| 2. Obraz kontenera | Działający lokalnie obraz z healthcheckiem | Niezgodność wersji npm (`packageManager: npm@12.1.0`) |
| 3. CI — testy | `main` jako domyślna, zielone testy na PR/push | `ng test` w trybie watch zawiesza job |
| 4. Serwer i skrypt | VPS + Caddy + `deploy.sh` z rollbackiem, ręczny deploy | Konfiguracja ręczna (DNS, firewall, OAuth) — najwięcej kroków ręcznych |
| 5. CD z main | Automatyczny deploy, smoke, rollback | Sekrety SSH/known_hosts i logowanie do GHCR na serwerze |

**Prerequisites:** zakup VPS i domeny (faza 4), dostęp do konsoli GitHub OAuth Apps i Google Cloud, konto GitHub z repo `ITCardBrawl`.
**Estimated effort:** ~3–4 sesje: fazy 1–3 to praca w repo, faza 4 to głównie ręczna konfiguracja, faza 5 domyka pipeline.

## Open Risks & Assumptions

- Zakładamy, że obraz `eclipse-temurin:21-jre` ma narzędzie do HEALTHCHECK (curl/wget) — inaczej trzeba je doinstalować (sprawdzane w fazie 2).
- Pierwszy deploy nie ma poprzedniej wersji — rollback niemożliwy, job tylko zgłasza błąd.
- Każde wdrożenie wylogowuje graczy (sesje w pamięci) — akceptowalne w M-1.
- Konkretny dostawca VPS i domena zostaną wybrane w fazie 4; plan jest od nich niezależny (`APP_DOMAIN`).

## Success Criteria (Summary)

- Zmiana zmergowana do `main` sama pojawia się na `https://<domena>/`.
- Gracz loguje się na produkcji GitHubem lub Google i widzi lobby, w czterech przeglądarkach.
- Zepsuta wersja nie zostaje na produkcji — pipeline jest czerwony, gra działa na poprzedniej wersji.
