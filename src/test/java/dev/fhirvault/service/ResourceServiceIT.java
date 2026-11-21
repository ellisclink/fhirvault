package dev.fhirvault.service;

import ca.uhn.fhir.rest.server.exceptions.PreconditionFailedException;
import ca.uhn.fhir.rest.server.exceptions.ResourceGoneException;
import dev.fhirvault.TestData;
import dev.fhirvault.TestcontainersConfiguration;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Exercises the versioning rules directly, without HTTP. */
@SpringBootTest
@ActiveProfiles("noauth")
@Import(TestcontainersConfiguration.class)
class ResourceServiceIT {

    @Autowired
    private ResourceService service;

    @Test
    void versionsIncreaseByOneAndLastUpdatedMovesForward() {
        WriteResult v1 = service.create(TestData.patient("Lee", "Ann"));
        String id = v1.stored().id();
        Patient body = TestData.patient("Lee", "Anne");
        body.setId(id);

        WriteResult v2 = service.update("Patient", id, body, 1L);

        assertThat(v1.created()).isTrue();
        assertThat(v2.created()).isFalse();
        assertThat(v2.stored().versionId()).isEqualTo(2);
        assertThat(v2.stored().lastUpdated()).isAfterOrEqualTo(v1.stored().lastUpdated());
    }

    @Test
    void ifMatchOnDeletedResourceFails() {
        String id = service.create(TestData.patient("Lee", "Ann")).stored().id();
        service.delete("Patient", id);
        Patient body = TestData.patient("Lee", "Ann");
        body.setId(id);

        assertThatThrownBy(() -> service.update("Patient", id, body, 1L))
                .isInstanceOf(PreconditionFailedException.class);
    }

    @Test
    void deleteReturnsDeletedVersionThenEmptyWhenRepeated() {
        String id = service.create(TestData.patient("Lee", "Ann")).stored().id();

        assertThat(service.delete("Patient", id)).hasValueSatisfying(v -> assertThat(v.versionId()).isEqualTo(1));
        assertThat(service.delete("Patient", id)).isEmpty();
        assertThatThrownBy(() -> service.read("Patient", id)).isInstanceOf(ResourceGoneException.class);
    }

    @Test
    void storedJsonCarriesServerManagedMeta() {
        String id = service.create(TestData.patient("Lee", "Ann")).stored().id();

        Resource read = service.read("Patient", id);

        assertThat(read.getIdElement().getIdPart()).isEqualTo(id);
        assertThat(read.getIdElement().getVersionIdPart()).isEqualTo("1");
        assertThat(read.getMeta().getVersionId()).isEqualTo("1");
    }
}
