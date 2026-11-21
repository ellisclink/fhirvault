package dev.fhirvault.service;

import dev.fhirvault.persistence.StoredResource;
import org.hl7.fhir.r4.model.Resource;

import java.util.Optional;

/**
 * One step of the write pipeline. {@link ResourceService} runs the hooks in {@code @Order} inside the
 * write's transaction. Before-hooks reject by throwing; after-hooks maintain derived data.
 */
public interface ResourceWriteHook {

    /** @param current the live version being replaced, empty for a create or re-create. */
    default void beforeWrite(Resource resource, Optional<StoredResource> current, WriteContext context) {
    }

    default void afterWrite(Resource resource, StoredResource stored) {
    }

    default void beforeDelete(StoredResource current, WriteContext context) {
    }

    default void afterDelete(StoredResource tombstone) {
    }
}
