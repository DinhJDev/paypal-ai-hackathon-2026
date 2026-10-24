# Suitefolk

Suitefolk is an open-source platform in development for salon suite owners to support tenants and manage their suites.

**Hackathon submission deadline:** November 12, 2026, at 12:00 PM PST (2:00 PM CST).
See the [official PayPal AI Hackathon schedule](https://paypalaihackathon.devpost.com/details/dates).

## Features in development

- **Owner workspace:** hosted sign-in with explicitly provisioned owner access.
- **Rent management:** weekly or monthly schedules, outside-payment tracking, and optional tenant-approved PayPal sandbox autopay.
- **Churn prevention:** explainable early warnings for tenants who may be at risk of leaving.
- **Tenant support and suite matching:** AI-assisted help and suitable suite recommendations.

## How it works

The Next.js dashboard calls a Spring Boot API backed by PostgreSQL. Owners create rent schedules,
record payments received elsewhere, and optionally invite tenants to approve PayPal autopay. The API
collects sandbox payments only when its autopay switch is explicitly enabled. Live PayPal processing is not configured.

## Developer docs

- [Development guide](docs/development.md): setup, local commands, checks, and contributing.
- [Architecture](docs/architecture.md): repository structure and current implementation boundaries.
- [Submission checklist](docs/hackathon-submission.md): hackathon deliverables and readiness checks.

Licensed under [Apache 2.0](LICENSE).
