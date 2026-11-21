package dev.fhirvault.security;

import ca.uhn.fhir.interceptor.api.Hook;
import ca.uhn.fhir.interceptor.api.Interceptor;
import ca.uhn.fhir.interceptor.api.Pointcut;
import ca.uhn.fhir.rest.api.RestOperationTypeEnum;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import ca.uhn.fhir.rest.api.server.ResponseDetails;
import ca.uhn.fhir.rest.server.exceptions.ForbiddenOperationException;
import dev.fhirvault.fhir.SupportedResourceTypes;
import dev.fhirvault.security.SmartAccess.Decision;
import dev.fhirvault.security.SmartScope.Permission;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Resource;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Request-level SMART checks. Patient-scoped searches are narrowed before parameters bind, the
 * interaction is checked before the handler runs, and every returned resource is checked again
 * afterwards.
 */
@Component
@Interceptor
public class SmartAuthorizationInterceptor {

    private final SmartAuthorizer authorizer;

    public SmartAuthorizationInterceptor(SmartAuthorizer authorizer) {
        this.authorizer = authorizer;
    }

    /**
     * Narrow here: HAPI binds search parameters after this pointcut and before PRE_HANDLED, so later
     * changes are ignored.
     */
    @Hook(Pointcut.SERVER_INCOMING_REQUEST_POST_PROCESSED)
    public boolean narrowSearches(RequestDetails request) {
        if (request.getRestOperationType() == RestOperationTypeEnum.SEARCH_TYPE) {
            String type = request.getResourceName();
            SmartAccess access = authorizer.access();
            if (access.decide(type, Permission.S) == Decision.PATIENT_COMPARTMENT) {
                narrowToPatient(request, type, access.patientId());
            }
        }
        return true;
    }

    @Hook(Pointcut.SERVER_INCOMING_REQUEST_PRE_HANDLED)
    public void beforeHandling(RequestDetails request, RestOperationTypeEnum operation) {
        String type = request.getResourceName();
        switch (operation) {
            case METADATA, TRANSACTION -> {
                // Metadata is public. Transaction entries are checked one by one in the service layer.
            }
            case READ, VREAD -> authorizer.require(type, Permission.R);
            case HISTORY_INSTANCE -> authorizer.checkInstanceHistory(type, request.getId().getIdPart());
            case SEARCH_TYPE -> authorizer.require(type, Permission.S);
            case HISTORY_TYPE -> {
                if (authorizer.require(type, Permission.S) == Decision.PATIENT_COMPARTMENT) {
                    throw new ForbiddenOperationException("Type-level history is not available with patient scopes");
                }
            }
            case HISTORY_SYSTEM -> {
                for (String clinicalType : SupportedResourceTypes.CLINICAL) {
                    if (authorizer.access().decide(clinicalType, Permission.S) != Decision.ALLOW) {
                        throw new ForbiddenOperationException("System history needs search access to every type");
                    }
                }
            }
            case CREATE -> authorizer.require(type, Permission.C);
            case UPDATE -> {
                // Update may create. The write hook decides which once it knows whether the id exists.
                if (authorizer.access().decide(type, Permission.U) == Decision.DENY) {
                    authorizer.require(type, Permission.C);
                }
            }
            case DELETE -> authorizer.require(type, Permission.D);
            default -> throw new ForbiddenOperationException("Operation " + operation.getCode() + " is not permitted");
        }
    }

    @Hook(Pointcut.SERVER_OUTGOING_RESPONSE)
    public boolean beforeResponding(RequestDetails request, ResponseDetails response) {
        IBaseResource body = response.getResponseResource();
        RestOperationTypeEnum operation = request.getRestOperationType();
        if (operation == null || body == null) {
            return true;
        }
        switch (operation) {
            case READ, VREAD -> checkResource(body, Permission.R);
            case HISTORY_INSTANCE -> checkEntries(body, Permission.R);
            case SEARCH_TYPE, HISTORY_TYPE, HISTORY_SYSTEM -> checkEntries(body, Permission.S);
            default -> {
                // The write hook and transaction service already checked writes.
            }
        }
        return true;
    }

    private void checkEntries(IBaseResource body, Permission permission) {
        if (body instanceof Bundle bundle) {
            for (Bundle.BundleEntryComponent entry : bundle.getEntry()) {
                if (entry.hasResource()) {
                    checkResource(entry.getResource(), permission);
                }
            }
        }
    }

    private void checkResource(IBaseResource resource, Permission permission) {
        if (resource instanceof Resource r && !"OperationOutcome".equals(r.fhirType())) {
            authorizer.checkOutgoing(r, permission);
        }
    }

    /** Adds an AND-ed patient filter, so results only ever contain the launch patient's data. */
    static void narrowToPatient(RequestDetails request, String type, String patientId) {
        Map<String, String[]> parameters = new HashMap<>(request.getParameters());
        String name = "Patient".equals(type) ? "_id" : "patient";
        String value = "Patient".equals(type) ? patientId : "Patient/" + patientId;
        String[] existing = parameters.getOrDefault(name, new String[0]);
        String[] narrowed = Arrays.copyOf(existing, existing.length + 1);
        narrowed[existing.length] = value;
        parameters.put(name, narrowed);
        request.setParameters(parameters);
    }
}
