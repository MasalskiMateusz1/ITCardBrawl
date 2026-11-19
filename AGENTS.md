# Repository Guidelines

ItCardBrawl is a browser card game. It has a Spring Boot 4 / Java 21 JSON API (`src/`) and an Angular 22 SPA (`frontend/`). In production the SPA ships inside the Spring jar as a single Docker image.

## Hard Rules

- Put every backend endpoint under `/api/**`. Path-based security must use the decoded path, never `getRequestURI()`. `BackendRoutesTests` enforces this. See @context/foundation/lessons.md.
- Game rules and the opponent live in Java (`game/`). Keep the SPA thin.
- Never hardcode UI text. Add each string to `frontend/src/app/i18n/messages.ts` and to both `pl.ts` and `en.ts`.
- Every `name`/`description`/`text` in `src/main/resources/game/factions.json` needs both `pl` and `en`. The app refuses to start otherwise.
- Never commit `.env.properties` (OAuth secrets). Copy it from `.env.properties.example`.

## Project Structure

- Backend `com.example.itcardbrawl`: `api/` (controllers, views), `config/` (security, locale), `game/` (domain, catalog), `web/` (SPA fallback).
- Frontend `frontend/src/app/`: one folder per screen (`lobby/`, `new-match/`, `match-table/`, `sign-in/`) plus `i18n/` and `game/`.
- `context/` holds the planning workflow: `foundation/` (PRD, roadmap, lessons), `changes/<id>/` (active plans), `archive/`.
- Deployment: @Dockerfile, `deploy/` (@deploy/README.md).

## Commands

- `./mvnw -B verify` runs the backend tests (CI gate).
- `./mvnw spring-boot:run` starts the API on :8080. It needs OAuth credentials; see @frontend/README.md.
- `cd frontend && npm start` starts the dev server on :4200, proxying `/api` and `/oauth2` (@frontend/proxy.conf.json).
- `cd frontend && npx ng test --watch=false` runs the frontend tests (CI gate).
- `cd frontend && npm run build` runs the production build (CI gate).

## Coding Style

- Java: tabs. Comments, javadoc and validation messages are in Polish.
- TypeScript: Prettier (@frontend/.prettierrc, width 100, single quotes), 2-space indent, English comments.
- Angular: standalone components with signals, split into `name.ts/.html/.css/.spec.ts` per file. Interceptors are functional (`HttpInterceptorFn`).

## Testing

- Backend tests use JUnit plus MockMvc (`*Tests.java` in `src/test/java`). Run a single class with `./mvnw -B test -Dtest=MatchControllerTests`.
- Frontend tests use Vitest via `ng test` with co-located `*.spec.ts` files. Run a single file with `--include src/app/<path>.spec.ts`.
- jsdom reports `en-US`. Specs that assert Polish must store `pl` under `LANGUAGE_STORAGE_KEY` before the first `TestBed.inject` and clear `localStorage` in `afterEach`.
- HTTP specs call `httpTesting.verify()` in `afterEach`.

## Commits

- Conventional Commits scoped to the change id, with a Polish title, e.g. `feat(<change-id>): Stół partii (p4)`. Also used: `fix`, `chore(archive)`, `docs(roadmap)`.
- CI (@.github/workflows/ci.yml) runs on PRs and on `main`. Each push to `main` builds the image and deploys automatically.
