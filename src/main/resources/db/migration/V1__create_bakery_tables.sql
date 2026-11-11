-- 1. Customers Table
CREATE TABLE customers (
    phone_number VARCHAR(20) PRIMARY KEY,
    name VARCHAR(100),
    delivery_address TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 2. Products Table
CREATE TABLE products (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(150) NOT NULL,
    category VARCHAR(50) NOT NULL,
    description TEXT,
    image_url TEXT,
    is_available BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 3. Product Variants Table
CREATE TABLE product_variants (
    id BIGSERIAL PRIMARY KEY,
    product_id BIGINT NOT NULL,
    variant_name VARCHAR(50) NOT NULL,
    price NUMERIC(10, 2) NOT NULL,
    stock_quantity INT NOT NULL DEFAULT 0,
    is_active BOOLEAN DEFAULT TRUE,
    CONSTRAINT fk_variant_product FOREIGN KEY (product_id) 
        REFERENCES products(id) ON DELETE CASCADE
);

-- 4. Orders Table
CREATE TABLE orders (
    id BIGSERIAL PRIMARY KEY,
    order_number VARCHAR(50) UNIQUE NOT NULL,
    customer_phone VARCHAR(20) NOT NULL,
    total_amount NUMERIC(10, 2) NOT NULL,
    order_status VARCHAR(30) NOT NULL DEFAULT 'PLACED',
    payment_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    delivery_address TEXT NOT NULL,
    custom_notes TEXT,
    payment_link_id VARCHAR(100),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_order_customer FOREIGN KEY (customer_phone) 
        REFERENCES customers(phone_number)
);

-- 5. Order Items Table
CREATE TABLE order_items (
    id BIGSERIAL PRIMARY KEY,
    order_id BIGINT NOT NULL,
    variant_id BIGINT NOT NULL,
    quantity INT NOT NULL DEFAULT 1,
    price_at_purchase NUMERIC(10, 2) NOT NULL,
    CONSTRAINT fk_item_order FOREIGN KEY (order_id) 
        REFERENCES orders(id) ON DELETE CASCADE,
    CONSTRAINT fk_item_variant FOREIGN KEY (variant_id) 
        REFERENCES product_variants(id)
);

-- Indexes
CREATE INDEX idx_products_category ON products(category);
CREATE INDEX idx_variants_product_id ON product_variants(product_id);
CREATE INDEX idx_orders_customer_phone ON orders(customer_phone);
CREATE INDEX idx_orders_order_number ON orders(order_number);
CREATE INDEX idx_orders_status ON orders(order_status);
CREATE INDEX idx_order_items_order_id ON order_items(order_id);