# fhirvault loader

Loads Synthea FHIR transaction bundles into fhirvault.

## Build

```bash
./mvnw -f tools/loader/pom.xml package       # from the repository root
```

## Generate synthetic patients with Synthea

Download `synthea-with-dependencies.jar` from https://github.com/synthetichealth/synthea/releases. Then run it with options that match this server: US Core 6.1, no separate practitioner or hospital bundles, and only the types the server stores (plus Medication, which the loader inlines):

```bash
java -jar synthea-with-dependencies.jar -p 5 -s 42 \
  --exporter.years_of_history 2 \
  --exporter.hospital.fhir.export false --exporter.practitioner.fhir.export false \
  --exporter.fhir.included_resources "Patient,Encounter,Condition,Observation,MedicationRequest,AllergyIntolerance,Medication"
```

The bundles are written to `output/fhir/`.

## Load

```bash
export FHIRVAULT_LOADER_CLIENT_SECRET=...    # from deploy/.env
java -jar tools/loader/target/fhirvault-loader.jar tools/loader/sample output/fhir
```

| Option | Default |
|---|---|
| `--server` | `http://localhost:8080/fhir` |
| `--token-url` | `http://localhost:8081/realms/fhirvault/protocol/openid-connect/token` |
| `--client-id` | `fhirvault-loader` |
| `--no-auth` | off (use it against a server running the `noauth` profile) |

## What it changes in each bundle

- It keeps only the six supported resource types.
- It sends every entry as `PUT Type/<Synthea id>`, so loading the same files again updates them instead of duplicating them.
- References to Practitioner, Organization and Location (Synthea uses conditional references) become display-only references, because the server only accepts references it can check.
- `MedicationRequest.medicationReference` is replaced by the referenced Medication's code.
