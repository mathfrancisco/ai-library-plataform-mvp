# SPEC-01 — CI and build fixes

**Priority:** P0 · **Depends on:** nothing

## Problem

CI run on `main` fails in two jobs:

- **backend**: `SchemaIntegrationTest` cannot load the context. Root cause: `Caused by: mapping values are not allowed here`. In `backend/src/main/resources/application.yml` the default
  `${OPEN_LIBRARY_USER_AGENT:AILibraryPortfolio/0.1 (contact: replace@example.com)}` contains `: ` inside a plain scalar.
- **frontend**: `actions/setup-node` with `cache: npm` fails because `frontend/package-lock.json` does not exist.

Also, there is no Maven Wrapper, so the backend cannot be built on a machine without Maven.

## Changes

1. Quote the value in `application.yml`:
   ```yaml
   user-agent: "${OPEN_LIBRARY_USER_AGENT:AILibraryPortfolio/0.1 (contact: replace@example.com)}"
   ```
   Scan the rest of the file for the same pattern.
2. Run `npm install` in `frontend/` and commit `package-lock.json`. Change CI step `npm install` to `npm ci`.
3. Add Maven Wrapper (`mvn wrapper:wrapper` via Docker, or copy `mvnw`, `mvnw.cmd`, `.mvn/wrapper/`). Change CI and `backend/Dockerfile` to use `./mvnw`.
4. Create `frontend/public/` (with `favicon.ico`). The frontend `Dockerfile` copies `/app/public`, which does not exist, so `docker compose build` fails. Switch the Dockerfile `deps` stage to `npm ci`.
5. Add `.gitattributes` with `mvnw text eol=lf` so Windows checkouts keep the script executable. Also run `git update-index --chmod=+x backend/mvnw` (Windows does not keep the executable bit).
6. Check the Testcontainers version. `pom.xml` pins `testcontainers.version` 1.21.3 and the artifact `junit-jupiter`, while Spring Boot 4 manages Testcontainers 2.x (artifacts renamed to `testcontainers-*`, e.g. `testcontainers-junit-jupiter`, `testcontainers-postgresql`). Remove the override and use the Boot-managed version unless the build proves 1.21.3 is needed. Verify with Context7 / `./mvnw dependency:tree | grep testcontainers`.
7. After the YAML fix, make sure `SchemaIntegrationTest` runs and is not silently skipped: `@Testcontainers(disabledWithoutDocker = true)` turns "no Docker" into a pass. In CI, set `-Dtestcontainers.required=true` or drop `disabledWithoutDocker` so a missing Docker fails the job.

## Acceptance criteria

- `./mvnw -B verify` passes locally (Docker running for Testcontainers).
- `npm ci && npm run lint && npm test && npm run build` passes in `frontend/`.
- GitHub Actions `CI` is green on `main`, including `docker-build`.

## Out of scope

Any behavior change. Refactors belong to SPEC-03.
