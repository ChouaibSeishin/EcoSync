# Demo data dictionary and semantics
Schema versions: sales 1, inventory 1, supplier snapshot 1; fixture version v1.0.0. These are independently owned contracts.

## Sales PostgreSQL
- products: product_id INTEGER PK; product_name VARCHAR(120); category VARCHAR(40); description TEXT nullable; active BOOLEAN; created_at TIMESTAMPTZ; selling_unit VARCHAR(8) explicitly names the sales quantity unit.
- customers: customer_id INTEGER PK; customer_segment VARCHAR(24); region VARCHAR(16); created_at TIMESTAMPTZ. No names, addresses or contacts.
- orders: order_id INTEGER PK; customer_id FK; ordered_at TIMESTAMPTZ; order_status constrained to FULFILLED/PENDING/CANCELLED; sales_channel and region describe the historical order.
- order_lines: line_id INTEGER PK; order_id/product_id FKs; quantity positive INTEGER; unit_price NUMERIC(12,2); discount_amount NUMERIC(14,2); currency_code CHAR(3). Price and line-total discount are nonnegative; discount cannot exceed quantity times price.

Realized revenue includes **FULFILLED only**: quantity * unit_price - discount_amount. Discount is per line, not per unit. Group all aggregates by currency; never convert currencies. Quantity uses that product's selling_unit, not an assumed inventory unit. UTC interval filters are [startInclusive,endExclusive). Index order time/status and line relationships. Product descriptions deliberately include nulls; similar names do not identify products.

## Inventory MySQL
- items: sku VARCHAR(24) case-sensitive PK; product_label VARCHAR(120); category VARCHAR(40); unit_code VARCHAR(8); supplier_catalog_ref VARCHAR(24) nullable; active BOOLEAN with explicit 0/1 constraint.
- warehouses: warehouse_id VARCHAR(8) PK; warehouse_name VARCHAR(80); region VARCHAR(16); warehouse_status OPEN/MAINTENANCE.
- stock_levels: stock_id INTEGER PK; sku/warehouse FKs; unique sku/warehouse; quantity_on_hand, quantity_reserved, reorder_threshold nonnegative INTEGER; reserved <= on_hand; last_updated_at DATETIME(0) interpreted explicitly as UTC.

available_quantity = on_hand - reserved. A warehouse stock row is low when available <= its threshold. Aggregate only compatible unit_code values; aggregate low-stock scope and warehouse status explicitly. An SKU can appear at several warehouses. Threshold is per warehouse; aggregate-threshold comparisons must say when thresholds are summed. Labels, SQL collation and SKU identity differ from sales. EA, BOX and M are separate units without approved conversions.

## Supplier JSON / REST
- suppliers: supplier_id, unique supplier_code, supplier_name, region, active. Optional rating is omitted rather than inventing a rating scale.
- offers: offer_id; catalog_reference; supplier_id FK inside snapshot; supplier_sku; unit_cost as a two-place decimal string; currency_code; unit_code; minimum_order_quantity >=1; available_quantity >=0; lead_time_days >=0; valid_from/valid_until UTC RFC3339 timestamps.

Offers are valid at t iff valid_from <= t < valid_until. Lead time is calendar days from an accepted order, not a guaranteed delivery timestamp. An eligible offer additionally needs an active supplier, adequate available quantity and satisfied minimum order; currency/unit compatibility must be checked independently. No cheapest-is-best heuristic or exchange-rate conversion is used. Multiple offers share a catalog reference. Inactive/expired offers remain in the data and default API results; filtering is explicit.

Control/history tables are not granted to source readers. Readers receive SELECT only on the intended business tables; no administrative/schema/write grants.
