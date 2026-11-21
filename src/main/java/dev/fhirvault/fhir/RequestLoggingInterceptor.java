package dev.fhirvault.fhir;

import ca.uhn.fhir.interceptor.api.Hook;
import ca.uhn.fhir.interceptor.api.Interceptor;
import ca.uhn.fhir.interceptor.api.Pointcut;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import ca.uhn.fhir.rest.server.servlet.ServletRequestDetails;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

/**
 * One log line per request: request id, interaction, Type/id, status and duration. Query strings and
 * bodies are never logged because they can carry patient identifiers. The request id also goes into the
 * MDC and matches X-Request-ID and the AuditEvent.
 */
@Component
@Interceptor
public class RequestLoggingInterceptor {

    static final String MDC_KEY = "requestId";
    private static final Logger log = LoggerFactory.getLogger("fhirvault.requests");

    @Hook(Pointcut.SERVER_INCOMING_REQUEST_POST_PROCESSED)
    public boolean start(RequestDetails request) {
        MDC.put(MDC_KEY, request.getRequestId());
        return true;
    }

    @Hook(Pointcut.SERVER_PROCESSING_COMPLETED)
    public void finish(RequestDetails request) {
        try {
            String operation = request.getRestOperationType() == null ? "unknown" : request.getRestOperationType().getCode();
            String target = request.getResourceName() == null ? "-"
                    : request.getId() != null && request.getId().hasIdPart()
                    ? request.getResourceName() + "/" + request.getId().getIdPart()
                    : request.getResourceName();
            int status = request instanceof ServletRequestDetails servlet ? servlet.getServletResponse().getStatus() : 0;
            long millis = request.getRequestStopwatch() == null ? -1 : request.getRequestStopwatch().getMillis();
            log.info("request={} op={} target={} status={} ms={}", request.getRequestId(), operation, target, status, millis);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }
}
