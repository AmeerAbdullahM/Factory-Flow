-- ============================================================
-- FactoryFlow Benchmark Opening Stock
-- Migration: V3 - Opening Stock for Benchmark
-- ============================================================
-- This sets up the initial inventory as described in Section 21:
--   Flour: 100 kg @ ₹42/kg
--   Oil:   30 L  @ ₹140/L
--   Spice: 5 kg  @ ₹200/kg
--   Pouches: 500 pcs @ ₹2.50/pcs
--   Cartons: 30 pcs  @ ₹15/pcs
--   FG Murukku 200g: 50 packets @ ₹30/packet (assumed opening cost)

-- Initialize inventory_balances
INSERT INTO inventory_balances (item_type, raw_material_id, packaging_material_id, product_id,
                                current_quantity, weighted_avg_cost, total_value) VALUES
    ('RAW_MATERIAL', '55555555-0000-0000-0000-000000000001', NULL, NULL,
     100.0000, 42.000000, 4200.00),  -- 100 kg Flour @ ₹42
    ('RAW_MATERIAL', '55555555-0000-0000-0000-000000000002', NULL, NULL,
     30.0000, 140.000000, 4200.00),  -- 30 L Oil @ ₹140
    ('RAW_MATERIAL', '55555555-0000-0000-0000-000000000003', NULL, NULL,
     5.0000, 200.000000, 1000.00),   -- 5 kg Spice @ ₹200
    ('PACKAGING', NULL, '66666666-0000-0000-0000-000000000001', NULL,
     500.0000, 2.500000, 1250.00),   -- 500 pcs Pouch @ ₹2.50
    ('PACKAGING', NULL, '66666666-0000-0000-0000-000000000002', NULL,
     30.0000, 15.000000, 450.00),    -- 30 pcs Carton @ ₹15
    ('FINISHED_GOODS', NULL, NULL, '77777777-0000-0000-0000-000000000001',
     50.0000, 30.000000, 1500.00);   -- 50 pkts Murukku @ ₹30 assumed

-- Opening stock movements (audit trail)
INSERT INTO stock_movements (item_type, raw_material_id, packaging_material_id, product_id,
                             movement_type, qty_in, running_balance,
                             unit_cost, total_value,
                             source_document_type, reference_number, notes, created_by)
VALUES
    ('RAW_MATERIAL', '55555555-0000-0000-0000-000000000001', NULL, NULL,
     'OPENING_STOCK', 100.0000, 100.0000, 42.000000, 4200.00,
     'SYSTEM', 'OPENING', 'Opening stock - Benchmark setup', '22222222-0000-0000-0000-000000000006'),
    ('RAW_MATERIAL', '55555555-0000-0000-0000-000000000002', NULL, NULL,
     'OPENING_STOCK', 30.0000, 30.0000, 140.000000, 4200.00,
     'SYSTEM', 'OPENING', 'Opening stock - Benchmark setup', '22222222-0000-0000-0000-000000000006'),
    ('RAW_MATERIAL', '55555555-0000-0000-0000-000000000003', NULL, NULL,
     'OPENING_STOCK', 5.0000, 5.0000, 200.000000, 1000.00,
     'SYSTEM', 'OPENING', 'Opening stock - Benchmark setup', '22222222-0000-0000-0000-000000000006'),
    ('PACKAGING', NULL, '66666666-0000-0000-0000-000000000001', NULL,
     'OPENING_STOCK', 500.0000, 500.0000, 2.500000, 1250.00,
     'SYSTEM', 'OPENING', 'Opening stock - Benchmark setup', '22222222-0000-0000-0000-000000000006'),
    ('PACKAGING', NULL, '66666666-0000-0000-0000-000000000002', NULL,
     'OPENING_STOCK', 30.0000, 30.0000, 15.000000, 450.00,
     'SYSTEM', 'OPENING', 'Opening stock - Benchmark setup', '22222222-0000-0000-0000-000000000006'),
    ('FINISHED_GOODS', NULL, NULL, '77777777-0000-0000-0000-000000000001',
     'OPENING_STOCK', 50.0000, 50.0000, 30.000000, 1500.00,
     'SYSTEM', 'OPENING', 'Opening stock FG Murukku 200g @ ₹30/pkt (assumed)', '22222222-0000-0000-0000-000000000006');
