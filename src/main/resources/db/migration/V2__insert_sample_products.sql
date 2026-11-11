-- 1. Insert Sample Products (Using is_available)
INSERT INTO products (id, name, category, description, image_url, is_available, created_at) VALUES 
(1, 'Black Forest Cake', 'CAKE', 'Classic chocolate sponge with fresh cream and cherries', 'https://placeholder.com/cake.jpg', true, CURRENT_TIMESTAMP),
(2, 'Butter Croissant', 'PASTRY', 'Flaky, buttery, authentic French pastry', 'https://placeholder.com/croissant.jpg', true, CURRENT_TIMESTAMP),
(3, 'Paneer Tikka Puff', 'SAVOURY', 'Crispy puff pastry stuffed with spicy paneer tikka', 'https://placeholder.com/puff.jpg', true, CURRENT_TIMESTAMP),
(4, 'Multigrain Bread', 'BREAD', 'Healthy loaf made with 7 different grains and seeds', 'https://placeholder.com/bread.jpg', true, CURRENT_TIMESTAMP);

-- 2. Insert Product Variants (Using is_active)
INSERT INTO product_variants (product_id, variant_name, price, stock_quantity, is_active) VALUES 
(1, '500g', 450.00, 20, true),
(1, '1 Kg', 850.00, 10, true),
(2, '1 Piece', 90.00, 50, true),
(3, '1 Piece', 45.00, 100, true),
(4, '400g Loaf', 65.00, 30, true);

-- 3. Reset the primary key sequence so future Java inserts don't cause ID conflicts
SELECT setval('products_id_seq', (SELECT MAX(id) FROM products));