-- Append-only audit trail. One row per (request, patient touched); content is the FHIR
-- AuditEvent. Indexed columns answer "who accessed this patient, when".
CREATE TABLE audit_event (
    id            VARCHAR(64)  PRIMARY KEY,
    recorded      TIMESTAMPTZ  NOT NULL,
    action        CHAR(1)      NOT NULL,
    outcome       VARCHAR(2)   NOT NULL,
    interaction   VARCHAR(32)  NOT NULL,
    agent_subject VARCHAR(255),
    agent_client  VARCHAR(255),
    patient_id    VARCHAR(64),
    request_id    VARCHAR(64),
    content       JSONB        NOT NULL
);

CREATE INDEX idx_audit_event_patient ON audit_event (patient_id, recorded);
CREATE INDEX idx_audit_event_recorded ON audit_event (recorded);
CREATE INDEX idx_audit_event_agent ON audit_event (agent_subject, recorded);

-- Audit records must not change once written, even through a bug in this application.
CREATE FUNCTION audit_event_immutable() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'audit_event is append-only';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER audit_event_no_update_or_delete
    BEFORE UPDATE OR DELETE ON audit_event
    FOR EACH ROW EXECUTE FUNCTION audit_event_immutable();
