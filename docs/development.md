# Development

## Requirements

- JDK 25 and the committed Maven wrapper; set `JAVA_HOME` to that JDK.
- Node.js 22 and pnpm 10.14.0, pinned in the web package.
- Docker with Linux containers for PostgreSQL and API tests.
- Network access for dependencies, Google Fonts, and live Auth0 login.

Commands below use PowerShell. On macOS/Linux, use `./mvnw` and shell `export` assignments.

## Auth0 setup

Use the hosted database connection in `suitefolk.us.auth0.com` and Free-compatible features:

1. Create a **Regular Web Application**. Set callback URL to `http://localhost:3000/auth/callback`,
   allowed logout URL to `http://localhost:3000`, and allowed web origin to `http://localhost:3000`.
   Enable its hosted database connection. Disable public sign-ups on that connection and provision
   the owner and a separate judge account manually; avoid enabling social connections for this demo.
2. Register the API identifier `https://api.suitefolk.com` with **RS256** signing. This is a logical
   audience, not a hosting requirement. If its **User-Delegated Access** policy is **Per-app
   authorization**, open **Applications → APIs → Suitefolk API → Application Access**, edit the
   web application, and grant **User-Delegated Access**. Do not grant **Client Access**; the app
   uses a signed-in user's token, not machine-to-machine access. The API currently defines no
   custom scopes. Use short-lived access tokens, enable **Allow Offline Access**, and enable
   rotating refresh tokens and the refresh-token grant for the web application.
3. Copy [`apps/web/.env.local.example`](../apps/web/.env.local.example) to `apps/web/.env.local`.
   Add the Client ID and Client Secret, then generate `AUTH0_SECRET` with:

   ```powershell
   [Convert]::ToHexString([Security.Cryptography.RandomNumberGenerator]::GetBytes(32)).ToLowerInvariant()
   ```

   Keep the result private. Next.js loads this file automatically. No authentication setting belongs
   in `NEXT_PUBLIC_*`. Deployed web/API origins must use HTTPS; loopback HTTP is permitted in development.
4. Export the values from [`apps/api/.env.example`](../apps/api/.env.example) into the API process:

   ```powershell
   $env:AUTH0_ISSUER_URI = 'https://suitefolk.us.auth0.com/'
   $env:AUTH0_AUDIENCE = 'https://api.suitefolk.com'
   $env:AUTH0_JWK_SET_URI = 'https://suitefolk.us.auth0.com/.well-known/jwks.json'
   ```

   Spring does **not** load `.env` files automatically. The API needs no Auth0 client secret.

If login returns `Client is not authorized to access resource server`, check that the API identifier
matches `AUTH0_AUDIENCE` exactly and that the web application has **User-Delegated Access** under
**Application Access**. Grant only that access; keep Client Access disabled. This grant lets Auth0
issue an API token; Suitefolk owner access still depends on PostgreSQL provisioning.

