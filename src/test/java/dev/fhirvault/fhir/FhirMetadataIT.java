package dev.fhirvault.fhir;

import dev.fhirvault.AbstractApiIT;
import org.hl7.fhir.r4.model.CapabilityStatement;
import org.hl7.fhir.r4.model.Enumerations.FHIRVersion;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;

import static org.assertj.core.api.Assertions.assertThat;

class FhirMetadataIT extends AbstractApiIT {

    @Autowired
    private JdbcClient jdbc;

    @Test
    void metadataReturnsR4CapabilityStatement() {
        ResponseEntity<String> response = get("metadata");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        CapabilityStatement capability = parse(CapabilityStatement.class, response);
        assertThat(capability.getFhirVersion()).isEqualTo(FHIRVersion._4_0_1);
    }

    @Test
    void flywayMigrationHasRun() {
        String value = jdbc.sql("SELECT value FROM schema_info WHERE key = 'created_by'")
                .query(String.class)
                .single();

        assertThat(value).isEqualTo("V1__create_schema_info");
    }
}
