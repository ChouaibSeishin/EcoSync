#!/bin/bash
set -euo pipefail
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" \
 --set=migration_password="$SALES_MIGRATION_PASSWORD" --set=reader_password="$SALES_READER_PASSWORD" <<'SQL'
REVOKE ALL ON DATABASE sales FROM PUBLIC;
REVOKE ALL ON SCHEMA public FROM PUBLIC;
CREATE ROLE sales_migrator LOGIN PASSWORD :'migration_password' NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION;
CREATE ROLE sales_reader LOGIN PASSWORD :'reader_password' NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION;
GRANT CONNECT ON DATABASE sales TO sales_migrator, sales_reader;
CREATE SCHEMA sales AUTHORIZATION sales_migrator;
ALTER ROLE sales_migrator IN DATABASE sales SET search_path=sales;
ALTER ROLE sales_reader IN DATABASE sales SET search_path=sales;
ALTER ROLE sales_reader SET timezone='UTC';
SQL
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<'SQL'
SET ROLE sales_migrator;
SET search_path=sales;
CREATE TABLE schema_history(version VARCHAR(4) PRIMARY KEY, sha256 CHAR(64) NOT NULL);
SQL
for migration in /migrations/V*.sql; do
 version="$(basename "$migration" | cut -c1-4)"
 checksum="$(sha256sum "$migration" | cut -d' ' -f1)"
 { printf 'BEGIN;\nSET ROLE sales_migrator;\nSET search_path=sales;\n'; cat "$migration"; printf "\nINSERT INTO schema_history VALUES ('%s','%s');\nCOMMIT;\n" "$version" "$checksum"; } | psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB"
done
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<'SQL'
GRANT USAGE ON SCHEMA sales TO sales_reader;
GRANT SELECT ON sales.products,sales.customers,sales.orders,sales.order_lines TO sales_reader;
SQL
