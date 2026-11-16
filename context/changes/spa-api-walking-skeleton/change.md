---
change_id: spa-api-walking-skeleton
title: Szkielet SPA (Angular) rozmawiającego z API przez jedno źródło
status: implemented
created: 2026-11-16
updated: 2026-11-16
archived_at: null
---

## Notes

Roadmap F-01. Angular w `frontend/` woła `GET /api/ping` w Spring Boot przez proxy `ng serve`, więc przeglądarka widzi jedno źródło. Minimalny łańcuch Spring Security (ping publiczny, reszta `/api/**` → 401) jako baza pod S-01 (external-login-gate).
