# Architecture

Suitefolk has an authentication foundation for salon suite owners. Tenant, AI, and payment workflows
in the [README](../README.md) remain planned.

## Repository layout

| Location | Responsibility |
| --- | --- |
| [`apps/web`](../apps/web) | Next.js App Router, hosted sign-in, server sessions, and owner dashboard. |
| [`apps/api`](../apps/api) | Spring Boot, owner identity/access, JWT validation, and OpenAPI. |
| [`compose.yaml`](../compose.yaml) | Local PostgreSQL 17 with persistent storage and a localhost-only port. |
| [`scripts`](../scripts) | Explicit owner-access provisioning. |
| [`docs`](.) | Focused developer guides maintained with the code. |

## Authentication and access

Auth0's hosted database authenticates users. The Next.js server uses the official SDK for
Authorization Code/PKCE, state/nonce validation, and encrypted HttpOnly sessions. Its browser token
endpoint is disabled. A request-time dashboard checks the session behind `Suspense`; the client calls
`GET /api/me`, whose server handler obtains the current caller's API token and persists refreshed cookies.

The handler calls Spring's `GET /api/v1/me` with a bearer header, no shared cache, no redirects, and a
five-second timeout. It validates the returned identity and matches its subject to the verified session
before responding. Neither tokens nor raw upstream error bodies enter browser responses.

Spring validates RS256 signatures through Auth0 JWKS plus issuer, audience, timestamps, and subject.
An enabled `owner_access` row keyed by **issuer + subject** grants `OWNER` access and supplies a stable
internal UUID. Login, email, and token roles cannot provision access. Disabled or missing records return
403; invalid credentials return 401. A separate judge login receives explicitly provisioned demo access.
There are no tenant, rent, payment, or AI business modules yet.

## API foundation

Spring MVC, JPA, Flyway, Spring Security, validation, Actuator, Spring AI, and Spring Modulith are present.
Flyway creates the owner-access table. Bearer authentication is limited to `/api/**`; the fallback
security chain retains CSRF and permits documentation only in `local` (plus error dispatches).
Documentation is disabled by default. Identity and error responses follow the published OpenAPI 3.1
schemas, including flattened RFC 9457 extensions. See [development](development.md) for setup and conventions.

The `local` profile uses Compose PostgreSQL; tests import Testcontainers service connections and
loopback signing-key fixtures. The running test application uses real configured JWT settings, not
those generated test keys. AI models remain disabled.

## Dependency boundaries

- Boot `4.2.0-SNAPSHOT`, Spring AI `2.1.0-M1`, and Modulith `2.2.0-M1` are prereleases.
- Springdoc `3.1.1` and both Swagger annotation artifacts at `2.2.55` are verified against this stack.
  Recheck contract tests when upgrading, particularly the moving Boot snapshot.
- Compose uses `postgres:17-alpine`; Testcontainers uses `postgres:latest`, so database versions can differ.
- Framework and Auth0 SDK versions are recorded in the web package/lockfile. Builds and CI require
  no live provider secrets. Auth0 settings fail closed when incomplete.

## Documentation scope

Add documentation for implemented features and useful decisions. Keep setup in the development guide
and avoid placeholder guides or repeated instructions.
