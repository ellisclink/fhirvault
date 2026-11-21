-- Proves Flyway runs; real storage tables arrive in Milestone 1.
CREATE TABLE schema_info (
    key   VARCHAR(64)  PRIMARY KEY,
    value VARCHAR(255) NOT NULL
);

INSERT INTO schema_info (key, value) VALUES ('created_by', 'V1__create_schema_info');
