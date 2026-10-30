CREATE TABLE items (
 sku VARCHAR(24) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
 product_label VARCHAR(120) NOT NULL, category VARCHAR(40) NOT NULL,
 unit_code VARCHAR(8) NOT NULL CHECK (unit_code IN ('EA','BOX','M')),
 supplier_catalog_ref VARCHAR(24) CHARACTER SET ascii COLLATE ascii_bin NULL,
 active BOOLEAN NOT NULL CHECK(active IN (0,1)),
 INDEX items_catalog(supplier_catalog_ref)
) ENGINE=InnoDB;
CREATE TABLE warehouses (
 warehouse_id VARCHAR(8) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
 warehouse_name VARCHAR(80) NOT NULL, region VARCHAR(16) NOT NULL,
 warehouse_status VARCHAR(16) NOT NULL CHECK(warehouse_status IN ('OPEN','MAINTENANCE'))
) ENGINE=InnoDB;
CREATE TABLE stock_levels (
 stock_id INTEGER PRIMARY KEY,
 sku VARCHAR(24) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 warehouse_id VARCHAR(8) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 quantity_on_hand INTEGER NOT NULL CHECK(quantity_on_hand>=0),
 quantity_reserved INTEGER NOT NULL CHECK(quantity_reserved>=0),
 reorder_threshold INTEGER NOT NULL CHECK(reorder_threshold>=0),
 last_updated_at DATETIME NOT NULL,
 CONSTRAINT stock_reserved CHECK(quantity_reserved<=quantity_on_hand),
 CONSTRAINT stock_item FOREIGN KEY(sku) REFERENCES items(sku),
 CONSTRAINT stock_warehouse FOREIGN KEY(warehouse_id) REFERENCES warehouses(warehouse_id),
 UNIQUE KEY stock_item_location(sku,warehouse_id),
 INDEX stock_location(warehouse_id)
) ENGINE=InnoDB;
