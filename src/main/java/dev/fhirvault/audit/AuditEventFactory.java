package dev.fhirvault.audit;

import org.hl7.fhir.r4.model.AuditEvent;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Reference;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * Builds FHIR AuditEvents in the IHE BALP layout: type "rest" with the interaction as subtype, the user
 * and client app as agents, and the patient and touched resources as entities.
 */
@Component
public class AuditEventFactory {

    static final String AUDIT_EVENT_TYPE = "http://terminology.hl7.org/CodeSystem/audit-event-type";
    static final String RESTFUL_INTERACTION = "http://hl7.org/fhir/restful-interaction";
    static final String ENTITY_TYPE = "http://terminology.hl7.org/CodeSystem/audit-entity-type";
    static final String OBJECT_ROLE = "http://terminology.hl7.org/CodeSystem/object-role";
    static final String SUBJECT_SYSTEM = "urn:fhirvault:token-subject";
    static final String CLIENT_SYSTEM = "urn:fhirvault:oauth-client";
    static final String SOURCE_TYPE = "http://terminology.hl7.org/CodeSystem/security-source-type";
    static final String BALP_ENTITY_TYPE = "https://profiles.ihe.net/ITI/BALP/CodeSystem/BasicAuditEntityType";

    public AuditEvent build(String id, AuditRecord record) {
        AuditEvent event = new AuditEvent();
        event.setId(id);
        event.setType(new Coding(AUDIT_EVENT_TYPE, "rest", "Restful Operation"));
        event.addSubtype(new Coding(RESTFUL_INTERACTION, record.interaction(), record.interaction()));
        event.setAction(AuditEvent.AuditEventAction.fromCode(record.action()));
        event.setRecorded(Date.from(record.recorded()));
        event.setOutcome(AuditEvent.AuditEventOutcome.fromCode(record.outcome()));
        if (record.outcomeDescription() != null) {
            event.setOutcomeDesc(record.outcomeDescription());
        }

        AuditEvent.AuditEventAgentComponent user = event.addAgent();
        user.setRequestor(true);
        user.setWho(new Reference().setIdentifier(new Identifier().setSystem(SUBJECT_SYSTEM).setValue(record.agentSubject()))
                .setDisplay(record.agentSubject()));
        if (record.clientAddress() != null) {
            user.getNetwork().setAddress(record.clientAddress()).setType(AuditEvent.AuditEventAgentNetworkType._2);
        }
        if (record.agentClient() != null) {
            event.addAgent().setRequestor(false)
                    .setWho(new Reference().setIdentifier(new Identifier().setSystem(CLIENT_SYSTEM).setValue(record.agentClient()))
                            .setDisplay(record.agentClient()));
        }

        event.getSource().setSite("fhirvault").setObserver(new Reference().setDisplay("fhirvault FHIR server"));
        event.getSource().addType(new Coding(SOURCE_TYPE, "4", "Application Server"));
        if (record.requestId() != null) {
            // IHE BALP: links this record to the request's log lines.
            event.addEntity()
                    .setWhat(new Reference().setIdentifier(new Identifier().setValue(record.requestId())))
                    .setType(new Coding(BALP_ENTITY_TYPE, "XrequestId", "X-Request-Id"));
        }

        if (record.patientId() != null) {
            event.addEntity()
                    .setWhat(new Reference("Patient/" + record.patientId()))
                    .setType(new Coding(ENTITY_TYPE, "1", "Person"))
                    .setRole(new Coding(OBJECT_ROLE, "1", "Patient"));
        }
        for (String entity : record.entities()) {
            event.addEntity()
                    .setWhat(new Reference(entity))
                    .setType(new Coding(ENTITY_TYPE, "2", "System Object"))
                    .setRole(new Coding(OBJECT_ROLE, "4", "Domain Resource"));
        }
        if (record.query() != null && !record.query().isEmpty()) {
            event.addEntity()
                    .setType(new Coding(ENTITY_TYPE, "2", "System Object"))
                    .setRole(new Coding(OBJECT_ROLE, "24", "Query"))
                    .setQuery(record.query().getBytes(StandardCharsets.UTF_8));
        }
        return event;
    }
}
