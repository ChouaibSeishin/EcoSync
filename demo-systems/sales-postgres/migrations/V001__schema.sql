CREATE TABLE products (
 product_id INTEGER PRIMARY KEY, product_name VARCHAR(120) NOT NULL,
 category VARCHAR(40) NOT NULL, description TEXT, active BOOLEAN NOT NULL,
 selling_unit VARCHAR(8) NOT NULL CHECK (selling_unit IN ('EA','BOX','M')),
 created_at TIMESTAMPTZ NOT NULL
);
CREATE TABLE customers (
 customer_id INTEGER PRIMARY KEY,
 customer_segment VARCHAR(24) NOT NULL CHECK (customer_segment IN ('SMB','EDUCATION','ENTERPRISE')),
 region VARCHAR(16) NOT NULL, created_at TIMESTAMPTZ NOT NULL
);
CREATE TABLE orders (
 order_id INTEGER PRIMARY KEY, customer_id INTEGER NOT NULL REFERENCES customers,
 ordered_at TIMESTAMPTZ NOT NULL,
 order_status VARCHAR(16) NOT NULL CHECK (order_status IN ('FULFILLED','PENDING','CANCELLED')),
 sales_channel VARCHAR(16) NOT NULL CHECK (sales_channel IN ('WEB','ACCOUNT','COUNTER')),
 region VARCHAR(16) NOT NULL
);
CREATE INDEX orders_time_status ON orders(ordered_at,order_status);
CREATE INDEX orders_customer ON orders(customer_id);
CREATE TABLE order_lines (
 line_id INTEGER PRIMARY KEY, order_id INTEGER NOT NULL REFERENCES orders,
 product_id INTEGER NOT NULL REFERENCES products,
 quantity INTEGER NOT NULL CHECK(quantity>0),
 unit_price NUMERIC(12,2) NOT NULL CHECK(unit_price>=0),
 discount_amount NUMERIC(14,2) NOT NULL CHECK(discount_amount>=0 AND discount_amount<=quantity*unit_price),
 currency_code CHAR(3) NOT NULL CHECK(currency_code IN ('USD','EUR','GBP'))
);
CREATE INDEX lines_order ON order_lines(order_id);
CREATE INDEX lines_product_currency ON order_lines(product_id,currency_code);
