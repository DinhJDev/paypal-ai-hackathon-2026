# Development

## Requirements

- JDK 25, with `JAVA_HOME` pointing to that JDK. Use the committed Maven wrapper.
- Node.js 22 (used for verification) and pnpm 10.14.0. The [web package](../apps/web/package.json) pins pnpm.
- Docker with Linux containers for PostgreSQL and API tests.
- Network access for dependencies and the web application's Google Fonts.

The commands below use PowerShell. On macOS or Linux, use `./mvnw` instead of `./mvnw.cmd`
and `export SUITEFOLK_DB_PASSWORD=...` instead of the PowerShell environment assignment.

## Run locally

From the repository root, choose a local development database password and start PostgreSQL and the API:

```powershell
$env:SUITEFOLK_DB_PASSWORD = 'choose-a-local-development-password'
docker compose up -d postgres
Set-Location apps/api
./mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=local"
```

PostgreSQL listens at `127.0.0.1:15432`; the API uses port 8080 by default. Keep the password
variable available in the shell that starts the API. Compose preserves database data in a named volume.

Alternatively, from `apps/api`, start the API with a disposable Testcontainers database:

```powershell
./mvnw.cmd spring-boot:test-run "-Dspring-boot.run.profiles=test"
```

In a second terminal, from the repository root:

```powershell
Set-Location apps/web
pnpm install --frozen-lockfile
pnpm dev
```

Open `http://localhost:3000`. Stop the Compose database from the repository root with
`docker compose stop postgres`.

## Verify changes

From `apps/api`, with JDK 25 and Docker running:

```powershell
./mvnw.cmd -B verify
```

This runs the application context test against a disposable PostgreSQL container and packages the API.

From `apps/web`:

```powershell
pnpm install --frozen-lockfile
pnpm lint
pnpm build
```

The web scaffold has no automated test suite yet. CI workflows have not been configured.
See [architecture](architecture.md) for the current dependency and database limitations.

## Configuration

`SUITEFOLK_DB_PASSWORD` is required by Compose and the API's `local` profile. Compose can read a root
`.env` file, but the API commands above need the password exported into their process environment.
AI model autoconfiguration is disabled, so local verification requires no OpenAI API key.

The [root ignore rules](../.gitignore) cover both apps: real `.env` files, local secret directories,
key files, dependencies, build output, logs, and IDE metadata. Example environment files may be committed.

## Contributing

Keep each pull request focused, run the checks relevant to your change, and describe the result.
Update these guides when setup or behavior changes. Keep the root README focused on the product overview.
