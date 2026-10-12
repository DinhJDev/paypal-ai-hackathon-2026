# Suitefolk

Suitefolk is an open-source platform in development for salon suite owners to support tenants and manage their suites.

## Planned features

- **Tenant support:** AI-assisted help with tenant questions and issues.
- **Churn detection:** identify tenants who may be at risk of leaving.
- **Suite matching:** match tenants with suitable salon suites.
- **Rent collection:** collect rent through PayPal.

## How it works

The intended workflow connects a Next.js web interface to a Spring Boot API backed by PostgreSQL.
The API will handle tenant workflows, AI assistance, and PayPal integration.

The project is at the foundation stage: app scaffolds and local development setup are in place.
Web/API integration and the business features above are still to be built.

## Developer docs

- [Development guide](docs/development.md): setup, local commands, checks, and contributing.
- [Architecture](docs/architecture.md): repository structure and current implementation boundaries.

Licensed under [Apache 2.0](LICENSE).
