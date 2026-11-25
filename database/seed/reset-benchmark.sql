-- Reset transactional data and restore Section 21 benchmark opening stock
TRUNCATE TABLE
    audit_logs,
    payment_allocations,
    payments,
    dispatch_items,
    dispatches,
    credit_notes,
    invoice_items,
    invoices,
    sales_order_items,
    sales_orders,
    production_costs,
    production_material_issues,
    production_runs,
    purchase_items,
    purchases,
    stock_movements,
    inventory_balances
RESTART IDENTITY CASCADE;

UPDATE invoice_sequences SET last_sequence = 0 WHERE financial_year = '2026-27';

-- Restore opening balances
INSERT INTO inventory_balances (item_type, raw_material_id, packaging_material_id, product_id,
                                current_quantity, weighted_avg_cost, total_value) VALUES
    ('RAW_MATERIAL', '55555555-0000-0000-0000-000000000001', NULL, NULL, 100.0000, 42.000000, 4200.00),
    ('RAW_MATERIAL', '55555555-0000-0000-0000-000000000002', NULL, NULL, 30.0000, 140.000000, 4200.00),
    ('RAW_MATERIAL', '55555555-0000-0000-0000-000000000003', NULL, NULL, 5.0000, 200.000000, 1000.00),
    ('PACKAGING', NULL, '66666666-0000-0000-0000-000000000001', NULL, 500.0000, 2.500000, 1250.00),
    ('PACKAGING', NULL, '66666666-0000-0000-0000-000000000002', NULL, 30.0000, 15.000000, 450.00),
    ('FINISHED_GOODS', NULL, NULL, '77777777-0000-0000-0000-000000000001', 50.0000, 30.000000, 1500.00);

INSERT INTO stock_movements (item_type, raw_material_id, packaging_material_id, product_id,
                             movement_type, qty_in, running_balance, unit_cost, total_value,
                             source_document_type, reference_number, notes, created_by) VALUES
    ('RAW_MATERIAL', '55555555-0000-0000-0000-000000000001', NULL, NULL, 'OPENING_STOCK', 100.0000, 100.0000, 42.000000, 4200.00, 'SYSTEM', 'OPENING', 'Opening stock', '22222222-0000-0000-0000-000000000006'),
    ('RAW_MATERIAL', '55555555-0000-0000-0000-000000000002', NULL, NULL, 'OPENING_STOCK', 30.0000, 30.0000, 140.000000, 4200.00, 'SYSTEM', 'OPENING', 'Opening stock', '22222222-0000-0000-0000-000000000006'),
    ('RAW_MATERIAL', '55555555-0000-0000-0000-000000000003', NULL, NULL, 'OPENING_STOCK', 5.0000, 5.0000, 200.000000, 1000.00, 'SYSTEM', 'OPENING', 'Opening stock', '22222222-0000-0000-0000-000000000006'),
    ('PACKAGING', NULL, '66666666-0000-0000-0000-000000000001', NULL, 'OPENING_STOCK', 500.0000, 500.0000, 2.500000, 1250.00, 'SYSTEM', 'OPENING', 'Opening stock', '22222222-0000-0000-0000-000000000006'),
    ('PACKAGING', NULL, '66666666-0000-0000-0000-000000000002', NULL, 'OPENING_STOCK', 30.0000, 30.0000, 15.000000, 450.00, 'SYSTEM', 'OPENING', 'Opening stock', '22222222-0000-0000-0000-000000000006'),
    ('FINISHED_GOODS', NULL, NULL, '77777777-0000-0000-0000-000000000001', 'OPENING_STOCK', 50.0000, 50.0000, 30.000000, 1500.00, 'SYSTEM', 'OPENING', 'Opening stock', '22222222-0000-0000-0000-000000000006');
