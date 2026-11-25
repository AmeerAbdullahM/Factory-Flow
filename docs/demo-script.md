# FactoryFlow — 8-Minute Demo Script (Section 21 Benchmark)

1. **[0:00–0:45] Overview & Reset:** Show the clean login screen (`/login`) with 6 seeded role logins.
2. **[0:45–1:45] Purchase Receipt (Admin):** Log in as `admin` → **Masters & Purchases** → Receive 60 kg Rice Flour @ ₹45/kg from Sri Lakshmi Flour Mills. Show **Stock & Ledger** updating Flour to `160 kg` at weighted average `₹43.125/kg`.
3. **[1:45–3:30] Production Run & Variances (Supervisor):** Log in as `supervisor` → **Production Runs** → Start 1 batch of Murukku 200g (expected 200 pkts). Enter actuals: Flour `40 kg`, Oil `10.5 L`, Spice `1.2 kg`, Pouches `188` used + `6` damaged, Cartons `9`, Good Output `188`. Post run. Point out:
   - Yield `-6.0%` (flagged), Oil `+5.0%` (flagged), Cartons `-10.0%` (flagged), Pouches `-3.0%` (**not flagged**), Flour `0%`.
   - Stock Ledger shows `188 PRODUCTION_ISSUE` and `6 DAMAGED_MATERIAL` for pouches (`306` remaining), Flour `120 kg` (`₹5,175.00`), FG `238` packets.
4. **[3:30–4:30] Mobile Salesman Order (Salesman A):** Switch to mobile viewport, log in as `salesman_a` → **Take Order** → Select Retailer A → Tap `+10` / `+1` steppers to `150` pkts Murukku 200g → Submit order.
5. **[4:30–5:45] Admin Approval, GST Invoice PDF & Dispatch:** Log in as `admin` → **Orders & Billing** → See `238` available → Approve → Raise GST Invoice (`₹7,500 + ₹187.50 CGST + ₹187.50 SGST = ₹7,875`) → Download PDF → Record Dispatch (`FG = 88`).
6. **[5:45–6:30] Shortage Handling (Salesman B → Admin):** Log in as `salesman_b` → Order `120` pkts for Distributor B (Karnataka). Switch to `admin` → See red badge **"Short by 32 packets (Avail: 88)"**; full invoice is blocked; demonstrate adjusting to `88` for partial fulfilment.
7. **[6:30–7:15] Payment & Post-Posting Overheads:** Record `₹4,000` payment from Retailer A (`₹3,875` outstanding). Log in as `supervisor` → Add `₹1,200` Labour and `₹600` Gas/Power to the posted run → Cost per packet updates to `₹31.14` (margin `37.7%`).
8. **[7:15–8:00] Auditor Reports & Owner Dashboard:** Log in as `auditor` → Export Sales Register, GST Summary, Stock Summary to CSV/Excel/PDF. Log in as `owner` → Show live dashboard cards & variance alerts.
