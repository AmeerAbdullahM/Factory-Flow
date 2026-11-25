-- ============================================================
-- FactoryFlow Seed Data
-- Migration: V2 - Seed Data
-- ============================================================

-- ============================================================
-- ROLES
-- ============================================================
INSERT INTO roles (id, name, description) VALUES
    ('11111111-0000-0000-0000-000000000001', 'SALESMAN', 'Field salesman - mobile order entry'),
    ('11111111-0000-0000-0000-000000000002', 'ADMIN', 'Admin / billing desk - full order and stock management'),
    ('11111111-0000-0000-0000-000000000003', 'PRODUCTION_SUPERVISOR', 'Production floor - manage runs, stock, BOM'),
    ('11111111-0000-0000-0000-000000000004', 'ACCOUNTANT', 'Accountant / Auditor - read-only reports'),
    ('11111111-0000-0000-0000-000000000005', 'OWNER', 'Owner - full visibility and dashboard');

-- ============================================================
-- USERS (passwords are BCrypt hashes of 'password123')
-- ============================================================
INSERT INTO users (id, username, email, password_hash, full_name, phone, is_active) VALUES
    ('22222222-0000-0000-0000-000000000001', 'salesman_a', 'salesman_a@factoryflow.com',
     '$2a$12$LQv3c1yqBWVHxkd0LHAkCOYz6TsCYt3JvYqaB2FJwnIBBpmBd1piy', 'Rajan Kumar', '9876543201', TRUE),
    ('22222222-0000-0000-0000-000000000002', 'salesman_b', 'salesman_b@factoryflow.com',
     '$2a$12$LQv3c1yqBWVHxkd0LHAkCOYz6TsCYt3JvYqaB2FJwnIBBpmBd1piy', 'Priya Devi', '9876543202', TRUE),
    ('22222222-0000-0000-0000-000000000003', 'admin', 'admin@factoryflow.com',
     '$2a$12$LQv3c1yqBWVHxkd0LHAkCOYz6TsCYt3JvYqaB2FJwnIBBpmBd1piy', 'Admin User', '9876543203', TRUE),
    ('22222222-0000-0000-0000-000000000004', 'supervisor', 'supervisor@factoryflow.com',
     '$2a$12$LQv3c1yqBWVHxkd0LHAkCOYz6TsCYt3JvYqaB2FJwnIBBpmBd1piy', 'Murugan Supervisor', '9876543204', TRUE),
    ('22222222-0000-0000-0000-000000000005', 'auditor', 'auditor@factoryflow.com',
     '$2a$12$LQv3c1yqBWVHxkd0LHAkCOYz6TsCYt3JvYqaB2FJwnIBBpmBd1piy', 'CA Ramesh', '9876543205', TRUE),
    ('22222222-0000-0000-0000-000000000006', 'owner', 'owner@factoryflow.com',
     '$2a$12$LQv3c1yqBWVHxkd0LHAkCOYz6TsCYt3JvYqaB2FJwnIBBpmBd1piy', 'Krishnamurthy Owner', '9876543206', TRUE);

INSERT INTO user_roles (user_id, role_id) VALUES
    ('22222222-0000-0000-0000-000000000001', '11111111-0000-0000-0000-000000000001'),
    ('22222222-0000-0000-0000-000000000002', '11111111-0000-0000-0000-000000000001'),
    ('22222222-0000-0000-0000-000000000003', '11111111-0000-0000-0000-000000000002'),
    ('22222222-0000-0000-0000-000000000004', '11111111-0000-0000-0000-000000000003'),
    ('22222222-0000-0000-0000-000000000005', '11111111-0000-0000-0000-000000000004'),
    ('22222222-0000-0000-0000-000000000006', '11111111-0000-0000-0000-000000000005');

-- ============================================================
-- UNITS
-- ============================================================
INSERT INTO units (id, name, symbol, unit_type, base_unit, to_base_factor) VALUES
    -- Weight: base unit = gram (g)
    ('33333333-0000-0000-0000-000000000001', 'Kilogram', 'kg', 'WEIGHT', 'g', 1000),
    ('33333333-0000-0000-0000-000000000002', 'Gram', 'g', 'WEIGHT', 'g', 1),
    -- Volume: base unit = millilitre (ml)
    ('33333333-0000-0000-0000-000000000003', 'Litre', 'L', 'VOLUME', 'ml', 1000),
    ('33333333-0000-0000-0000-000000000004', 'Millilitre', 'ml', 'VOLUME', 'ml', 1),
    -- Count: base unit = piece
    ('33333333-0000-0000-0000-000000000005', 'Piece', 'pcs', 'COUNT', 'pcs', 1),
    ('33333333-0000-0000-0000-000000000006', 'Packet', 'pkt', 'COUNT', 'pcs', 1),
    ('33333333-0000-0000-0000-000000000007', 'Carton', 'ctn', 'COUNT', 'pcs', 20);

-- ============================================================
-- SUPPLIERS
-- ============================================================
INSERT INTO suppliers (id, name, contact_person, phone, state, state_code, gstin, is_active) VALUES
    ('44444444-0000-0000-0000-000000000001', 'Sri Lakshmi Flour Mills', 'Suresh', '9811122233', 'Tamil Nadu', '33', '33AABCF5678B1Z2', TRUE),
    ('44444444-0000-0000-0000-000000000002', 'Murugan Oil Traders', 'Velu', '9811122234', 'Tamil Nadu', '33', '33AABCO1234C1Z3', TRUE),
    ('44444444-0000-0000-0000-000000000003', 'Spice World', 'Meena', '9811122235', 'Tamil Nadu', '33', '33AABCS5678D1Z4', TRUE),
    ('44444444-0000-0000-0000-000000000004', 'Pack N Print', 'David', '9811122236', 'Tamil Nadu', '33', '33AABCP1234E1Z5', TRUE);

