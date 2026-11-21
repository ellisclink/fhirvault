package dev.fhirvault.security;

import dev.fhirvault.security.SmartAccess.Decision;
import dev.fhirvault.security.SmartScope.Context;
import dev.fhirvault.security.SmartScope.Permission;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.EnumSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SmartScopeTest {

    @Test
    void parsesV2Scope() {
        assertThat(SmartScope.parse("patient/Observation.rs")).contains(
                new SmartScope(Context.PATIENT, "Observation", EnumSet.of(Permission.R, Permission.S)));
    }

    @Test
    void mapsV1ReadWriteAndStar() {
        assertThat(SmartScope.parse("user/*.read").orElseThrow().permissions()).containsExactlyInAnyOrder(Permission.R, Permission.S);
        assertThat(SmartScope.parse("user/*.write").orElseThrow().permissions())
                .containsExactlyInAnyOrder(Permission.C, Permission.U, Permission.D);
        assertThat(SmartScope.parse("system/*.*").orElseThrow().permissions()).containsExactlyInAnyOrder(Permission.values());
    }

    @ParameterizedTest
    @ValueSource(strings = {"openid", "launch/patient", "fhirUser", "patient/Observation.sr", "patient/Observation.rs?category=x",
            "admin/*.read", "patient/observation.rs", "patient/*.xyz"})
    void ignoresNonResourceOrMalformedScopes(String scope) {
        assertThat(SmartScope.parse(scope)).isEmpty();
    }

    @Test
    void wildcardDoesNotCoverAuditEvent() {
        SmartScope all = SmartScope.parse("system/*.cruds").orElseThrow();

        assertThat(all.covers("Observation", Permission.R)).isTrue();
        assertThat(all.covers("AuditEvent", Permission.R)).isFalse();
        assertThat(SmartScope.parse("system/AuditEvent.rs").orElseThrow().covers("AuditEvent", Permission.S)).isTrue();
    }

    @Test
    void userScopeBeatsPatientScope() {
        SmartAccess access = new SmartAccess(List.of(
                SmartScope.parse("patient/*.rs").orElseThrow(),
                SmartScope.parse("user/Observation.rs").orElseThrow()), "p1", "u", "c", false);

        assertThat(access.decide("Observation", Permission.R)).isEqualTo(Decision.ALLOW);
        assertThat(access.decide("Condition", Permission.R)).isEqualTo(Decision.PATIENT_COMPARTMENT);
        assertThat(access.decide("Condition", Permission.C)).isEqualTo(Decision.DENY);
    }

    @Test
    void patientScopeWithoutLaunchPatientGrantsNothing() {
        SmartAccess access = new SmartAccess(List.of(SmartScope.parse("patient/*.rs").orElseThrow()), null, "u", "c", false);

        assertThat(access.decide("Observation", Permission.R)).isEqualTo(Decision.DENY);
    }
}
