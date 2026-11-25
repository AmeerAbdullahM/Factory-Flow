-- ============================================================
-- FactoryFlow Database Schema
-- Migration: V1 - Initial Schema
-- ============================================================

-- Enable UUID extension
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- ============================================================
-- AUTH & USERS
-- ============================================================

CREATE TABLE roles (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name VARCHAR(50) NOT NULL UNIQUE,
    description VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    username VARCHAR(50) NOT NULL UNIQUE,
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    full_name VARCHAR(100) NOT NULL,
    phone VARCHAR(20),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE user_roles (
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role_id UUID NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    PRIMARY KEY (user_id, role_id)
);

-- ============================================================
-- UNITS
-- ============================================================

CREATE TABLE units (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name VARCHAR(50) NOT NULL UNIQUE,
    symbol VARCHAR(20) NOT NULL UNIQUE,
    unit_type VARCHAR(30) NOT NULL, -- WEIGHT, VOLUME, COUNT
    base_unit VARCHAR(50),          -- the base unit for conversion (e.g. 'g' for weight)
    to_base_factor DECIMAL(20,10) NOT NULL DEFAULT 1, -- multiply by this to convert to base unit
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- ============================================================
-- SUPPLIERS
-- ============================================================

CREATE TABLE suppliers (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name VARCHAR(255) NOT NULL,
    contact_person VARCHAR(100),
    phone VARCHAR(20),
    email VARCHAR(255),
    address TEXT,
    city VARCHAR(100),
    state VARCHAR(100),
    state_code VARCHAR(5),
    gstin VARCHAR(20),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- ============================================================
-- RAW MATERIALS & PACKAGING
-- ============================================================

CREATE TABLE raw_materials (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name VARCHAR(255) NOT NULL,
    code VARCHAR(50) NOT NULL UNIQUE,
    description TEXT,
    unit_id UUID NOT NULL REFERENCES units(id),
    reorder_level DECIMAL(14,4) NOT NULL DEFAULT 0,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE packaging_materials (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name VARCHAR(255) NOT NULL,
    code VARCHAR(50) NOT NULL UNIQUE,
    description TEXT,
    unit_id UUID NOT NULL REFERENCES units(id),
    reorder_level DECIMAL(14,4) NOT NULL DEFAULT 0,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- ============================================================
-- PRODUCTS
-- ============================================================

CREATE TABLE products (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name VARCHAR(255) NOT NULL,
    code VARCHAR(50) NOT NULL UNIQUE,
    category VARCHAR(100) NOT NULL,  -- Murukku, Mixture, Chips, Sweets
    pack_size_grams DECIMAL(10,2),   -- weight in grams (for 200g pack: 200)
    pack_size_description VARCHAR(100), -- human-readable: '200g', '500g'
    base_product_id UUID REFERENCES products(id), -- for pack-size variants sharing a BOM
    bom_conversion_factor DECIMAL(10,6) DEFAULT 1.0, -- factor vs base BOM (e.g. 500g pack = 2.5x base 200g BOM)
    selling_price DECIMAL(10,2) NOT NULL,
    gst_rate DECIMAL(5,2) NOT NULL DEFAULT 5.00,
    hsn_code VARCHAR(20),
    unit_id UUID NOT NULL REFERENCES units(id),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- ============================================================
-- CUSTOMERS
-- ============================================================

CREATE TABLE customers (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name VARCHAR(255) NOT NULL,
    type VARCHAR(20) NOT NULL CHECK (type IN ('RETAILER', 'DISTRIBUTOR')),
    gstin VARCHAR(20),
    billing_address TEXT,
    city VARCHAR(100),
    state VARCHAR(100) NOT NULL DEFAULT 'Tamil Nadu',
    state_code VARCHAR(5) NOT NULL DEFAULT '33',
    phone VARCHAR(20),
    email VARCHAR(255),
    credit_limit DECIMAL(12,2) NOT NULL DEFAULT 0,
    payment_terms_days INTEGER NOT NULL DEFAULT 30,
    assigned_salesman_id UUID REFERENCES users(id),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- ============================================================
-- BOM (BILL OF MATERIALS)
-- ============================================================

CREATE TABLE bom_headers (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    code VARCHAR(50) NOT NULL UNIQUE,
    product_id UUID NOT NULL REFERENCES products(id),
    batch_size INTEGER NOT NULL DEFAULT 1,  -- number of batches this BOM describes
    expected_output INTEGER NOT NULL,       -- packets per batch
    version INTEGER NOT NULL DEFAULT 1,
    effective_date DATE NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    notes TEXT,
    created_by UUID REFERENCES users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (product_id, version)
);

CREATE TABLE bom_items (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    bom_header_id UUID NOT NULL REFERENCES bom_headers(id) ON DELETE CASCADE,
    item_type VARCHAR(20) NOT NULL CHECK (item_type IN ('RAW_MATERIAL', 'PACKAGING')),
    raw_material_id UUID REFERENCES raw_materials(id),
    packaging_material_id UUID REFERENCES packaging_materials(id),
    quantity DECIMAL(14,4) NOT NULL,
    unit_id UUID NOT NULL REFERENCES units(id),
    wastage_percent DECIMAL(5,2) NOT NULL DEFAULT 0,
    notes VARCHAR(255),
    CONSTRAINT bom_item_ref_check CHECK (
        (item_type = 'RAW_MATERIAL' AND raw_material_id IS NOT NULL AND packaging_material_id IS NULL) OR
        (item_type = 'PACKAGING' AND packaging_material_id IS NOT NULL AND raw_material_id IS NULL)
    )
);

-- ============================================================
-- INVENTORY: STOCK LEDGER (Source of Truth)
-- ============================================================

CREATE TABLE stock_movements (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    movement_date TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    item_type VARCHAR(20) NOT NULL CHECK (item_type IN ('RAW_MATERIAL', 'PACKAGING', 'FINISHED_GOODS')),
    raw_material_id UUID REFERENCES raw_materials(id),
    packaging_material_id UUID REFERENCES packaging_materials(id),
    product_id UUID REFERENCES products(id),
    movement_type VARCHAR(30) NOT NULL,
    -- Types: OPENING_STOCK, PURCHASE_RECEIPT, PRODUCTION_ISSUE, PRODUCTION_OUTPUT,
    --        DAMAGED_MATERIAL, SALES_DISPATCH, SALES_RETURN, STOCK_ADJUSTMENT
    qty_in DECIMAL(14,4),     -- positive = incoming
    qty_out DECIMAL(14,4),    -- positive = outgoing
    running_balance DECIMAL(14,4) NOT NULL,
    unit_cost DECIMAL(14,6),  -- cost per unit at time of movement (for valuation)
    total_value DECIMAL(14,2),
    source_document_type VARCHAR(50),  -- PURCHASE, PRODUCTION_RUN, SALES_ORDER, INVOICE, DISPATCH, ADJUSTMENT
    source_document_id UUID,
    reference_number VARCHAR(100),
    notes TEXT,
    created_by UUID REFERENCES users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT sm_item_ref CHECK (
        (item_type = 'RAW_MATERIAL' AND raw_material_id IS NOT NULL) OR
        (item_type = 'PACKAGING' AND packaging_material_id IS NOT NULL) OR
        (item_type = 'FINISHED_GOODS' AND product_id IS NOT NULL)
    ),
    CONSTRAINT sm_qty_check CHECK (
        (qty_in IS NOT NULL AND qty_in >= 0) OR (qty_out IS NOT NULL AND qty_out >= 0)
    )
);

-- Materialized balance cache (derived from ledger, reconcilable)
CREATE TABLE inventory_balances (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    item_type VARCHAR(20) NOT NULL,
    raw_material_id UUID REFERENCES raw_materials(id),
    packaging_material_id UUID REFERENCES packaging_materials(id),
    product_id UUID REFERENCES products(id),
    current_quantity DECIMAL(14,4) NOT NULL DEFAULT 0,
    weighted_avg_cost DECIMAL(14,6) NOT NULL DEFAULT 0, -- per unit
    total_value DECIMAL(14,2) NOT NULL DEFAULT 0,
    last_updated TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (item_type, raw_material_id, packaging_material_id, product_id)
);

-- ============================================================
-- PURCHASES
-- ============================================================

CREATE TABLE purchases (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    reference_number VARCHAR(100) NOT NULL UNIQUE,
    supplier_id UUID NOT NULL REFERENCES suppliers(id),
    purchase_date DATE NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT', 'RECEIVED', 'CANCELLED')),
    taxable_amount DECIMAL(12,2) NOT NULL DEFAULT 0,
    gst_amount DECIMAL(12,2) NOT NULL DEFAULT 0,
    total_amount DECIMAL(12,2) NOT NULL DEFAULT 0,
    notes TEXT,
    created_by UUID REFERENCES users(id),
    received_by UUID REFERENCES users(id),
    received_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE purchase_items (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    purchase_id UUID NOT NULL REFERENCES purchases(id) ON DELETE CASCADE,
    item_type VARCHAR(20) NOT NULL CHECK (item_type IN ('RAW_MATERIAL', 'PACKAGING')),
    raw_material_id UUID REFERENCES raw_materials(id),
    packaging_material_id UUID REFERENCES packaging_materials(id),
    quantity DECIMAL(14,4) NOT NULL,
    unit_id UUID NOT NULL REFERENCES units(id),
    unit_rate DECIMAL(14,6) NOT NULL,
    taxable_amount DECIMAL(12,2) NOT NULL,
    gst_rate DECIMAL(5,2) NOT NULL DEFAULT 0,
    gst_amount DECIMAL(12,2) NOT NULL DEFAULT 0,
    total_amount DECIMAL(12,2) NOT NULL
);

-- ============================================================
-- PRODUCTION
-- ============================================================

CREATE TABLE production_runs (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    reference_number VARCHAR(100) NOT NULL UNIQUE,
    product_id UUID NOT NULL REFERENCES products(id),
    bom_header_id UUID NOT NULL REFERENCES bom_headers(id),
    bom_version INTEGER NOT NULL,
    run_date DATE NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT'
        CHECK (status IN ('DRAFT', 'POSTED', 'CANCELLED')),

    -- Planned (from BOM)
    planned_batches INTEGER NOT NULL DEFAULT 1,
    planned_output INTEGER NOT NULL,

    -- Actual (entered by supervisor)
    actual_good_output INTEGER,
    wastage_packets INTEGER NOT NULL DEFAULT 0,
    notes TEXT,

    -- Costing totals (computed on post)
    total_material_cost DECIMAL(12,2),
    total_packaging_cost DECIMAL(12,2),
    total_labour_cost DECIMAL(12,2) NOT NULL DEFAULT 0,
    total_overhead_cost DECIMAL(12,2) NOT NULL DEFAULT 0,
    total_cost DECIMAL(12,2),
    cost_per_packet DECIMAL(10,4),

    -- Variance flags
    yield_variance_percent DECIMAL(8,4),
    is_yield_flagged BOOLEAN NOT NULL DEFAULT FALSE,

    created_by UUID REFERENCES users(id),
    posted_by UUID REFERENCES users(id),
    posted_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE production_material_issues (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    production_run_id UUID NOT NULL REFERENCES production_runs(id) ON DELETE CASCADE,
    item_type VARCHAR(20) NOT NULL CHECK (item_type IN ('RAW_MATERIAL', 'PACKAGING')),
    raw_material_id UUID REFERENCES raw_materials(id),
    packaging_material_id UUID REFERENCES packaging_materials(id),

    planned_quantity DECIMAL(14,4) NOT NULL,
    actual_quantity DECIMAL(14,4) NOT NULL,
    damaged_quantity DECIMAL(14,4) NOT NULL DEFAULT 0,  -- for packaging only
    unit_id UUID NOT NULL REFERENCES units(id),

    unit_cost DECIMAL(14,6),   -- weighted avg cost at time of posting
    total_cost DECIMAL(12,2),

    variance_percent DECIMAL(8,4),
    is_variance_flagged BOOLEAN NOT NULL DEFAULT FALSE,

    -- Stock movement IDs (set when run is posted)
    issue_movement_id UUID REFERENCES stock_movements(id),
    damaged_movement_id UUID REFERENCES stock_movements(id)
);

CREATE TABLE production_costs (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    production_run_id UUID NOT NULL REFERENCES production_runs(id) ON DELETE CASCADE,
    cost_type VARCHAR(50) NOT NULL,  -- LABOUR, GAS, POWER, OTHER
    description VARCHAR(255),
    amount DECIMAL(12,2) NOT NULL,
    added_by UUID REFERENCES users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- ============================================================
-- SALES ORDERS
-- ============================================================

CREATE TABLE sales_orders (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    order_number VARCHAR(100) NOT NULL UNIQUE,
    customer_id UUID NOT NULL REFERENCES customers(id),
    salesman_id UUID NOT NULL REFERENCES users(id),
    order_date DATE NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'SUBMITTED'
        CHECK (status IN ('SUBMITTED', 'APPROVED', 'REJECTED', 'INVOICED',
                          'PARTIALLY_DISPATCHED', 'DISPATCHED', 'CANCELLED')),
    notes TEXT,
    rejection_reason TEXT,
    approved_by UUID REFERENCES users(id),
    approved_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE sales_order_items (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    sales_order_id UUID NOT NULL REFERENCES sales_orders(id) ON DELETE CASCADE,
    product_id UUID NOT NULL REFERENCES products(id),
    ordered_quantity INTEGER NOT NULL,
    approved_quantity INTEGER,       -- admin can adjust
    unit_price DECIMAL(10,2) NOT NULL,
    gst_rate DECIMAL(5,2) NOT NULL,
    taxable_amount DECIMAL(12,2),
    gst_amount DECIMAL(12,2),
    total_amount DECIMAL(12,2)
);

-- ============================================================
-- INVOICES
-- ============================================================

CREATE TABLE invoices (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    invoice_number VARCHAR(50) NOT NULL UNIQUE,
    financial_year VARCHAR(10) NOT NULL,  -- e.g. '2026-27'
    sequence_number INTEGER NOT NULL,
    sales_order_id UUID REFERENCES sales_orders(id),
    customer_id UUID NOT NULL REFERENCES customers(id),
    invoice_date DATE NOT NULL,
    place_of_supply VARCHAR(100) NOT NULL,
    supply_state_code VARCHAR(5) NOT NULL,
    is_interstate BOOLEAN NOT NULL DEFAULT FALSE,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'CANCELLED')),

    taxable_amount DECIMAL(12,2) NOT NULL,
    cgst_amount DECIMAL(12,2) NOT NULL DEFAULT 0,
    sgst_amount DECIMAL(12,2) NOT NULL DEFAULT 0,
    igst_amount DECIMAL(12,2) NOT NULL DEFAULT 0,
    total_amount DECIMAL(12,2) NOT NULL,

    amount_in_words TEXT,
    payment_terms TEXT,
    notes TEXT,

    created_by UUID REFERENCES users(id),
    cancelled_by UUID REFERENCES users(id),
    cancelled_at TIMESTAMPTZ,
    cancellation_reason TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE invoice_items (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    invoice_id UUID NOT NULL REFERENCES invoices(id) ON DELETE CASCADE,
    product_id UUID NOT NULL REFERENCES products(id),
    sales_order_item_id UUID REFERENCES sales_order_items(id),
    hsn_code VARCHAR(20),
    description VARCHAR(255),
    quantity INTEGER NOT NULL,
    unit_price DECIMAL(10,2) NOT NULL,
    taxable_amount DECIMAL(12,2) NOT NULL,
    gst_rate DECIMAL(5,2) NOT NULL,
    cgst_rate DECIMAL(5,2) NOT NULL DEFAULT 0,
    sgst_rate DECIMAL(5,2) NOT NULL DEFAULT 0,
    igst_rate DECIMAL(5,2) NOT NULL DEFAULT 0,
    cgst_amount DECIMAL(12,2) NOT NULL DEFAULT 0,
    sgst_amount DECIMAL(12,2) NOT NULL DEFAULT 0,
    igst_amount DECIMAL(12,2) NOT NULL DEFAULT 0,
    total_amount DECIMAL(12,2) NOT NULL
);

-- Invoice sequence tracker (concurrency-safe)
CREATE TABLE invoice_sequences (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    financial_year VARCHAR(10) NOT NULL UNIQUE,
    last_sequence INTEGER NOT NULL DEFAULT 0
);

-- Credit Notes
CREATE TABLE credit_notes (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    credit_note_number VARCHAR(50) NOT NULL UNIQUE,
    invoice_id UUID NOT NULL REFERENCES invoices(id),
    customer_id UUID NOT NULL REFERENCES customers(id),
    credit_date DATE NOT NULL,
    reason TEXT NOT NULL,
    amount DECIMAL(12,2) NOT NULL,
    created_by UUID REFERENCES users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- ============================================================
-- DISPATCH
-- ============================================================

CREATE TABLE dispatches (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    dispatch_number VARCHAR(100) NOT NULL UNIQUE,
    invoice_id UUID NOT NULL REFERENCES invoices(id),
    customer_id UUID NOT NULL REFERENCES customers(id),
    dispatch_date DATE NOT NULL,
    transport_details VARCHAR(255),
    delivery_reference VARCHAR(100),
    status VARCHAR(20) NOT NULL DEFAULT 'DISPATCHED',
    notes TEXT,
    created_by UUID REFERENCES users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE dispatch_items (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    dispatch_id UUID NOT NULL REFERENCES dispatches(id) ON DELETE CASCADE,
    invoice_item_id UUID REFERENCES invoice_items(id),
    product_id UUID NOT NULL REFERENCES products(id),
    quantity INTEGER NOT NULL,
    stock_movement_id UUID REFERENCES stock_movements(id)
);

-- ============================================================
-- PAYMENTS & RECEIVABLES
-- ============================================================

CREATE TABLE payments (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    reference_number VARCHAR(100) NOT NULL UNIQUE,
    customer_id UUID NOT NULL REFERENCES customers(id),
    payment_date DATE NOT NULL,
    payment_method VARCHAR(50) NOT NULL,  -- CASH, CHEQUE, NEFT, UPI, OTHER
    amount DECIMAL(12,2) NOT NULL,
    bank_reference VARCHAR(100),
    notes TEXT,
    created_by UUID REFERENCES users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE payment_allocations (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    payment_id UUID NOT NULL REFERENCES payments(id) ON DELETE CASCADE,
    invoice_id UUID NOT NULL REFERENCES invoices(id),
    allocated_amount DECIMAL(12,2) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- ============================================================
-- AUDIT LOG
-- ============================================================

CREATE TABLE audit_logs (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID REFERENCES users(id),
    username VARCHAR(50),
    role VARCHAR(50),
    action VARCHAR(100) NOT NULL,
    entity_type VARCHAR(50),
    entity_id UUID,
    old_values JSONB,
    new_values JSONB,
    reference_number VARCHAR(100),
    ip_address VARCHAR(45),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- ============================================================
-- INDEXES for performance
-- ============================================================

CREATE INDEX idx_stock_movements_item ON stock_movements(item_type, raw_material_id, packaging_material_id, product_id);
CREATE INDEX idx_stock_movements_date ON stock_movements(movement_date);
CREATE INDEX idx_stock_movements_source ON stock_movements(source_document_type, source_document_id);
CREATE INDEX idx_sales_orders_customer ON sales_orders(customer_id);
CREATE INDEX idx_sales_orders_salesman ON sales_orders(salesman_id);
CREATE INDEX idx_sales_orders_status ON sales_orders(status);
CREATE INDEX idx_invoices_customer ON invoices(customer_id);
CREATE INDEX idx_invoices_date ON invoices(invoice_date);
CREATE INDEX idx_payments_customer ON payments(customer_id);
CREATE INDEX idx_audit_logs_entity ON audit_logs(entity_type, entity_id);
CREATE INDEX idx_audit_logs_user ON audit_logs(user_id);
CREATE INDEX idx_production_runs_product ON production_runs(product_id);
CREATE INDEX idx_purchases_supplier ON purchases(supplier_id);
