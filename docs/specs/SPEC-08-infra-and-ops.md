# SPEC-08 — Infrastructure, containers and CI hardening

**Priority:** P1 (P0 for §1.1) · **Depends on:** SPEC-01

Findings come from a read of `docker-compose.yml`, both `Dockerfile`s, `.github/workflows/ci.yml`, `.github/dependabot.yml`, `Makefile` and `.env.example`. SPEC-01 fixes what blocks the build; this spec fixes what makes the stack fragile, insecure by default, or slow in CI.

Legend: 🔴 broken · 🟠 wrong behavior or security gap · 🟡 improvement.

---

## 1. `docker-compose.yml`

| # | Finding | Change |
|---|---|---|
| 1.1 🔴 | `backend.env_file: .env` is required. On a fresh clone without `.env`, `docker compose up` fails before anything starts. | Use `env_file: [{ path: .env, required: false }]` (Compose ≥ 2.24). Keep all defaults in `environment:` with `${VAR:-default}`. |
| 1.2 🟠 | Postgres publishes `5432:5432` on all interfaces with password `ailibrary`. On a laptop in a shared network the DB is reachable. | Bind to loopback: `"127.0.0.1:5432:5432"`. Same for backend (`127.0.0.1:8080:8080`) is optional. |
| 1.3 🟠 | No backend healthcheck; `frontend.depends_on: backend` only waits for container start. | Backend healthcheck: `curl -fs http://localhost:8080/actuator/health/readiness` (install `curl` or use `wget` in the image), `start_period: 60s`. Frontend `depends_on: backend: condition: service_healthy`. |
| 1.4 🟡 | `frontend.environment.NEXT_PUBLIC_API_URL` has no effect at runtime (value is inlined at build). It suggests the URL can be changed without a rebuild. | Remove it from `environment:`; keep only the build arg. Or adopt the same-origin proxy (§4), which removes the variable from the browser entirely. |
| 1.5 🟡 | ONNX model cache (SPEC-02) would be downloaded on every new container. | Named volume `onnx-cache:/data/onnx-cache` (already listed in SPEC-07 §2; keep it in one place — here). |
| 1.6 🟡 | No resource limits. Local ONNX embeddings + Tika can take all CPU/RAM. | `deploy.resources.limits` for backend (e.g. `memory: 2g`) and `JAVA_TOOL_OPTIONS=-XX:MaxRAMPercentage=75`. |
| 1.7 🟡 | No dev override. Every code change needs an image rebuild. | Optional `docker-compose.dev.yml` that runs only `postgres`; developers run backend (`./mvnw spring-boot:run`) and frontend (`npm run dev`) on the host. Document in README. |

## 2. `backend/Dockerfile`

| # | Finding | Change |
|---|---|---|
| 2.1 🟠 | Runtime image runs as `root`. | Add a non-root user (`useradd -r -u 10001 app`), `chown` `/data/uploads` and `/data/onnx-cache`, `USER app`. |
| 2.2 🟠 | Build uses the `maven` image, not the project wrapper (SPEC-01 adds `mvnw`). Maven version can drift from CI. | `FROM eclipse-temurin:21-jdk AS build`, copy `mvnw` + `.mvn/`, use `./mvnw`. Use a BuildKit cache mount for `~/.m2` instead of `dependency:go-offline`. |
| 2.3 🟡 | Fat jar copied as is; every code change re-uploads all dependencies. | Use Spring Boot layered jar (`java -Djarmode=tools -jar app.jar extract --layers --launcher`) and copy layers in dependency → application order. |
| 2.4 🟡 | No JVM container flags. | `ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"`. |
| 2.5 🟡 | No `.dockerignore`; `target/` and IDE files enter the build context. | Add `backend/.dockerignore` (`target/`, `.idea/`, `*.iml`) and `frontend/.dockerignore` (`node_modules/`, `.next/`, `coverage/`). |

## 3. `frontend/Dockerfile`

| # | Finding | Change |
|---|---|---|
| 3.1 🔴 | Copies `/app/public`, which does not exist (SPEC-01 §4). | Fixed by SPEC-01. |
| 3.2 🟠 | Runtime runs as `root`. | `USER node` (the `node:22-alpine` image has it); `COPY --chown=node:node`. |
| 3.3 🟡 | `npm install` in `deps` stage. | `npm ci` (SPEC-01) + `ENV NEXT_TELEMETRY_DISABLED=1`. |
| 3.4 🟡 | No `HOSTNAME`/`PORT` env. Next standalone binds to the container hostname by default in some versions. | `ENV HOSTNAME=0.0.0.0 PORT=3000`. |

