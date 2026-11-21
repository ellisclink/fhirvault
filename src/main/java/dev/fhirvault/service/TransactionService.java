package dev.fhirvault.service;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.server.exceptions.BaseServerResponseException;
import ca.uhn.fhir.rest.server.exceptions.InvalidRequestException;
import ca.uhn.fhir.util.ResourceReferenceInfo;
import dev.fhirvault.fhir.SupportedResourceTypes;
import dev.fhirvault.persistence.StoredResource;
import dev.fhirvault.security.SmartAuthorizer;
import dev.fhirvault.security.SmartScope;
import org.hl7.fhir.instance.model.api.IBaseOperationOutcome;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Bundle.BundleEntryComponent;
import org.hl7.fhir.r4.model.Bundle.HTTPVerb;
import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Processes a transaction Bundle atomically. Entries use the same {@link ResourceService} pipeline as
 * single requests, all in one database transaction.
 */
@Service
public class TransactionService {

    public static final int MAX_ENTRIES = 5000;

    private static final Pattern TYPE_ONLY = Pattern.compile("^([A-Z][A-Za-z]+)$");
    private static final Pattern TYPE_AND_ID = Pattern.compile("^([A-Z][A-Za-z]+)/([A-Za-z0-9\\-.]{1,64})$");
    private static final Pattern TYPE_ID_VERSION =
            Pattern.compile("^([A-Z][A-Za-z]+)/([A-Za-z0-9\\-.]{1,64})/_history/([0-9]{1,18})$");

    /** One parsed entry; {@code index} is its position in the request bundle. */
    public record PlannedEntry(int index, HTTPVerb method, String type, String id, Long version,
                               Resource resource, Long expectedVersion) {

        String typeAndId() {
            return type + "/" + id;
        }
    }

    /** The response bundle plus what was touched, for auditing. */
    public record TransactionResult(Bundle response, List<Touched> touched) {
    }

    /** A resource version the transaction read or wrote; {@code resource} is null for deletes. */
    public record Touched(HTTPVerb method, String type, String id, Resource resource) {
    }

    private final ResourceService resourceService;
    private final FhirContext fhirContext;
    private final SmartAuthorizer authorizer;

    public TransactionService(ResourceService resourceService, FhirContext fhirContext, SmartAuthorizer authorizer) {
        this.resourceService = resourceService;
        this.fhirContext = fhirContext;
        this.authorizer = authorizer;
    }

    /** Parses and checks the bundle without touching the database. */
    public List<PlannedEntry> plan(Bundle bundle) {
        if (bundle.getType() != Bundle.BundleType.TRANSACTION) {
            throw new InvalidRequestException("Only Bundle.type = transaction is supported");
        }
        if (bundle.getEntry().size() > MAX_ENTRIES) {
            throw new InvalidRequestException("Transaction has " + bundle.getEntry().size()
                    + " entries; the limit is " + MAX_ENTRIES);
        }
        List<PlannedEntry> planned = new ArrayList<>();
        Map<String, String> fullUrlToId = new HashMap<>();
        Set<String> writeTargets = new HashSet<>();
        for (int i = 0; i < bundle.getEntry().size(); i++) {
            PlannedEntry entry = planEntry(i, bundle.getEntry().get(i));
            if (entry.method() != HTTPVerb.GET && !writeTargets.add(entry.typeAndId())) {
                throw entryError(i, new InvalidRequestException(
                        "More than one entry writes " + entry.typeAndId()));
            }
            String fullUrl = bundle.getEntry().get(i).getFullUrl();
            if (fullUrl != null && entry.resource() != null) {
                fullUrlToId.put(fullUrl, entry.typeAndId());
            }
            planned.add(entry);
        }
        rewriteReferences(planned, fullUrlToId);
        return planned;
    }

