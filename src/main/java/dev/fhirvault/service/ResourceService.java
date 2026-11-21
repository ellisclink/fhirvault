package dev.fhirvault.service;

import ca.uhn.fhir.rest.server.exceptions.InvalidRequestException;
import ca.uhn.fhir.rest.server.exceptions.PreconditionFailedException;
import ca.uhn.fhir.rest.server.exceptions.ResourceGoneException;
import ca.uhn.fhir.rest.server.exceptions.ResourceNotFoundException;
import ca.uhn.fhir.rest.server.exceptions.ResourceVersionConflictException;
import dev.fhirvault.persistence.ResourceCodec;
import dev.fhirvault.persistence.ResourceRepository;
import dev.fhirvault.persistence.StoredResource;
import org.hl7.fhir.instance.model.api.IAnyResource;
import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.Resource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Versioned CRUD for any resource type. Each write runs in one transaction and joins a bundle's
 * transaction when there is one.
 */
@Service
public class ResourceService {

    /** FHIR R4 id rule: 1-64 characters from A-Z, a-z, 0-9, '-' and '.'. */
    private static final Pattern VALID_ID = Pattern.compile("[A-Za-z0-9\\-.]{1,64}");

    private final ResourceRepository repository;
    private final ResourceCodec codec;
    private final List<ResourceWriteHook> hooks;
    private final Clock clock;

    public ResourceService(ResourceRepository repository, ResourceCodec codec,
                           ObjectProvider<ResourceWriteHook> hooks,
                           Clock clock) {
        this.repository = repository;
        this.codec = codec;
        // @Order runs authorization, then validation, references and indexing.
        this.hooks = hooks.orderedStream().toList();
        this.clock = clock;
    }

    /** Creates under a new server id. Any id in the body is ignored, as FHIR requires. */
    @Transactional
    public WriteResult create(Resource resource) {
        return create(resource, newId(), WriteContext.single());
    }

    /** Create with an id chosen earlier (transaction bundles assign ids before writing). */
    @Transactional
    public WriteResult create(Resource resource, String id, WriteContext context) {
        String type = resource.fhirType();
        try {
            return write(type, id, resource, Optional.empty(), context);
        } catch (DuplicateKeyException e) {
            throw new ResourceVersionConflictException("Resource " + type + "/" + id + " already exists");
        }
    }

    @Transactional
    public WriteResult update(String type, String id, Resource resource, Long expectedVersion) {
        return update(type, id, resource, expectedVersion, WriteContext.single());
    }

    /**
     * Updates, or creates when the id is unused (update as create). {@code expectedVersion} comes from
     * If-Match; a mismatch is 412.
     */
    @Transactional
    public WriteResult update(String type, String id, Resource resource, Long expectedVersion, WriteContext context) {
        requireValidId(id);
        if (!type.equals(resource.fhirType())) {
            throw new InvalidRequestException("Body is a " + resource.fhirType() + " but the URL is for " + type);
        }
        if (!resource.hasIdElement() || !id.equals(resource.getIdElement().getIdPart())) {
            throw new InvalidRequestException("Resource id in the body must match the id in the URL (" + id + ")");
        }

        Optional<StoredResource> current = repository.findCurrentForUpdate(type, id);
        if (expectedVersion != null) {
            long actual = current.filter(c -> !c.deleted()).map(StoredResource::versionId).orElse(-1L);
            if (actual != expectedVersion) {
                throw new PreconditionFailedException("If-Match version " + expectedVersion
                        + " does not match current version of " + type + "/" + id);
            }
        }
        try {
            return write(type, id, resource, current, context);
        } catch (DuplicateKeyException e) {
            // Two clients raced to create the same id.
            throw new ResourceVersionConflictException("Concurrent create of " + type + "/" + id);
        }
    }

    /**
     * Deletes by writing a content-less version. Returns the deleted version, or empty if it was
     * already deleted.
     */
    @Transactional
    public Optional<StoredResource> delete(String type, String id) {
        return delete(type, id, WriteContext.single());
    }

