package dev.fhirvault.audit;

import ca.uhn.fhir.interceptor.api.Hook;
import ca.uhn.fhir.interceptor.api.Interceptor;
import ca.uhn.fhir.interceptor.api.Pointcut;
import ca.uhn.fhir.model.api.ResourceMetadataKeyEnum;
import ca.uhn.fhir.rest.api.RestOperationTypeEnum;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import ca.uhn.fhir.rest.api.server.ResponseDetails;
import ca.uhn.fhir.rest.server.exceptions.BaseServerResponseException;
import ca.uhn.fhir.rest.server.servlet.ServletRequestDetails;
import dev.fhirvault.fhir.SystemProvider;
import dev.fhirvault.persistence.ResourceCodec;
import dev.fhirvault.persistence.ResourceRepository;
import dev.fhirvault.security.PatientCompartment;
import dev.fhirvault.security.SmartAccess;
import dev.fhirvault.security.SmartAuthorizer;
import dev.fhirvault.service.TransactionService;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Writes one AuditEvent per patient touched by a request, for successes and failures. Order 100 puts it
 * after the SMART checks. If a successful request can't be audited, the response fails, so no data
 * leaves without a record.
 */
@Component
@Interceptor
public class AuditInterceptor {

    private static final Logger log = LoggerFactory.getLogger(AuditInterceptor.class);
    private static final int AFTER_AUTHORIZATION = 100;

    private final AuditService auditService;
    private final SmartAuthorizer authorizer;
    private final PatientCompartment compartment;
    private final ResourceRepository resources;
    private final ResourceCodec codec;
    private final Clock clock;

    public AuditInterceptor(AuditService auditService, SmartAuthorizer authorizer, PatientCompartment compartment,
                            ResourceRepository resources, ResourceCodec codec, Clock clock) {
        this.auditService = auditService;
        this.authorizer = authorizer;
        this.compartment = compartment;
        this.resources = resources;
        this.codec = codec;
        this.clock = clock;
    }

    @Hook(value = Pointcut.SERVER_OUTGOING_RESPONSE, order = AFTER_AUTHORIZATION)
    public boolean auditSuccess(RequestDetails request, ResponseDetails response) {
        RestOperationTypeEnum operation = request.getRestOperationType();
        if (operation == null || operation == RestOperationTypeEnum.METADATA) {
            return true;
        }
        Map<String, List<String>> byPatient = new LinkedHashMap<>();
        IBaseResource body = response.getResponseResource();
        switch (operation) {
            case READ, VREAD -> addResource(byPatient, body);
            case SEARCH_TYPE, HISTORY_INSTANCE, HISTORY_TYPE, HISTORY_SYSTEM -> addBundleEntries(byPatient, body);
            case CREATE, UPDATE -> addResource(byPatient, body instanceof Resource ? body : request.getResource());
            case DELETE -> addStoredResource(byPatient, request.getResourceName(), request.getId().getIdPart());
            case TRANSACTION -> addTransaction(byPatient, request);
            default -> byPatient.put(null, new ArrayList<>());
        }
        if (byPatient.isEmpty()) {
            // A search with no results is still an access attempt.
            byPatient.put(null, new ArrayList<>());
        }
        for (Map.Entry<String, List<String>> entry : byPatient.entrySet()) {
            auditService.record(record(request, operation, "0", null, entry.getKey(), entry.getValue()));
        }
        return true;
    }

    @Hook(value = Pointcut.SERVER_HANDLE_EXCEPTION, order = AFTER_AUTHORIZATION)
    public boolean auditFailure(RequestDetails request, BaseServerResponseException exception) {
        RestOperationTypeEnum operation = request.getRestOperationType();
        if (operation == null || operation == RestOperationTypeEnum.METADATA) {
            return true;
        }
        try {
            Map<String, List<String>> byPatient = new LinkedHashMap<>();
            if (request.getId() != null && request.getId().hasIdPart()) {
                // URL ids are untrusted; truncate to the column width so the insert can't fail.
                String type = request.getResourceName();
                String id = truncate(request.getId().getIdPart(), 64);
                if ("Patient".equals(type)) {
                    byPatient.put(id, new ArrayList<>(List.of(type + "/" + id)));
                } else {
                    addStoredResource(byPatient, type, id);
                }
            }
            if (byPatient.isEmpty()) {
                byPatient.put(null, new ArrayList<>());
            }
            String outcome = exception.getStatusCode() >= 500 ? "8" : "4";
            String description = exception.getStatusCode() + " " + firstLine(exception.getMessage());
            for (Map.Entry<String, List<String>> entry : byPatient.entrySet()) {
                auditService.record(record(request, operation, outcome, description, entry.getKey(), entry.getValue()));
            }
        } catch (RuntimeException e) {
            // Don't let an audit failure mask the original error.
            log.error("Could not write audit record for failed request {}", request.getRequestId(), e);
        }
        return true;
    }

