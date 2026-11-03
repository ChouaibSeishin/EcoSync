CREATE TABLE database_credential (
 organization_id uuid NOT NULL REFERENCES organization(id), id uuid NOT NULL,
 key_id varchar(64) NOT NULL, nonce bytea NOT NULL CHECK(octet_length(nonce)=12), ciphertext bytea NOT NULL,
 revoked boolean NOT NULL DEFAULT false, created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
 PRIMARY KEY(organization_id,id)
);
CREATE TABLE database_connector (
 organization_id uuid NOT NULL REFERENCES organization(id), id uuid NOT NULL, display_name varchar(100) NOT NULL,
 configuration_version bigint NOT NULL CHECK(configuration_version>0),
 state varchar(16) NOT NULL DEFAULT 'DRAFT' CHECK(state IN('DRAFT','VALIDATING','ACTIVE','DISABLED')),
 health varchar(16) NOT NULL DEFAULT 'UNKNOWN' CHECK(health IN('UNKNOWN','HEALTHY','UNREACHABLE')),
 removed boolean NOT NULL DEFAULT false, created_by uuid NOT NULL REFERENCES app_user(id),
 created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP, updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
 last_validated_at timestamptz, PRIMARY KEY(organization_id,id)
);
CREATE UNIQUE INDEX database_connector_name ON database_connector(organization_id,lower(display_name)) WHERE NOT removed;
CREATE TABLE database_configuration (
 organization_id uuid NOT NULL, connector_id uuid NOT NULL, version bigint NOT NULL,
 database_type varchar(16) NOT NULL CHECK(database_type IN('POSTGRESQL','MYSQL')),
 host varchar(253) NOT NULL, port integer NOT NULL CHECK(port BETWEEN 1 AND 65535), database_name varchar(63) NOT NULL,
 schemas jsonb NOT NULL, tls boolean NOT NULL, credential_id uuid NOT NULL, fingerprint char(64) NOT NULL,
 created_by uuid NOT NULL REFERENCES app_user(id), created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
 PRIMARY KEY(organization_id,connector_id,version),
 FOREIGN KEY(organization_id,connector_id) REFERENCES database_connector(organization_id,id),
 FOREIGN KEY(organization_id,credential_id) REFERENCES database_credential(organization_id,id)
);
CREATE TABLE database_table (
 organization_id uuid NOT NULL, connector_id uuid NOT NULL, version bigint NOT NULL, id uuid NOT NULL,
 schema_name varchar(128) NOT NULL, table_name varchar(128) NOT NULL, fingerprint char(64) NOT NULL, metadata jsonb NOT NULL,
 present boolean NOT NULL DEFAULT true, discovered_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
 PRIMARY KEY(organization_id,id), UNIQUE(organization_id,connector_id,version,schema_name,table_name,fingerprint),
 FOREIGN KEY(organization_id,connector_id,version) REFERENCES database_configuration(organization_id,connector_id,version)
);
CREATE TABLE database_approval (
 organization_id uuid NOT NULL, id uuid NOT NULL, table_id uuid NOT NULL,
 role varchar(32) NOT NULL CHECK(role IN('INTEGRATION_ADMIN','USER')), columns jsonb NOT NULL,
 fingerprint char(64) NOT NULL, status varchar(16) NOT NULL CHECK(status IN('APPROVED','REVOKED','STALE')),
 actor uuid NOT NULL REFERENCES app_user(id), created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
 PRIMARY KEY(organization_id,id), FOREIGN KEY(organization_id,table_id) REFERENCES database_table(organization_id,id)
);
CREATE UNIQUE INDEX database_current_approval ON database_approval(organization_id,table_id,role) WHERE status='APPROVED';
CREATE TABLE database_event (
 organization_id uuid NOT NULL, connector_id uuid NOT NULL, id uuid NOT NULL, actor uuid NOT NULL REFERENCES app_user(id),
 version bigint NOT NULL, table_id uuid, kind varchar(24) NOT NULL, status varchar(32) NOT NULL, error_code varchar(64),
 started_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP, ended_at timestamptz, duration_ms bigint,
 PRIMARY KEY(organization_id,id), FOREIGN KEY(organization_id,connector_id) REFERENCES database_connector(organization_id,id)
);
CREATE INDEX database_events_recent ON database_event(organization_id,connector_id,started_at DESC);
DO $$ BEGIN
 IF EXISTS(SELECT 1 FROM pg_roles WHERE rolname='ecosync_runtime') THEN
  GRANT SELECT,INSERT,UPDATE ON database_connector,database_credential,database_table,database_approval,database_event TO ecosync_runtime;
  GRANT SELECT,INSERT ON database_configuration TO ecosync_runtime;
 END IF;
END $$;
