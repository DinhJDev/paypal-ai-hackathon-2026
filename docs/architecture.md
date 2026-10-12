# Architecture

Suitefolk currently provides application scaffolds and local infrastructure. The product workflow
described in the [README](../README.md) is planned; the web application does not yet call the API.

## Repository layout

| Location | Responsibility |
| --- | --- |
| [`apps/web`](../apps/web) | Next.js App Router, TypeScript, Tailwind CSS, and a starter shadcn/Base UI component. |
| [`apps/api`](../apps/api) | Spring Boot application, Maven wrapper, local/test profiles, and a Testcontainers context test. |
| [`compose.yaml`](../compose.yaml) | PostgreSQL 17 for local development, with persistent storage and a localhost-only port. |
| [`docs`](.) | Focused developer guides maintained alongside the code. |

## API foundation

The [API dependencies](../apps/api/pom.xml) include Spring MVC, JPA, Flyway, Spring Security,
OAuth2 resource-server support, validation, Actuator, Spring AI, and Spring Modulith.
There are no domain modules, application database migrations, or business endpoints yet.
Spring Security is enabled; application authentication still needs configuration.

The `local` profile connects to Compose PostgreSQL. Tests import
[`TestcontainersConfiguration`](../apps/api/src/test/java/api/TestcontainersConfiguration.java)
to supply a disposable database through Spring Boot service connections.
AI models are disabled in the current application configuration.

## Dependency boundaries

- Spring Boot `4.2.0-SNAPSHOT`, Spring AI `2.1.0-M1`, and Spring Modulith `2.2.0-M1` are prerelease dependencies.
- Compose pins `postgres:17-alpine`; Testcontainers currently uses `postgres:latest`, so their database versions can differ.
- Next.js and React versions are declared in the [web package](../apps/web/package.json);
  the committed pnpm lockfile records resolved dependencies.

## Documentation scope

Use the [development guide](development.md) for commands and contributor workflow. Add new documentation
when an implemented feature or a recorded design decision needs it; avoid placeholder guides and repeated setup instructions.
