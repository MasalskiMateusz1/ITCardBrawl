---
bootstrapped_at: 2026-11-16T10:35:11Z
starter_id: spring
starter_name: Spring Boot
project_name: itcardbrawl
language_family: java
package_manager: maven
cwd_strategy: subdir-then-move
bootstrapper_confidence: verified
phase_3_status: ok
audit_command: "null"
---

## Hand-off

```yaml
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
```

**Why this stack**

ItCardBrawl is a small, browser-based card game built solo after hours within a 6-week MVP budget, and the author chose Java as the language family. Spring Boot is the vetted default for a Java web app: explicitly typed, convention-based, widely documented and well represented in AI training data, so agents can work in it reliably. Spring Security's OAuth2 client covers the PRD's external-account-only login (FR-001), and the game rules (stress limit 30, 30-card faction decks, computer opponent) live as plain, unit-testable Java domain code. No payments, realtime, background jobs or LLM features are in v1 — the computer opponent is rule-based game logic. Deployment is self-hosted (a Dockerfile is the expected artifact), with GitHub Actions auto-deploying on merge to main. Note: the default scaffold includes only web + devtools; OAuth2 client/security and a view layer (e.g., Thymeleaf with light JS for the game table) need to be added after scaffolding.

## Pre-scaffold verification

| Signal      | Value   | Severity | Notes                                                                                   |
| ----------- | ------- | -------- | --------------------------------------------------------------------------------------- |
| npm package | not run | n/a      | non-JS starter                                                                          |
| GitHub repo | not run | n/a      | card `docs_url` (https://docs.spring.io/spring-boot/) is not a GitHub URL — no recency signal available |

## Scaffold log

**Resolved invocation**: `mkdir .bootstrap-scaffold && cd .bootstrap-scaffold && curl -sf https://start.spring.io/starter.tgz -d dependencies=web,devtools -d type=maven-project -d javaVersion=21 -d groupId=com.example -d artifactId=itcardbrawl | tar -xzf -`
**Strategy**: subdir-then-move
**Exit code**: 0
**Files moved**: 9 (+ `.gitignore` append-merged)
**Conflicts (.scaffold siblings)**: pom.xml.scaffold
**.gitignore handling**: append-merged
**.bootstrap-scaffold cleanup**: deleted

Deviation from card `cmd_template`: the Initializr archive has no top-level folder (it extracts straight into the current directory), so the command ran *inside* `.bootstrap-scaffold/` and kept `artifactId=itcardbrawl` (the hand-off `project_name`) instead of substituting `{name}=.bootstrap-scaffold` into `artifactId`. tar printed harmless "time stamp in the future" / unknown `LIBARCHIVE.creationtime` header warnings.

Pre-existing files (IntelliJ empty Maven skeleton) were preserved unchanged at the user's request: `pom.xml` (`org.example:a-cards`, no dependencies), `src/main/java/org/example/Main.java`, `.idea/`.

File-by-file:

```
MOVED          .gitattributes
APPEND-MERGED  .gitignore  (added: HELP.md, .mvn/wrapper/maven-wrapper.jar, ### STS ###, .idea)
MOVED          .mvn/wrapper/maven-wrapper.properties
MOVED          HELP.md
MOVED          mvnw
MOVED          mvnw.cmd
CONFLICT       pom.xml -> pom.xml.scaffold
MOVED          src/main/java/com/example/itcardbrawl/ItcardbrawlApplication.java
MOVED          src/main/resources/application.properties
MOVED          src/test/java/com/example/itcardbrawl/ItcardbrawlApplicationTests.java
CREATED (dir)  src/main/resources/static/, src/main/resources/templates/
```

## Post-scaffold audit

**Tool**: skipped — no built-in audit tool for java
**Recommended external tool**: OWASP Dependency-Check (`org.owasp:dependency-check-maven`) or Snyk.

## Hints recorded but not acted on

| Hint                    | Value                |
| ----------------------- | -------------------- |
| bootstrapper_confidence | verified             |
| quality_override        | false                |
| path_taken              | standard             |
| self_check_answers      | null                 |
| team_size               | solo                 |
| deployment_target       | self-host            |
| ci_provider             | github-actions       |
| ci_default_flow         | auto-deploy-on-merge |
| has_auth                | true                 |
| has_payments            | false                |
| has_realtime            | false                |
| has_ai                  | false                |
| has_background_jobs     | false                |

## Next steps

Next: a future skill will set up agent context (CLAUDE.md, AGENTS.md). For now, your project is scaffolded and verified — happy hacking.

Useful manual steps in the meantime:
- `git init` (if you have not already) to start your own repo history.
- Review any `.scaffold` siblings the conflict policy created and decide which version of each file to keep. **Resolved after the run (2026-11-16):** at the user's request `pom.xml.scaffold` replaced the IntelliJ placeholder `pom.xml`; `./mvnw test` passed (Spring Boot 4.1.2, Java 21).
- Address audit findings per your project's risk tolerance — the full breakdown is in this log.