    /** Runs the plan in FHIR order (DELETE, POST, PUT, GET) and returns responses in request order. */
    @Transactional
    public TransactionResult execute(Bundle bundle) {
        List<PlannedEntry> planned = plan(bundle);
        WriteContext context = new WriteContext(
                collect(planned, HTTPVerb.POST, HTTPVerb.PUT),
                collect(planned, HTTPVerb.DELETE));

        Bundle response = new Bundle().setType(Bundle.BundleType.TRANSACTIONRESPONSE);
        BundleEntryComponent[] responseEntries = new BundleEntryComponent[planned.size()];
        List<Touched> touched = new ArrayList<>();

        List<PlannedEntry> ordered = planned.stream()
                .sorted(Comparator.comparingInt((PlannedEntry e) -> processingOrder(e.method()))
                        .thenComparingInt(PlannedEntry::index))
                .toList();
        for (PlannedEntry entry : ordered) {
            try {
                responseEntries[entry.index()] = process(entry, context, touched);
            } catch (BaseServerResponseException e) {
                throw entryError(entry.index(), e);
            }
        }
        for (BundleEntryComponent entry : responseEntries) {
            response.addEntry(entry);
        }
        return new TransactionResult(response, touched);
    }

    private BundleEntryComponent process(PlannedEntry entry, WriteContext context, List<Touched> touched) {
        BundleEntryComponent out = new BundleEntryComponent();
        switch (entry.method()) {
            case DELETE -> {
                resourceService.delete(entry.type(), entry.id(), context);
                touched.add(new Touched(HTTPVerb.DELETE, entry.type(), entry.id(), null));
                out.getResponse().setStatus("204 No Content");
            }
            case POST -> {
                WriteResult result = resourceService.create(entry.resource(), entry.id(), context);
                touched.add(new Touched(HTTPVerb.POST, entry.type(), entry.id(), result.resource()));
                describeWrite(out, result, "201 Created");
            }
            case PUT -> {
                WriteResult result = resourceService.update(entry.type(), entry.id(), entry.resource(),
                        entry.expectedVersion(), context);
                touched.add(new Touched(HTTPVerb.PUT, entry.type(), entry.id(), result.resource()));
                describeWrite(out, result, result.created() ? "201 Created" : "200 OK");
            }
            case GET -> {
                Resource resource = entry.version() == null
                        ? resourceService.read(entry.type(), entry.id())
                        : resourceService.vread(entry.type(), entry.id(), entry.version());
                // The write hook authorizes writes; bundle reads are checked here.
                authorizer.checkOutgoing(resource, SmartScope.Permission.R);
                touched.add(new Touched(HTTPVerb.GET, entry.type(), entry.id(), resource));
                out.setResource(resource);
                out.getResponse().setStatus("200 OK")
                        .setEtag("W/\"" + resource.getIdElement().getVersionIdPart() + "\"")
                        .setLastModified(resource.getMeta().getLastUpdated());
            }
            default -> throw new InvalidRequestException("Unsupported method " + entry.method());
        }
        return out;
    }

    private static void describeWrite(BundleEntryComponent out, WriteResult result, String status) {
        StoredResource stored = result.stored();
        out.getResponse()
                .setStatus(status)
                .setLocation(stored.resourceType() + "/" + stored.id() + "/_history/" + stored.versionId())
                .setEtag("W/\"" + stored.versionId() + "\"")
                .setLastModified(Date.from(stored.lastUpdated()));
    }

    private PlannedEntry planEntry(int index, BundleEntryComponent entry) {
        Bundle.BundleEntryRequestComponent request = entry.getRequest();
        if (request.getMethod() == null || request.getUrl() == null) {
            throw entryError(index, new InvalidRequestException("request.method and request.url are required"));
        }
        if (request.hasIfNoneExist() || request.hasIfNoneMatch() || request.hasIfModifiedSince()) {
            throw entryError(index, new InvalidRequestException("Conditional operations are not supported"));
        }
        String url = request.getUrl();
        if (url.contains("?")) {
            throw entryError(index, new InvalidRequestException(
                    "Conditional or search URLs are not supported in transactions: " + url));
        }
        Resource resource = entry.getResource();
        HTTPVerb method = request.getMethod();
        return switch (method) {
            case POST -> {
                Matcher m = match(index, TYPE_ONLY, url);
                requireResource(index, resource, m.group(1));
                yield new PlannedEntry(index, method, m.group(1), ResourceService.newId(), null, resource, null);
            }
            case PUT -> {
                Matcher m = match(index, TYPE_AND_ID, url);
                requireResource(index, resource, m.group(1));
                Long expected = request.hasIfMatch() ? parseEtag(index, request.getIfMatch()) : null;
                yield new PlannedEntry(index, method, m.group(1), m.group(2), null, resource, expected);
            }
            case DELETE -> {
                Matcher m = match(index, TYPE_AND_ID, url);
                yield new PlannedEntry(index, method, m.group(1), m.group(2), null, null, null);
            }
            case GET -> {
                Matcher versioned = TYPE_ID_VERSION.matcher(url);
                if (versioned.matches()) {
                    yield new PlannedEntry(index, method, versioned.group(1), versioned.group(2),
                            Long.parseLong(versioned.group(3)), null, null);
                }
                Matcher m = match(index, TYPE_AND_ID, url);
                yield new PlannedEntry(index, method, m.group(1), m.group(2), null, null, null);
            }
            default -> throw entryError(index, new InvalidRequestException("Unsupported method " + method));
        };
    }

