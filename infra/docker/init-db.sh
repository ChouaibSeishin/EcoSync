#!/bin/sh
set -eu
psql --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" --set=ON_ERROR_STOP=1 \
  --set=migration_password="$DB_MIGRATION_PASSWORD" --set=runtime_password="$DB_RUNTIME_PASSWORD" <<'SQL'
CREATE ROLE ecosync_migrator LOGIN PASSWORD :'migration_password' NOSUPERUSER NOCREATEDB NOCREATEROLE;
CREATE ROLE ecosync_runtime LOGIN PASSWORD :'runtime_password' NOSUPERUSER NOCREATEDB NOCREATEROLE;
ALTER DATABASE ecosync OWNER TO ecosync_migrator;
REVOKE CREATE ON SCHEMA public FROM PUBLIC;
CREATE SCHEMA app AUTHORIZATION ecosync_migrator;
GRANT USAGE ON SCHEMA app TO ecosync_runtime;
ALTER DEFAULT PRIVILEGES FOR ROLE ecosync_migrator IN SCHEMA app
  GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO ecosync_runtime;
SQL
