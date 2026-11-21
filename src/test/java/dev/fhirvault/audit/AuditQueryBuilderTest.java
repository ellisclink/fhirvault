package dev.fhirvault.audit;

import ca.uhn.fhir.rest.param.DateAndListParam;
import ca.uhn.fhir.rest.param.DateParam;
import ca.uhn.fhir.rest.param.ReferenceAndListParam;
import ca.uhn.fhir.rest.param.ReferenceOrListParam;
import ca.uhn.fhir.rest.param.ReferenceParam;
import ca.uhn.fhir.rest.server.exceptions.InvalidRequestException;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuditQueryBuilderTest {

    @Test
    void repeatedLowerBoundsIntersect() {
        DateAndListParam dates = new DateAndListParam()
                .addAnd(new DateParam("ge2025")).addAnd(new DateParam("ge2024")).addAnd(new DateParam("lt2026-06"));

        AuditRepository.Filter filter = AuditQueryBuilder.build(null, dates, null, null, null, null).orElseThrow();

        assertThat(filter.from()).isEqualTo(Instant.parse("2025-01-01T00:00:00Z"));
        assertThat(filter.to()).isEqualTo(Instant.parse("2026-06-01T00:00:00Z"));
    }

    @Test
    void twoDifferentPatientsCanNeverMatch() {
        ReferenceAndListParam patients = new ReferenceAndListParam()
                .addAnd(new ReferenceOrListParam().add(new ReferenceParam("Patient/a")))
                .addAnd(new ReferenceOrListParam().add(new ReferenceParam("Patient/b")));

        assertThat(AuditQueryBuilder.build(patients, null, null, null, null, null)).isEmpty();
    }

    @Test
    void samePatientTwiceIsFine() {
        ReferenceAndListParam patients = new ReferenceAndListParam()
                .addAnd(new ReferenceOrListParam().add(new ReferenceParam("Patient/a")))
                .addAnd(new ReferenceOrListParam().add(new ReferenceParam("a")));

        assertThat(AuditQueryBuilder.build(patients, null, null, null, null, null).orElseThrow().patientId()).isEqualTo("a");
    }

    @Test
    void negativePagingIsRejected() {
        assertThatThrownBy(() -> AuditQueryBuilder.build(null, null, null, null, -1, null))
                .isInstanceOf(InvalidRequestException.class);
    }
}