    private AuditRecord record(RequestDetails request, RestOperationTypeEnum operation, String outcome,
                               String description, String patientId, List<String> entities) {
        SmartAccess access = currentAccess();
        String query = operation == RestOperationTypeEnum.SEARCH_TYPE ? queryString(request) : null;
        return new AuditRecord(clock.instant(), action(operation), outcome, description, operation.getCode(),
                patientId, entities, query,
                access == null ? "unknown" : access.subject(),
                access == null ? null : access.clientId(),
                clientAddress(request), request.getRequestId());
    }

    private void addResource(Map<String, List<String>> byPatient, IBaseResource resource) {
        if (resource instanceof Resource r && !"OperationOutcome".equals(r.fhirType())) {
            String entity = r.getIdElement().toUnqualified().getValue();
            Set<String> patients = compartment.patientIds(r);
            if (patients.isEmpty()) {
                byPatient.computeIfAbsent(null, k -> new ArrayList<>()).add(entity);
            }
            for (String patient : patients) {
                byPatient.computeIfAbsent(patient, k -> new ArrayList<>()).add(entity);
            }
        }
    }

    private void addBundleEntries(Map<String, List<String>> byPatient, IBaseResource body) {
        if (body instanceof Bundle bundle) {
            for (Bundle.BundleEntryComponent entry : bundle.getEntry()) {
                if (entry.hasResource() && ResourceMetadataKeyEnum.DELETED_AT.get(entry.getResource()) == null) {
                    addResource(byPatient, entry.getResource());
                }
            }
        }
    }

    /**
     * Deletes and failures carry no content, so the patient comes from the newest stored version that
     * has content.
     */
    private void addStoredResource(Map<String, List<String>> byPatient, String type, String id) {
        String entity = type + "/" + id;
        Set<String> patients = Set.of();
        try {
            patients = resources.findLatestWithContent(type, id)
                    .map(latest -> compartment.patientIds(codec.decode(latest)))
                    .orElse(Set.of());
        } catch (RuntimeException e) {
            log.warn("Could not resolve patient for audit of {}", entity, e);
        }
        if (patients.isEmpty()) {
            byPatient.computeIfAbsent(null, k -> new ArrayList<>()).add(entity);
        }
        for (String patient : patients) {
            byPatient.computeIfAbsent(patient, k -> new ArrayList<>()).add(entity);
        }
    }

    @SuppressWarnings("unchecked")
    private void addTransaction(Map<String, List<String>> byPatient, RequestDetails request) {
        Object touched = request.getUserData().get(SystemProvider.TOUCHED_RESOURCES);
        if (touched instanceof List<?> list) {
            for (TransactionService.Touched t : (List<TransactionService.Touched>) list) {
                if (t.resource() == null) {
                    addStoredResource(byPatient, t.type(), t.id());
                } else {
                    addResource(byPatient, t.resource());
                }
            }
        }
    }

    private SmartAccess currentAccess() {
        try {
            return authorizer.access();
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static String action(RestOperationTypeEnum operation) {
        return switch (operation) {
            case CREATE -> "C";
            case UPDATE -> "U";
            case DELETE -> "D";
            case TRANSACTION -> "E";
            default -> "R";
        };
    }

    private static String queryString(RequestDetails request) {
        return request instanceof ServletRequestDetails servlet ? servlet.getServletRequest().getQueryString() : null;
    }

    private static String clientAddress(RequestDetails request) {
        return request instanceof ServletRequestDetails servlet ? servlet.getServletRequest().getRemoteAddr() : null;
    }

    private static String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }

    private static String firstLine(String message) {
        if (message == null) {
            return "";
        }
        String line = message.lines().findFirst().orElse("");
        return line.length() > 500 ? line.substring(0, 500) : line;
    }
}
