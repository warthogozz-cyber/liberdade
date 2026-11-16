-- Passkeys, stored by Spring Security's JDBC repositories: these two tables match its user-entities-schema.sql and
-- user-credentials-schema-postgres.sql. user_entities.name holds users.id (D18), the name Spring signs a passkey in as.
CREATE TABLE user_entities (
    id           varchar(1000) NOT NULL,
    name         varchar(100)  NOT NULL,
    display_name varchar(200),
    PRIMARY KEY (id)
);

CREATE TABLE user_credentials (
    credential_id                varchar(1000) NOT NULL,
    user_entity_user_id          varchar(1000) NOT NULL,
    public_key                   bytea         NOT NULL,
    signature_count              bigint,
    uv_initialized               boolean,
    backup_eligible              boolean       NOT NULL,
    authenticator_transports     varchar(1000),
    public_key_credential_type   varchar(100),
    backup_state                 boolean       NOT NULL,
    attestation_object           bytea,
    attestation_client_data_json bytea,
    created                      timestamp,
    last_used                    timestamp,
    label                        varchar(1000) NOT NULL,
    PRIMARY KEY (credential_id)
);

-- Beyond Spring's schema: one entity per user, and a user's passkeys go with their entity.
CREATE UNIQUE INDEX user_entities_name_ix ON user_entities (name);

ALTER TABLE user_credentials
    ADD CONSTRAINT user_credentials_user_entity_fk FOREIGN KEY (user_entity_user_id)
        REFERENCES user_entities (id) ON DELETE CASCADE;

CREATE INDEX user_credentials_user_entity_ix ON user_credentials (user_entity_user_id);