    @Transactional
    public Optional<StoredResource> delete(String type, String id, WriteContext context) {
        StoredResource current = repository.findCurrentForUpdate(type, id)
                .orElseThrow(() -> new ResourceNotFoundException(new IdType(type, id)));
        if (current.deleted()) {
            return Optional.empty();
        }
        hooks.forEach(h -> h.beforeDelete(current, context));
        StoredResource tombstone = new StoredResource(type, id, current.versionId() + 1, now(), true, null);
        repository.updateCurrent(tombstone);
        hooks.forEach(h -> h.afterDelete(tombstone));
        return Optional.of(current);
    }

    @Transactional(readOnly = true)
    public Resource read(String type, String id) {
        StoredResource stored = repository.findCurrent(type, id)
                .orElseThrow(() -> new ResourceNotFoundException(new IdType(type, id)));
        if (stored.deleted()) {
            throw new ResourceGoneException(new IdType(type, id, Long.toString(stored.versionId())));
        }
        return codec.decode(stored);
    }

    @Transactional(readOnly = true)
    public Resource vread(String type, String id, long versionId) {
        StoredResource stored = repository.findVersion(type, id, versionId)
                .orElseThrow(() -> new ResourceNotFoundException(new IdType(type, id, Long.toString(versionId))));
        if (stored.deleted()) {
            throw new ResourceGoneException(new IdType(type, id, Long.toString(versionId)));
        }
        return codec.decode(stored);
    }

    @Transactional(readOnly = true)
    public Optional<StoredResource> findCurrent(String type, String id) {
        return repository.findCurrent(type, id).filter(r -> !r.deleted());
    }

    /** One page of history, newest first; deleted versions appear as DELETE entries. */
    public record HistoryPage(List<IAnyResource> entries, int total, int offset, int count) {
    }

    /**
     * History at instance level (type and id), type level (id null) or system level (both null).
     * Instance history of an unknown id is 404.
     */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public HistoryPage history(String type, String id, Instant since, int offset, int count) {
        if (id != null && repository.findCurrent(type, id).isEmpty()) {
            throw new ResourceNotFoundException(new IdType(type, id));
        }
        int total = repository.countHistory(type, id, since);
        List<IAnyResource> entries = count == 0 ? List.of()
                : repository.history(type, id, since, offset, count).stream().map(codec::toHistoryEntry).toList();
        return new HistoryPage(entries, total, offset, count);
    }

    public static String newId() {
        return UUID.randomUUID().toString();
    }

    private WriteResult write(String type, String id, Resource resource, Optional<StoredResource> current,
                              WriteContext context) {
        // Hooks need the final id for self-references and links.
        resource.setId(new IdType(type, id));
        Optional<StoredResource> live = current.filter(c -> !c.deleted());
        hooks.forEach(h -> h.beforeWrite(resource, live, context));
        long version = current.map(c -> c.versionId() + 1).orElse(1L);
        Instant now = now();
        String json = codec.prepareAndEncode(resource, id, version, now);
        StoredResource stored = new StoredResource(type, id, version, now, false, json);
        if (current.isPresent()) {
            repository.updateCurrent(stored);
        } else {
            repository.insertNew(stored);
        }
        hooks.forEach(h -> h.afterWrite(resource, stored));
        boolean created = current.isEmpty() || current.get().deleted();
        return new WriteResult(resource, stored, created);
    }

    private Instant now() {
        // FHIR instants have millisecond precision; truncating keeps DB and JSON equal.
        return clock.instant().truncatedTo(ChronoUnit.MILLIS);
    }

    private static void requireValidId(String id) {
        if (id == null || !VALID_ID.matcher(id).matches()) {
            throw new InvalidRequestException("Invalid resource id: must be 1-64 characters of [A-Za-z0-9-.]");
        }
    }
}