Use the default Auth0 domain and hosted password login. Application owner access lives in PostgreSQL;
Auth0 role management, Organizations, premium connections, and machine-to-machine tokens are unnecessary.
Check the tenant's actual Free plan before the demo; trial-only features must not be required.
See Auth0's [web registration](https://auth0.com/docs/get-started/auth0-overview/create-applications/regular-web-apps)
and [refresh-token setup](https://auth0.com/docs/secure/tokens/refresh-tokens/configure-refresh-token-rotation).

## PayPal sandbox rent autopay

Rent autopay is sandbox-only. The API calls `https://api-m.sandbox.paypal.com` directly and has no
live-mode setting. Add the sandbox REST app's client ID and secret to the ignored root
`.env.paypal.local` file using `PAYPAL_CLIENT_ID=...` and `PAYPAL_CLIENT_SECRET=...`. From the repository
root, in the same PowerShell session that will run the API, load those values without printing them:

```powershell
./scripts/import-paypal-sandbox-env.ps1
```

Start PostgreSQL and the API using the commands below. The web dashboard stays on its regular `.env.local`
configuration; PayPal credentials belong only in the API process. The scheduler is disabled by default.
For an intentional local sandbox charge test, set `$env:PAYPAL_AUTOPAY_ENABLED = 'true'` in the API
session before startup. This enables the 9 AM America/Chicago daily run for due schedules in the
sandbox; it does not schedule real/live money movements. Set it back to `false` after the test.

The owner creates a weekly or monthly USD schedule independently of PayPal. **Set up autopay**
creates an optional approval link to share with the tenant. The [rent ledger](rent-ledger.md) also
records cash, Venmo, and other outside payments without PayPal setup. After approval, the API verifies
the setup token with PayPal, creates the vault payment token, and stores that token server-side. An approved schedule is charged on its due date by the
sandbox-only scheduler. Each charge uses an idempotency key derived from schedule and due date; the
owner dashboard shows recent payment state and can pause future charges. Failed provider attempts
pause the schedule. Missed dates and partial outside payments hold the affected rent period for owner
review; missed periods are not charged as automatic catch-up. The
dashboard reports separately whether tenant approval is complete and automatic charges are enabled.
Automated retries/dunning are out of scope for this slice.

For webhook verification, configure a sandbox webhook for `POST /api/v1/rent-schedules/paypal/webhook`
and set its webhook ID as `PAYPAL_WEBHOOK_ID`. PayPal must be able to reach the API over HTTPS, so
local webhook tests require a tunnel. The public PayPal return endpoint accepts only setup-token IDs
already recorded for a schedule, then independently checks approval status with PayPal. Do not expose
the vault payment token or PayPal credentials to the browser.

## Run locally

From the root, with the Auth0 variables exported, choose a database password and start the API:

```powershell
$env:SUITEFOLK_DB_PASSWORD = 'choose-a-local-development-password'
docker compose up -d postgres
Set-Location apps/api
./mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=local"
```

PostgreSQL listens at `127.0.0.1:15432`; the API uses port 8080. Compose preserves data in a named volume.
Keep the password exported in the API shell. After Flyway creates the tables, enable each approved
Auth0 account from the repository root, using its **User ID**, not its email:

```powershell
./scripts/provision-owner.ps1 -Issuer 'https://suitefolk.us.auth0.com/' -Subject 'auth0|replace-with-user-id'
```

Repeat for the separate judge account. Repeating this command preserves the owner UUID; `-Disable`
revokes API access even while a login session remains valid. Successful login never grants owner access
or creates an owner record. Keep real IDs and judge credentials out of Git and deliver demo credentials privately.

For a disposable database, run `spring-boot:test-run` with the `test` profile instead. Export the same
Auth0 variables. Provision with explicit `-ContainerName`, `-DatabaseUser test`, and `-DatabaseName test`;
owner access disappears when that test database is stopped.

In another terminal, from the root:

```powershell
Set-Location apps/web
pnpm install --frozen-lockfile
pnpm dev
```

Open `http://localhost:3000`, sign in, and confirm the dashboard reports owner access. A valid login
without enabled provisioning shows an access-not-enabled message. Check logout, browser back navigation,
and the judge account before submitting. The SDK's `/auth/access-token` route is disabled (404);
browser code uses `/api/me` and never receives a token. Stop the database with `docker compose stop postgres`.

## Verify changes

From `apps/api`, with JDK 25 and Docker running:

```powershell
./mvnw.cmd -B --no-transfer-progress verify
```

Tests use disposable PostgreSQL and local generated signing keys, including invalid/expired/wrong-audience
credentials, owner access denial, sandbox callback/signature handling, and idempotent payment scheduling.
PayPal calls are mocked in automated tests; no PayPal credentials or live calls are needed.

From `apps/web`:

```powershell
pnpm install --frozen-lockfile
pnpm test
pnpm lint
pnpm build
```

Unit tests cover server configuration, session boundaries, identity validation, and sanitized errors.
Unconfigured builds succeed; authentication fails safely at runtime. Live login/callback/refresh/logout
remain browser checks against the configured tenant.

## API documentation

The `local` profile exposes [Swagger UI](http://localhost:8080/swagger-ui.html),
[OpenAPI JSON](http://localhost:8080/v3/api-docs), and [YAML](http://localhost:8080/v3/api-docs.yaml)
without sign-in. Documentation is disabled otherwise; never deploy with `local` enabled.
For disposable-database documentation, use `test,local` and export the local database password.

`GET /api/v1/me` accepts an Auth0 API access token and returns the enabled owner UUID, verified subject,
and `OWNER` role. Missing/invalid credentials return 401 with a Bearer challenge; unprovisioned/disabled
owners return 403. Identity responses are private and not cached. Tokens must validate against the
configured issuer, audience, RS256 signature, timestamps, and a nonempty subject. API routes accept only
bearer credentials and do not use browser sessions; other routes retain CSRF protection.

New endpoints belong under `/api/v1`. Use validated request/response DTOs, stable operation names, and
explicit status/media-type annotations. Use 200 for reads/updates, 201 with `Location` for creation,
and 204 for empty responses. RFC 9457 `application/problem+json` includes `type`, `title`, `status`,
`detail`, and request-path `instance`; validation errors add `{field, message}` entries in `errors`.
Unexpected failures and authentication errors hide internal details. Extend the existing OpenAPI document
and response-schema tests with each endpoint; test fixtures remain outside the packaged application.

## Continuous integration

[API CI](../.github/workflows/api.yml) verifies with Java 25/Docker.
[Web CI](../.github/workflows/web.yml) uses Node.js 22, pinned pnpm, locked installation, unit tests,
lint, and build. Both jobs run only when the original **and** triggering actor are `DinhJDev` and the
repository was public at the event. Approving an outside PR's run does not satisfy these guards.
The repository requires [approval for all external contributors' fork workflows](https://docs.github.com/en/rest/actions/permissions#set-fork-pr-contributor-approval-permissions-for-a-repository).

Standard Ubuntu runners are [free for public repositories](https://docs.github.com/en/billing/concepts/product-billing/github-actions).
There are no paid runners, dependency caches, or artifact uploads. API/web timeouts remain 15/10 minutes;
superseded runs by the same actor are cancelled. Before making the repository private, disable Actions
and cancel pending runs: reruns retain the original event's visibility. Tests need no provider secrets.

## Contributing

Keep PRs focused, run the relevant checks, and update these guides with behavior changes.
The root ignore rules cover real environment files, credentials, dependencies, build output, and logs;
only empty/sample configuration belongs in Git. Keep the README focused on the product overview.
