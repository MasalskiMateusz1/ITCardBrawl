# Lessons Learned

> Append-only register of recurring rules and patterns. Re-read at start by /10x-frame, /10x-research, /10x-plan, /10x-plan-review, /10x-implement, /10x-impl-review.

## Cały backend pod /api (fallback SPA przepuszcza resztę bez logowania)

- **Context**: `SecurityConfig.spaRoutes()` + `SpaForwardController.isSpaRoute` (deploy-pipeline-skeleton, impl-review F1/F2).
- **Problem**: Reguła tras SPA to deny-lista — każdy GET spoza `/api`, `/oauth2`, `/login`, `/error` bez kropki w ostatnim segmencie dostaje `permitAll`. Endpoint backendu poza tymi prefiksami (np. actuator, nowy kontroler) po cichu staje się publiczny; a decyzja na surowym (niezdekodowanym) URI pozwalała ominąć auth przez `/%61pi/me`.
- **Rule**: Każdy endpoint backendu umieszczaj pod `/api/**` (OAuth2 Springa zostaje pod `/oauth2`, `/login`). Reguły bezpieczeństwa oparte na ścieżce licz na zdekodowanej, znormalizowanej ścieżce (`UrlPathHelper`), tak jak routing MVC — nigdy na `getRequestURI()`. Pilnuje tego `BackendRoutesTests`.
- **Applies to**: nowe kontrolery, actuator / narzędzia diagnostyczne, zmiany `SecurityConfig` i `SpaForwardController`.
