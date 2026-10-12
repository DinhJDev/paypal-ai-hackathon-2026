# OpenAPI test schema

`oas-3.1-schema.json` is an unmodified copy of the OpenAPI Initiative's
[OpenAPI 3.1 document schema, 2022-10-07](https://spec.openapis.org/oas/3.1/schema/2022-10-07).
It validates the generated document structure without requiring a network request during tests.
The [upstream project](https://github.com/OAI/OpenAPI-Specification) uses Apache License 2.0.

The document schema does not validate embedded JSON Schemas. Contract tests separately resolve local
references and validate actual success/error bodies against their published JSON Schemas.
