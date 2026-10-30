#!/bin/bash
set -euo pipefail
for value in "$INVENTORY_MIGRATION_PASSWORD" "$INVENTORY_READER_PASSWORD"; do
 [[ "$value" =~ ^[A-Za-z0-9_-]{32,}$ ]] || { echo 'Invalid local credential format' >&2; exit 1; }
done
MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql --protocol=socket -uroot inventory <<SQL
CREATE USER 'inventory_migrator'@'%' IDENTIFIED BY '$INVENTORY_MIGRATION_PASSWORD';
CREATE USER 'inventory_reader'@'%' IDENTIFIED BY '$INVENTORY_READER_PASSWORD';
GRANT ALL PRIVILEGES ON inventory.* TO 'inventory_migrator'@'%';
CREATE TABLE inventory.schema_history(version VARCHAR(4) PRIMARY KEY,sha256 CHAR(64) NOT NULL) ENGINE=InnoDB;
SQL
for migration in /migrations/V*.sql; do
 version="$(basename "$migration" | cut -c1-4)"
 checksum="$(sha256sum "$migration" | cut -d' ' -f1)"
 { cat "$migration"; printf "\nINSERT INTO schema_history VALUES ('%s','%s');\n" "$version" "$checksum"; } | MYSQL_PWD="$INVENTORY_MIGRATION_PASSWORD" mysql --protocol=socket -u inventory_migrator inventory
done
MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql --protocol=socket -uroot inventory <<'SQL'
GRANT SELECT ON inventory.items TO 'inventory_reader'@'%';
GRANT SELECT ON inventory.warehouses TO 'inventory_reader'@'%';
GRANT SELECT ON inventory.stock_levels TO 'inventory_reader'@'%';
SQL
