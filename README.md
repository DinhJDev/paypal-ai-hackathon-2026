# Suitefolk

Suitefolk is an open-source platform in development for salon suite owners to support tenants and manage their suites.

**Hackathon submission deadline:** November 12, 2026, at 12:00 PM PST (2:00 PM CST).
See the [official PayPal AI Hackathon schedule](https://paypalaihackathon.devpost.com/details/dates).

## Planned features

- **Tenant support:** AI-assisted help with tenant questions and issues.
- **Churn detection:** identify tenants who may be at risk of leaving.
- **Suite matching:** match tenants with suitable salon suites.
- **Rent collection:** collect rent through PayPal.

## How it works

The intended workflow connects a Next.js web interface to a Spring Boot API backed by PostgreSQL.
The API will handle tenant workflows, AI assistance, and PayPal integration.

The authentication foundation connects hosted sign-in to API-confirmed owner access.
The business features above are still to be built.

## Developer docs

- [Development guide](docs/development.md): setup, local commands, checks, and contributing.
- [Architecture](docs/architecture.md): repository structure and current implementation boundaries.
- [Submission checklist](docs/hackathon-submission.md): hackathon deliverables and readiness checks.

Licensed under [Apache 2.0](LICENSE).
