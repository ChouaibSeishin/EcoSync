CREATE TABLE organization (
    id uuid PRIMARY KEY,
    slug varchar(80) NOT NULL UNIQUE,
    display_name varchar(120) NOT NULL,
    enabled boolean NOT NULL DEFAULT true,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    lock_version bigint NOT NULL DEFAULT 0
);
CREATE TABLE app_user (
    id uuid PRIMARY KEY,
    login varchar(64) NOT NULL UNIQUE CHECK (login = lower(login)),
    display_name varchar(80) NOT NULL,
    password_hash varchar(255) NOT NULL,
    enabled boolean NOT NULL DEFAULT true,
    platform_admin boolean NOT NULL DEFAULT false,
    credential_version bigint NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE membership (
    organization_id uuid NOT NULL REFERENCES organization(id),
    id uuid NOT NULL,
    user_id uuid NOT NULL REFERENCES app_user(id),
    PRIMARY KEY (organization_id, id),
    UNIQUE (organization_id, user_id)
);
CREATE TABLE role_assignment (
    organization_id uuid NOT NULL,
    membership_id uuid NOT NULL,
    role varchar(32) NOT NULL CHECK (role IN ('INTEGRATION_ADMIN', 'USER', 'AUDITOR')),
    PRIMARY KEY (organization_id, membership_id, role),
    FOREIGN KEY (organization_id, membership_id) REFERENCES membership(organization_id, id)
);
CREATE INDEX membership_by_user ON membership(user_id, organization_id);
CREATE TABLE bootstrap_marker (
    id integer PRIMARY KEY CHECK (id = 1),
    completed_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP
);
