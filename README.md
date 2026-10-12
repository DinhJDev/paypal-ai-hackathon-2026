# paypal-ai-hackathon-2026

AI-powered salon suite tenant support system with churn risk detection, optimized suite matching, and PayPal rent
collection for salon suite owners.

## Suitefolk foundation

This monorepo currently contains the application scaffolds. Tenant support, churn detection, suite matching,
and PayPal integration are planned features.

- `apps/api`: Spring Boot API with Maven, JPA, Flyway, Spring Security, Actuator, Spring AI, and Spring Modulith dependencies.
- `apps/web`: Next.js App Router application with TypeScript, Tailwind CSS, and a starter UI component.
- `compose.yaml`: local PostgreSQL 17 with persistent storage, exposed only at `127.0.0.1:15432`.

## Requirements

- JDK 25; point `JAVA_HOME` at that JDK before running the API commands.
- Node.js 22 (used for verification) and pnpm 10.14.0, as pinned in `apps/web/package.json`.
- Docker with Linux containers for PostgreSQL and API tests.
- Network access for dependency downloads and the web application's Google Fonts.

Use the committed Maven wrapper instead of a system Maven installation. The API currently uses Spring Boot
`4.2.0-SNAPSHOT`, Spring AI `2.1.0-M1`, and Spring Modulith `2.2.0-M1`; these are prerelease dependencies.

## Local development

Run the following from the repository root in PowerShell. Choose a password for the local development database
and keep this environment variable available in the shell that starts the API:

```powershell
$env:SUITEFOLK_DB_PASSWORD = 'choose-a-local-development-password'
docker compose up -d postgres
Set-Location apps/api
./mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=local"
```

The API uses port 8080 by default. The `local` profile connects to the Compose database using
`SUITEFOLK_DB_PASSWORD`. Spring Security is enabled; authentication configuration is still part of future work.
There are no application database migrations or domain modules yet.

Alternatively, start the API with a disposable Testcontainers database from `apps/api`:

```powershell
./mvnw.cmd spring-boot:test-run "-Dspring-boot.run.profiles=test"
```

In another shell, start the web application:

```powershell
Set-Location apps/web
pnpm install --frozen-lockfile
pnpm dev
```

Open `http://localhost:3000`. Stop the Compose database from the repository root with `docker compose stop postgres`.
On macOS or Linux, use `./mvnw` and `export SUITEFOLK_DB_PASSWORD=...` instead of the PowerShell equivalents.

## Verification

From `apps/api`, with JDK 25 and Docker running:

```powershell
./mvnw.cmd -B verify
```

This runs the Spring application context test against a disposable PostgreSQL container and packages the API.
The test container currently uses `postgres:latest`, while Compose pins PostgreSQL 17.

From `apps/web`:

```powershell
pnpm install --frozen-lockfile
pnpm lint
pnpm build
```

The web scaffold has no automated test suite yet. CI workflows have not been configured.

## Local files and credentials

The root `.gitignore` applies to both apps. It excludes real `.env` files, local secret directories and key files,
dependencies, build output, logs, and IDE metadata; example environment files may be committed.
Docker Compose can read a root `.env` file, but the API commands above need the password exported into their
process environment. AI model autoconfiguration is currently disabled, so these checks require no OpenAI API key.
