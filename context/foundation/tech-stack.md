---
starter_id: spring
package_manager: maven
project_name: itcardbrawl
hints:
  language_family: java
  team_size: solo
  deployment_target: self-host
  ci_provider: github-actions
  ci_default_flow: auto-deploy-on-merge
  bootstrapper_confidence: verified
  path_taken: standard
  quality_override: false
  self_check_answers: null
  has_auth: true
  has_payments: false
  has_realtime: false
  has_ai: false
  has_background_jobs: false
---

## Why this stack

ItCardBrawl is a small, browser-based card game built solo after hours within a 6-week MVP budget, and the author chose Java as the language family. Spring Boot is the vetted default for a Java web app: explicitly typed, convention-based, widely documented and well represented in AI training data, so agents can work in it reliably. Spring Security's OAuth2 client covers the PRD's external-account-only login (FR-001), and the game rules (stress limit 30, 30-card faction decks, computer opponent) live as plain, unit-testable Java domain code. No payments, realtime, background jobs or LLM features are in v1 — the computer opponent is rule-based game logic. Deployment is self-hosted (a Dockerfile is the expected artifact), with GitHub Actions auto-deploying on merge to main. Note: the default scaffold includes only web + devtools; OAuth2 client/security and a view layer (e.g., Thymeleaf with light JS for the game table) need to be added after scaffolding.

## Post-bootstrap notes

- 2026-11-16 — **View layer changed: separate frontend project instead of Thymeleaf.** The GUI will be a standalone SPA (Angular or React — not yet decided; location — sibling folder or `frontend/` — not yet decided). The Spring Boot app becomes a JSON API only.
- Recommended auth shape: Spring handles the OAuth2 login (`oauth2Login`) and keeps a session cookie; the SPA's dev server proxies API calls to Spring so the browser sees one origin (no tokens in the browser, no CORS).
- Already added to `pom.xml` after scaffolding: `spring-boot-starter-security`, `spring-boot-starter-security-oauth2-client` (+ their `-test` starters). OAuth2 provider registration (`spring.security.oauth2.client.registration.*`) and a `SecurityFilterChain` are still to do.
