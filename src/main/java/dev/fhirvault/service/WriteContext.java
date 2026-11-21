package dev.fhirvault.service;

import java.util.Set;

/**
 * Request facts write hooks need, as "Type/id" strings. {@code pendingCreates}: what the bundle will
 * create, so entries can reference each other before they're written. {@code pendingDeletes}: what it
 * will delete, so a resource can go together with its referrers.
 */
public record WriteContext(Set<String> pendingCreates, Set<String> pendingDeletes) {

    public static WriteContext single() {
        return new WriteContext(Set.of(), Set.of());
    }
}
