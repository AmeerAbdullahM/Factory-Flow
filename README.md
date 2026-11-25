#Copyright (c) [2026] [Mohamed Abdullah M]. All rights reserved.

# FactoryFlow — Order-to-Audit Management System

FactoryFlow is an Order-to-Audit ERP built for a Tamil Nadu-based South Indian packaged-snack manufacturer (Murukku, Mixture, Chips, Sweets). It connects the **Sales loop** (mobile order → admin verification → GST invoice → dispatch → collections) and the **Production loop** (purchase → raw stock → BOM issue → production run → FG stock → variance & costing) into a single ledger-backed system.

## Tech Stack & Justification
We use **Java 21 + Spring Boot 3.4 + PostgreSQL (with Flyway)** on the backend and **React + TypeScript + Vite + Tailwind CSS** on the frontend. Spring Boot with `BigDecimal` arithmetic and `@Transactional` service boundaries guarantees atomic stock-ledger updates and exact monetary calculations without floating-point drift, while React + Tailwind delivers a fast, mobile-first order entry screen for field salesmen and clean desktop tables for admin, production, and audit teams.

## Quick Start (Local)

### Prerequisites
- Java 21+ (tested on JDK 25)
- PostgreSQL 16+ running on `localhost:5432` (database `factoryflow`, user `factoryflow_user`, password `factoryflow_pass`)
- Node 20+ & npm

### 1. Run Backend
```bash
cd backend
mvn spring-boot:run
```
Swagger UI: `http://localhost:8080/swagger-ui.html`

### 2. Run Frontend
```bash
cd frontend
npm install
npm run dev
```
Open `http://localhost:5173`.

### 3. Docker Compose
```bash
cp .env.example .env
docker compose up --build
```

## Seeded Test Logins (Password: `password123`)

| Role | Username | Password | Capabilities |
| :--- | :--- | :--- | :--- |
| Salesman A | `salesman_a` | `password123` | Mobile order entry, assigned customers, own orders |
| Salesman B | `salesman_b` | `password123` | Mobile order entry, assigned customers, own orders |
| Admin | `admin` | `password123` | Approve/reject orders, stock check, GST invoices, dispatch, payments, purchases |
| Production Supervisor | `supervisor` | `password123` | BOMs, production runs, actuals, wastage, overheads, variances |
| Accountant / Auditor | `auditor` | `password123` | Read-only access to all 10 reports + CSV/Excel/PDF exports + audit log |
| Owner | `owner` | `password123` | Executive dashboard, alerts, full visibility |

## Section 21 Benchmark Results (Verified by Automated Test)
Running `mvn test` executes `FullBenchmarkEndToEndTest`, asserting every benchmark figure:
- **Flour closing:** `120 kg` valued at `₹5,175.00` (weighted avg `₹43.125/kg`)
- **Oil closing:** `19.5 L` (`+5.0%` variance, **FLAGGED**)
- **Spice closing:** `3.8 kg` (`0%` variance)
- **Pouches closing:** `306 pcs` (`188` `PRODUCTION_ISSUE` + `6` `DAMAGED_MATERIAL` = `194` consumed, `-3.0%` variance, **NOT flagged** under strict `> 3%` rule, cost `₹485.00`)
- **Cartons closing:** `21 pcs` (`9` used vs `10` planned, `-10.0%` variance, **FLAGGED**)
- **Yield variance:** `188` vs `200` = `-6.0%` (**FLAGGED**)
- **FG Stock:** `238` after run → `88` after Retailer A dispatch
- **Retailer A Invoice:** `₹7,500 + ₹187.50 CGST + ₹187.50 SGST = ₹7,875.00`; after `₹4,000` payment → `₹3,875.00` outstanding
- **Distributor B:** `120` ordered vs `88` available → **Short by 32 packets** flagged; full invoice blocked
- **Cost per packet:** `₹5,855.00 / 188 = ₹31.1436` (`~₹31.14`, margin `~₹18.86` / `37.7%`)

Reset benchmark state anytime using `database/seed/reset-benchmark.sql`.

## Feature Checklist by Tier
- **MUST (Complete):** 5-role JWT auth & server-side RBAC, Masters & pack-size BOM reuse, Purchases & weighted-avg inventory, Atomic production posting with variance flags & damaged packaging, Ledger & reconciliation, Mobile salesman order entry, Admin stock check & approval, GST invoice PDF & dispatch, Payments & outstanding, 10 reports with CSV/Excel/PDF exports, Automated benchmark test.
- **SHOULD (Complete):** Per-packet costing with post-posting overheads & margins, GSTR-1/3B shaped GST summary, Outstanding ageing buckets (`0-30`, `31-60`, `61-90`, `90+`), Owner dashboard & variance alerts, Immutable audit log, Credit note invoice cancellation.
- **STRETCH (Not implemented in v1):** Offline PWA IndexedDB sync, batch/FEFO expiry, Tally XML export.