## 4. Same-origin API proxy (decision)

**Problem.** The browser calls `http://localhost:8080` directly. This needs CORS (SPEC-04 §2.2), bakes the API URL into the JS bundle at build time, and blocks the V2 move to `httpOnly` cookies (cookies across origins need `SameSite=None; Secure`).

**Decision.** Add a Next.js rewrite: browser calls `/api/*` on the frontend origin; Next forwards to `API_INTERNAL_URL` (runtime, server-side env, default `http://backend:8080` in Compose).

```ts
// next.config.ts
async rewrites() {
  return [{ source: "/api/:path*", destination: `${process.env.API_INTERNAL_URL ?? "http://localhost:8080"}/api/:path*` }];
}
```

- `lib/api.ts` uses relative paths (`BASE = ""`).
- CORS stays configured for direct API use (Swagger, curl) but is no longer on the critical path.
- Uploads go through the proxy; confirm the 25 MB body passes (Next rewrites stream the body, no `bodyParser` limit applies to rewrites — verify with a 24 MB file).
- If deploy targets split frontend (Vercel) and backend (Render/Fly) on different domains, the rewrite still works server-side.

**Alternative (not chosen):** keep direct calls + CORS. Simpler today, but costs a migration later.

## 5. CI (`.github/workflows/ci.yml`)

| # | Finding | Change |
|---|---|---|
| 5.1 🟡 | No `concurrency`; pushes to a PR stack up runs. | `concurrency: { group: ci-${{ github.ref }}, cancel-in-progress: true }`. |
| 5.2 🟡 | No job timeouts. A hung Testcontainers run burns 6 h. | `timeout-minutes: 20` per job. |
| 5.3 🟡 | `docker-build` builds without cache. | `docker/setup-buildx-action` + `docker/build-push-action` with `cache-from/to: type=gha`, `push: false`, one step per image. |
| 5.4 🟡 | No test reports on failure. | Upload `backend/target/surefire-reports` and `failsafe-reports` with `actions/upload-artifact` when `failure()`. |
| 5.5 🟡 | No dependency or image scan. | Add `github/codeql-action` (java, javascript-typescript) on a weekly schedule, and `aquasecurity/trivy-action` on the two built images with `severity: CRITICAL,HIGH`, `exit-code: 0` at first (report only). |
| 5.6 🟡 | `Makefile` uses `mvn` and has no lint/format targets. | Targets: `up`, `down`, `dev-db`, `test` (both), `lint`, `format`, `verify` (what CI runs). All call `./mvnw` / `npm`. |
| 5.7 🟡 | Dependabot opens one PR per package. | Add `groups` (e.g. `spring`, `testing`, `next-react`) and `open-pull-requests-limit: 5`. |

## 6. Runtime configuration

| # | Finding | Change |
|---|---|---|
| 6.1 🟠 | `/actuator/metrics` is open to any authenticated user (only `health`/`info` are public, `anyRequest().authenticated()` covers the rest). | Expose only `health,info` by default; `metrics`/`prometheus` only in a profile, and require `ROLE_ADMIN`. |
| 6.2 🟡 | Liveness/readiness probes are enabled but readiness does not include the DB. | `management.endpoint.health.group.readiness.include: readinessState,db`. |
| 6.3 🟡 | No request id / correlation id in logs. SPEC-04 §1 wants "log with a request id". | Servlet filter that reads `X-Request-Id` or generates one, puts it in MDC and in the response header; include it in `ApiError` as `requestId`. |
| 6.4 🟡 | Unused `hstore` extension in V1. | Leave V1 unchanged; note it. No action. |

## Acceptance criteria

- Fresh clone, no `.env`: `docker compose up --build` starts all three services; frontend waits for backend health.
- `docker compose exec backend id -u` and `docker compose exec frontend id -u` do not print `0`.
- `docker compose ps` shows backend `healthy`.
- Port 5432 is not reachable from another machine on the LAN.
- CI: second run of `docker-build` with no changes finishes faster than the first (GHA cache hit visible in logs).
- A failing backend test uploads its surefire report.

## Out of scope

Kubernetes, Terraform, multi-instance deploy, Redis, object storage (tracked in `12-deployment.md` scale triggers).
