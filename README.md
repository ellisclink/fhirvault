# fhirvault

A FHIR R4 server built on the HAPI FHIR plain-server library and Spring Boot. It has its own PostgreSQL storage, US Core 6.1 validation, FHIR search and version history, atomic transaction bundles, SMART on FHIR authorization through Keycloak, and a per-patient AuditEvent trail.

Built as a portfolio project.

## Features

| Area | What it does |
|---|---|
| Resources | Patient, Observation, Encounter, Condition, MedicationRequest, AllergyIntolerance (create, read, vread, update, delete) and read-only AuditEvent |
| Storage | Current version and full history as JSONB in PostgreSQL, tombstone deletes (410 Gone), `If-Match` optimistic locking (412) |
| Validation | Base R4 plus US Core 6.1.0 profiles; errors return 422 with an OperationOutcome |
| Integrity | References must point at live resources; deleting a referenced resource returns 409 |
| Search | US Core search parameters (token, string, date, reference, `_id`, `_lastUpdated`), date prefixes, `:exact`/`:contains`, `_sort`, stateless `_count`/`_offset` paging; unsupported features return 400 |
| History | `_history` at instance, type and system level with `_since` and paging |
| Transactions | Atomic `transaction` bundles with `urn:uuid` reference resolution |
| Security | Keycloak JWTs (issuer, audience, signature), SMART v1/v2 scopes, patient-compartment enforcement, `/.well-known/smart-configuration` |
| Audit | One FHIR AuditEvent per request and patient, append-only in the database, readable with dedicated scopes; patients can see who accessed their record |

## Quick start (Docker Compose)

Requires Docker and JDK 21 (the loader runs on the host).

```bash
cp deploy/.env.example deploy/.env       # then replace every change-me value
docker compose -f deploy/docker-compose.yml up --build
```

| Service | URL |
|---|---|
| FHIR API | http://localhost:8080/fhir (CapabilityStatement at `/fhir/metadata`) |
| Keycloak | http://localhost:8081 (admin user `admin`, password from `.env`) |

Postgres is reachable only inside the Compose network.

### Load data

```bash
./mvnw -f tools/loader/pom.xml package
export FHIRVAULT_LOADER_CLIENT_SECRET=...   # the value from deploy/.env
java -jar tools/loader/target/fhirvault-loader.jar tools/loader/sample path/to/synthea/output/fhir
```

`tools/loader/sample` contains `Patient/example-patient`, the record linked to the `patient-demo` login. Synthea output is optional. See [tools/loader](tools/loader/README.md) for how to generate it.

### Call the API

Import [docs/api/fhirvault.postman_collection.json](docs/api/fhirvault.postman_collection.json) into Postman (or run it with newman). Set `loader_client_secret` and `demo_password` from your `.env`, run a request in "0 Auth", then the other folders.

The same with curl:

```bash
TOKEN=$(curl -s -d grant_type=client_credentials -d client_id=fhirvault-loader \
  -d client_secret=$FHIRVAULT_LOADER_CLIENT_SECRET \
  http://localhost:8081/realms/fhirvault/protocol/openid-connect/token | jq -r .access_token)
curl -H "Authorization: Bearer $TOKEN" "http://localhost:8080/fhir/Observation?patient=example-patient&category=laboratory"
```

| Login (Keycloak) | Client | Scopes to request | Sees |
|---|---|---|---|
| client credentials | `fhirvault-loader` | `system/*.cruds` (default) | everything except AuditEvent |
| `patient-demo` | `fhirvault-tester` or `smart-demo-app` | `launch/patient patient/*.rs patient/AuditEvent.rs` | only Patient/example-patient's compartment |
| `clinician-demo` | `fhirvault-tester` or `smart-demo-app` | `user/*.cruds user/AuditEvent.rs` | all patients; audit log |

`fhirvault-tester` uses the password grant and is meant for local development and tests only. Real SMART apps use `smart-demo-app` (authorization code with PKCE).

## Run without Docker (Windows)

Uses the PostgreSQL installed on your machine and Keycloak's standalone Java distribution. The script downloads Keycloak (about 200 MB) into `.local/` on its first run. You need JDK 21, with `JAVA_HOME` pointing at it.

```powershell
copy deploy\.env.example deploy\.env          # then set the passwords
powershell -ExecutionPolicy Bypass -File scripts\local\setup-db.ps1     # once; asks for your postgres password
powershell -ExecutionPolicy Bypass -File scripts\local\run-local.ps1    # Keycloak on :8081, API on :8080
```

Ctrl+C stops both. To run the API alone with open access, add `-NoAuth`. If Keycloak is left running after you close the window, `scripts\local\stop-local.ps1` stops it. The loader and the Postman collection work the same way as with Compose.

## Development

```bash
./mvnw verify                                   # unit tests + integration tests (needs Docker)
./mvnw -f tools/loader/pom.xml verify           # loader tests
```

The integration tests start PostgreSQL and Keycloak with Testcontainers. They import the same realm file as Compose, with random secrets.

To run locally without Keycloak, use the `noauth` profile (open access, development only):

```bash
export DB_PASSWORD=...                          # Postgres on localhost:5432, database/user "fhirvault"
./mvnw spring-boot:run -Dspring-boot.run.profiles=local,noauth
```

Configuration comes from environment variables: `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `KEYCLOAK_ISSUER_URI`, `SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_JWK_SET_URI`, `FHIRVAULT_AUDIENCE` and `FHIRVAULT_CORS_ALLOWED_ORIGINS`. No secret is committed.

## Layout

```
src/main/java/dev/fhirvault/
  config/       Spring + HAPI wiring           service/      versioned writes, transactions
  fhir/         RestfulServer, providers       validation/   US Core, reference checks
  persistence/  SQL repositories, indexing     search/       search params -> SQL
  security/     SMART scopes, compartments     audit/        AuditEvent trail
src/main/resources/db/migration/   Flyway V1-V5
deploy/                            docker-compose.yml, Keycloak realm
tools/loader/                      Synthea bundle loader
docs/api/                          Postman collection
```

All data in this repository is synthetic (Synthea or hand-made).

## AI assistance

I used Claude (Anthropic) as a coding assistant on this project. Edit this section to describe what you did yourself and what the assistant helped with.