-- ============================================================
-- RAW MATERIALS
-- ============================================================
INSERT INTO raw_materials (id, name, code, unit_id, reorder_level) VALUES
    ('55555555-0000-0000-0000-000000000001', 'Rice Flour', 'RM-FLOUR', '33333333-0000-0000-0000-000000000001', 50),
    ('55555555-0000-0000-0000-000000000002', 'Cooking Oil', 'RM-OIL', '33333333-0000-0000-0000-000000000003', 20),
    ('55555555-0000-0000-0000-000000000003', 'Salt and Spice Mix', 'RM-SPICE', '33333333-0000-0000-0000-000000000001', 5);

-- ============================================================
-- PACKAGING MATERIALS
-- ============================================================
INSERT INTO packaging_materials (id, name, code, unit_id, reorder_level) VALUES
    ('66666666-0000-0000-0000-000000000001', '200g Pouch', 'PKG-POUCH-200', '33333333-0000-0000-0000-000000000005', 200),
    ('66666666-0000-0000-0000-000000000002', 'Carton (20 packets)', 'PKG-CARTON', '33333333-0000-0000-0000-000000000005', 20);

-- ============================================================
-- PRODUCTS
-- ============================================================
INSERT INTO products (id, name, code, category, pack_size_grams, pack_size_description, selling_price, gst_rate, hsn_code, unit_id) VALUES
    ('77777777-0000-0000-0000-000000000001', 'Murukku 200g', 'PRD-MRK-200', 'Murukku', 200, '200g', 50.00, 5.00, '21069099', '33333333-0000-0000-0000-000000000006'),
    ('77777777-0000-0000-0000-000000000002', 'Mixture 200g', 'PRD-MIX-200', 'Mixture', 200, '200g', 55.00, 5.00, '21069099', '33333333-0000-0000-0000-000000000006'),
    ('77777777-0000-0000-0000-000000000003', 'Chips 100g', 'PRD-CHP-100', 'Chips', 100, '100g', 30.00, 12.00, '20052000', '33333333-0000-0000-0000-000000000006'),
    ('77777777-0000-0000-0000-000000000004', 'Mysore Pak 250g', 'PRD-SWT-250', 'Sweets', 250, '250g', 120.00, 5.00, '17049099', '33333333-0000-0000-0000-000000000006');

-- ============================================================
-- CUSTOMERS (Benchmark: Retailer A and Distributor B)
-- ============================================================
INSERT INTO customers (id, name, type, gstin, billing_address, state, state_code, phone, credit_limit, payment_terms_days, assigned_salesman_id) VALUES
    ('88888888-0000-0000-0000-000000000001', 'Retailer A - Chennai Stores', 'RETAILER',
     '33AABCR1111A1Z9', '12 Anna Salai, Chennai, Tamil Nadu', 'Tamil Nadu', '33',
     '9900001111', 50000.00, 30, '22222222-0000-0000-0000-000000000001'),
    ('88888888-0000-0000-0000-000000000002', 'Distributor B - Bangalore Trade', 'DISTRIBUTOR',
     '29AABCD2222B1Z8', '45 MG Road, Bengaluru, Karnataka', 'Karnataka', '29',
     '9900002222', 100000.00, 45, '22222222-0000-0000-0000-000000000002');

-- ============================================================
-- BOM: Murukku 200g (1 batch = 200 packets)
-- ============================================================
INSERT INTO bom_headers (id, code, product_id, batch_size, expected_output, version, effective_date, is_active, notes, created_by) VALUES
    ('99999999-0000-0000-0000-000000000001', 'BOM-MRK-200-V1',
     '77777777-0000-0000-0000-000000000001', 1, 200, 1, '2026-01-01', TRUE,
     'Standard BOM for Murukku 200g. 1 batch = 200 packets.',
     '22222222-0000-0000-0000-000000000006');

INSERT INTO bom_items (id, bom_header_id, item_type, raw_material_id, packaging_material_id, quantity, unit_id) VALUES
    -- Raw materials
    (uuid_generate_v4(), '99999999-0000-0000-0000-000000000001', 'RAW_MATERIAL',
     '55555555-0000-0000-0000-000000000001', NULL, 40.0000, '33333333-0000-0000-0000-000000000001'),  -- 40 kg Flour
    (uuid_generate_v4(), '99999999-0000-0000-0000-000000000001', 'RAW_MATERIAL',
     '55555555-0000-0000-0000-000000000002', NULL, 10.0000, '33333333-0000-0000-0000-000000000003'),  -- 10 L Oil
    (uuid_generate_v4(), '99999999-0000-0000-0000-000000000001', 'RAW_MATERIAL',
     '55555555-0000-0000-0000-000000000003', NULL, 1.2000, '33333333-0000-0000-0000-000000000001'),   -- 1.2 kg Spice
    -- Packaging
    (uuid_generate_v4(), '99999999-0000-0000-0000-000000000001', 'PACKAGING',
     NULL, '66666666-0000-0000-0000-000000000001', 200.0000, '33333333-0000-0000-0000-000000000005'), -- 200 pcs Pouch
    (uuid_generate_v4(), '99999999-0000-0000-0000-000000000001', 'PACKAGING',
     NULL, '66666666-0000-0000-0000-000000000002', 10.0000, '33333333-0000-0000-0000-000000000005');  -- 10 pcs Carton

-- ============================================================
-- INVOICE SEQUENCES (initialize for current financial year)
-- ============================================================
INSERT INTO invoice_sequences (financial_year, last_sequence) VALUES ('2026-27', 0);
