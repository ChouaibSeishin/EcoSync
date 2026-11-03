import {readFile} from 'node:fs/promises';
import {spawnSync} from 'node:child_process';
const values=Object.fromEntries((await readFile('infra/docker/.env.database','utf8')).split(/\r?\n/).filter(l=>l&&!l.startsWith('#')).map(l=>{const i=l.indexOf('=');return[l.slice(0,i),l.slice(i+1)];}));
for(const name of ['GATEWAY_SALES_READER_PASSWORD','GATEWAY_INVENTORY_READER_PASSWORD'])if(!/^[A-Za-z0-9_-]{32,128}$/.test(values[name]??''))throw Error('Invalid retained reader credential.');
function execute(service,command,sql){const r=spawnSync('docker',['compose','--env-file','infra/docker/demo.env','-f','infra/docker/demo-systems.compose.yaml','exec','-T',service,...command],{input:sql,encoding:'utf8',windowsHide:true});if(r.status!==0)throw Error('Reader provisioning failed for '+service+'; inspect the source locally without printing credentials.');}
execute('sales',['psql','-U','sales_owner','-d','sales','-v','ON_ERROR_STOP=1'],`DO $$ BEGIN IF NOT EXISTS(SELECT 1 FROM pg_roles WHERE rolname='gateway_sales_reader') THEN CREATE ROLE gateway_sales_reader LOGIN PASSWORD '${values.GATEWAY_SALES_READER_PASSWORD}' NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT; END IF; END $$;
GRANT CONNECT ON DATABASE sales TO gateway_sales_reader;
GRANT USAGE ON SCHEMA sales TO gateway_sales_reader;
GRANT SELECT ON sales.products,sales.customers,sales.orders,sales.order_lines TO gateway_sales_reader;
ALTER ROLE gateway_sales_reader SET default_transaction_read_only=on;
ALTER ROLE gateway_sales_reader SET statement_timeout='4s';
ALTER ROLE gateway_sales_reader SET lock_timeout='1s';
`);
execute('inventory',['sh','-c','MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -u root --batch'],`CREATE USER IF NOT EXISTS 'gateway_inventory_reader'@'%' IDENTIFIED BY '${values.GATEWAY_INVENTORY_READER_PASSWORD}';
GRANT SELECT ON inventory.items TO 'gateway_inventory_reader'@'%';
GRANT SELECT ON inventory.warehouses TO 'gateway_inventory_reader'@'%';
GRANT SELECT ON inventory.stock_levels TO 'gateway_inventory_reader'@'%';
`);
console.log('Separate reader accounts provisioned idempotently. Existing passwords, native readers, business schemas and data retained.');
