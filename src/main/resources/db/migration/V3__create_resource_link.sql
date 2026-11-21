-- Every literal reference held by the current version of a resource. Lets a delete find who
-- still points at the target (referential integrity) without scanning JSON.
CREATE TABLE resource_link (
    source_type VARCHAR(64) NOT NULL,
    source_id   VARCHAR(64) NOT NULL,
    target_type VARCHAR(64) NOT NULL,
    target_id   VARCHAR(64) NOT NULL,
    PRIMARY KEY (source_type, source_id, target_type, target_id)
);

CREATE INDEX idx_resource_link_target ON resource_link (target_type, target_id);
