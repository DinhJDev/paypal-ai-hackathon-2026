# Architecture

Suitefolk currently provides application scaffolds and local infrastructure. The product workflow
described in the [README](../README.md) is planned; the web application does not yet call the API.

## Repository layout

| Location | Responsibility |
| --- | --- |
| [`apps/web`](../apps/web) | Next.js App Router, TypeScript, Tailwind CSS, and a starter shadcn/Base UI component. |
| [`apps/api`](../apps/api) | Spring Boot application, Maven wrapper, OpenAPI foundation, local/test profiles, and Testcontainers tests. |
| [`compose.yaml`](../compose.yaml) | PostgreSQL 17 for local development, with persistent storage and a localhost-only port. |
| [`docs`](.) | Focused developer guides maintained alongside the code. |

## API foundation

The [API dependencies](../apps/api/pom.xml) include Spring MVC, JPA, Flyway, Spring Security,
OAuth2 resource-server support, validation, Actuator, Spring AI, and Spring Modulith.
There are no domain modules, application database migrations, or business endpoints yet.
Spring Security is enabled; application authentication still needs configuration.

Springdoc generates OpenAPI 3.1 JSON/YAML and Swagger UI only when enabled by the `local` profile.
The contract includes `/api/v1/**` endpoints and excludes Actuator. A local security chain permits only
documentation routes and error dispatches without authentication; application routes stay protected.
Outside `local`, Spring Boot's existing default security remains in effect and documentation is disabled.
The MVC exception handler provides RFC 9457 errors and a schema that matches Spring's flattened extension
fields. See the [development guide](development.md#api-documentation) for endpoint conventions.

The `local` profile connects to Compose PostgreSQL. Tests import
[`TestcontainersConfiguration`](../apps/api/src/test/java/api/TestcontainersConfiguration.java)
to supply a disposable database through Spring Boot service connections.
AI models are disabled in the current application configuration.

## Dependency boundaries

- Spring Boot `4.2.0-SNAPSHOT`, Spring AI `2.1.0-M1`, and Spring Modulith `2.2.0-M1` are prerelease dependencies.
- Springdoc `3.1.1` is verified against this stack. Both Swagger annotation artifacts are aligned to
  `2.2.55`; older versions introduced by Spring AI/OpenAI otherwise break contract generation.
  Recheck the contract tests when upgrading these dependencies, especially the moving Boot snapshot.
- Compose pins `postgres:17-alpine`; Testcontainers currently uses `postgres:latest`, so their database versions can differ.
- Next.js and React versions are declared in the [web package](../apps/web/package.json);
  the committed pnpm lockfile records resolved dependencies.

## Documentation scope

Use the [development guide](development.md) for commands and contributor workflow. Add new documentation
when an implemented feature or a recorded design decision needs it; avoid placeholder guides and repeated setup instructions.
