-- Search values extracted from the current version of each resource, one row per value.
-- Only the columns for the row's param_type are filled. Dates are half-open ranges
-- [low, high); NULL means unbounded on that side (e.g. a Period with no end).
CREATE TABLE search_index (
    resource_type      VARCHAR(64) NOT NULL,
    resource_id        VARCHAR(64) NOT NULL,
    param_name         VARCHAR(64) NOT NULL,
    param_type         VARCHAR(16) NOT NULL,
    value_system       TEXT,
    value_code         TEXT,
    value_string       TEXT,
    value_string_exact TEXT,
    value_date_low     TIMESTAMPTZ,
    value_date_high    TIMESTAMPTZ,
    value_reference    TEXT,
    CONSTRAINT search_index_param_type CHECK (param_type IN ('token', 'string', 'date', 'reference'))
);

-- Rows are replaced wholesale per resource on every write.
CREATE INDEX idx_search_index_resource ON search_index (resource_type, resource_id);

CREATE INDEX idx_search_index_token ON search_index (resource_type, param_name, value_code, value_system)
    WHERE param_type = 'token';
-- text_pattern_ops lets "starts with" (LIKE 'abc%') use the index regardless of collation.
CREATE INDEX idx_search_index_string ON search_index (resource_type, param_name, value_string text_pattern_ops)
    WHERE param_type = 'string';
CREATE INDEX idx_search_index_date ON search_index (resource_type, param_name, value_date_low, value_date_high)
    WHERE param_type = 'date';
CREATE INDEX idx_search_index_reference ON search_index (resource_type, param_name, value_reference)
    WHERE param_type = 'reference';