    private static Matcher match(int index, Pattern pattern, String url) {
        Matcher m = pattern.matcher(url);
        if (!m.matches() || !SupportedResourceTypes.isClinical(m.group(1))) {
            throw entryError(index, new InvalidRequestException("Unsupported request.url: " + url));
        }
        return m;
    }

    private static void requireResource(int index, Resource resource, String type) {
        if (resource == null) {
            throw entryError(index, new InvalidRequestException("POST and PUT entries need a resource"));
        }
        if (!type.equals(resource.fhirType())) {
            throw entryError(index, new InvalidRequestException(
                    "request.url is for " + type + " but the resource is a " + resource.fhirType()));
        }
    }

    private static Long parseEtag(int index, String etag) {
        String digits = etag.replaceAll("^W/", "").replace("\"", "");
        try {
            return Long.parseLong(digits);
        } catch (NumberFormatException e) {
            throw entryError(index, new InvalidRequestException("Invalid ifMatch: " + etag));
        }
    }

    /**
     * Points references at server-chosen ids: a reference equal to another entry's fullUrl becomes
     * "Type/id", and PUT bodies get the id from their URL.
     */
    private void rewriteReferences(List<PlannedEntry> planned, Map<String, String> fullUrlToId) {
        for (PlannedEntry entry : planned) {
            if (entry.resource() == null) {
                continue;
            }
            if (entry.method() == HTTPVerb.PUT && needsUrlId(entry.resource())) {
                entry.resource().setId(new IdType(entry.type(), entry.id()));
            }
            for (ResourceReferenceInfo info : fhirContext.newTerser().getAllResourceReferences(entry.resource())) {
                String target = fullUrlToId.get(info.getResourceReference().getReferenceElement().getValue());
                if (target != null) {
                    info.getResourceReference().setReference(target);
                }
            }
        }
    }

    /**
     * True when the body has no id, or HAPI's parser replaced it with the urn fullUrl (it does when the
     * urn ends with the id). The URL wins.
     */
    private static boolean needsUrlId(Resource resource) {
        return !resource.hasIdElement() || resource.getIdElement().getValue().startsWith("urn:");
    }

    private static Set<String> collect(List<PlannedEntry> planned, HTTPVerb... methods) {
        Set<String> ids = new HashSet<>();
        for (PlannedEntry entry : planned) {
            for (HTTPVerb method : methods) {
                if (entry.method() == method) {
                    ids.add(entry.typeAndId());
                }
            }
        }
        return ids;
    }

    private static int processingOrder(HTTPVerb method) {
        return switch (method) {
            case DELETE -> 0;
            case POST -> 1;
            case PUT, PATCH -> 2;
            default -> 3;
        };
    }

    /** Rethrows an entry's error with its status and the entry index, so clients can find it. */
    private static BaseServerResponseException entryError(int index, BaseServerResponseException cause) {
        String where = "Bundle.entry[" + index + "]";
        BaseServerResponseException wrapped =
                BaseServerResponseException.newInstance(cause.getStatusCode(), where + ": " + cause.getMessage());
        IBaseOperationOutcome original = cause.getOperationOutcome();
        if (original instanceof OperationOutcome outcome) {
            OperationOutcome copy = outcome.copy();
            for (OperationOutcome.OperationOutcomeIssueComponent issue : copy.getIssue()) {
                issue.setDiagnostics(where + ": " + issue.getDiagnostics());
                issue.getExpression().forEach(e -> e.setValue(where + ".resource." + e.getValue()));
            }
            wrapped.setOperationOutcome(copy);
        }
        return wrapped;
    }
}
