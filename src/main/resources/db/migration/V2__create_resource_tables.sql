-- Current version of every resource. One row per logical resource; a delete keeps the
-- row (deleted = true, content NULL) so the version counter survives and reads can return 410.
CREATE TABLE resource (
    resource_type VARCHAR(64)  NOT NULL,
    id            VARCHAR(64)  NOT NULL,
    version_id    BIGINT       NOT NULL,
    last_updated  TIMESTAMPTZ  NOT NULL,
    deleted       BOOLEAN      NOT NULL DEFAULT FALSE,
    content       JSONB,
    PRIMARY KEY (resource_type, id),
    CONSTRAINT resource_content_present CHECK (deleted OR content IS NOT NULL)
);

CREATE INDEX idx_resource_type_updated ON resource (resource_type, last_updated);

-- Append-only log of every version, including deletes. Serves vread and _history.
CREATE TABLE resource_history (
    resource_type VARCHAR(64)  NOT NULL,
    id            VARCHAR(64)  NOT NULL,
    version_id    BIGINT       NOT NULL,
    last_updated  TIMESTAMPTZ  NOT NULL,
    deleted       BOOLEAN      NOT NULL DEFAULT FALSE,
    content       JSONB,
    PRIMARY KEY (resource_type, id, version_id),
    CONSTRAINT history_content_present CHECK (deleted OR content IS NOT NULL)
);

CREATE INDEX idx_history_type_updated ON resource_history (resource_type, last_updated);
CREATE INDEX idx_history_updated ON resource_history (last_updated);
