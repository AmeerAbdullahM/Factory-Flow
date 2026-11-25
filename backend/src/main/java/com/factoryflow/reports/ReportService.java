package com.factoryflow.reports;

import com.factoryflow.common.BusinessException;
import com.factoryflow.inventory.InventoryBalance;
import com.factoryflow.inventory.InventoryService;
import com.factoryflow.inventory.StockMovement;
import com.factoryflow.invoicing.Invoice;
import com.factoryflow.invoicing.InvoiceItem;
import com.factoryflow.invoicing.InvoiceRepository;
import com.factoryflow.payments.Payment;
import com.factoryflow.payments.PaymentRepository;
import com.factoryflow.payments.PaymentService;
import com.factoryflow.production.ProductionRun;
import com.factoryflow.production.ProductionRunRepository;
import com.factoryflow.purchases.Purchase;
import com.factoryflow.purchases.PurchaseItem;
import com.factoryflow.purchases.PurchaseRepository;
import com.lowagie.text.Document;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;

@Service
public class ReportService {

    private final InvoiceRepository invoiceRepo;
    private final PurchaseRepository purchaseRepo;
    private final InventoryService inventoryService;
    private final ProductionRunRepository runRepo;
    private final PaymentRepository paymentRepo;
    private final PaymentService paymentService;

    public ReportService(InvoiceRepository invoiceRepo, PurchaseRepository purchaseRepo,
                         InventoryService inventoryService, ProductionRunRepository runRepo,
                         PaymentRepository paymentRepo, PaymentService paymentService) {
        this.invoiceRepo = invoiceRepo;
        this.purchaseRepo = purchaseRepo;
        this.inventoryService = inventoryService;
        this.runRepo = runRepo;
        this.paymentRepo = paymentRepo;
        this.paymentService = paymentService;
    }

    // 1. Sales Register
    public List<SalesRegisterRow> getSalesRegister(LocalDate from, LocalDate to, UUID customerId) {
        return filterInvoices(from, to, customerId).stream()
                .map(i -> new SalesRegisterRow(
                        i.getInvoiceNumber(), i.getInvoiceDate().toString(),
                        i.getCustomer().getName(),
                        i.getCustomer().getGstin() != null ? i.getCustomer().getGstin() : "",
                        i.getPlaceOfSupply(), i.getStatus(),
                        i.getTaxableAmount(), i.getCgstAmount(), i.getSgstAmount(),
                        i.getIgstAmount(), i.getTotalAmount()
                )).toList();
    }

    // 2. Customer-wise Sales
    public List<CustomerSalesRow> getCustomerWiseSales(LocalDate from, LocalDate to) {
        var invoices = filterInvoices(from, to, null).stream()
                .filter(i -> "ACTIVE".equals(i.getStatus())).toList();
        var outstandingMap = new HashMap<UUID, BigDecimal>();
        for (var o : paymentService.getAllOutstanding()) {
            outstandingMap.merge(o.customerId(), o.outstanding(), BigDecimal::add);
        }
        var paidMap = new HashMap<UUID, BigDecimal>();
        for (Payment p : paymentRepo.findAll()) {
            paidMap.merge(p.getCustomer().getId(), p.getAmount(), BigDecimal::add);
        }

        Map<UUID, List<Invoice>> byCust = new LinkedHashMap<>();
        for (Invoice i : invoices) {
            byCust.computeIfAbsent(i.getCustomer().getId(), k -> new ArrayList<>()).add(i);
        }

        return byCust.entrySet().stream().map(e -> {
            UUID custId = e.getKey();
            var list = e.getValue();
            String custName = list.get(0).getCustomer().getName();
            BigDecimal totalSales = list.stream().map(Invoice::getTotalAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal collections = paidMap.getOrDefault(custId, BigDecimal.ZERO);
            BigDecimal outstanding = outstandingMap.getOrDefault(custId, BigDecimal.ZERO);
            return new CustomerSalesRow(custId, custName, list.size(), totalSales, collections, outstanding);
        }).toList();
    }

    // 3. Product-wise Sales
    public List<ProductSalesRow> getProductWiseSales(LocalDate from, LocalDate to) {
        var invoices = filterInvoices(from, to, null).stream()
                .filter(i -> "ACTIVE".equals(i.getStatus())).toList();
        Map<String, List<InvoiceItem>> byProd = new LinkedHashMap<>();
        for (Invoice i : invoices) {
            for (InvoiceItem item : i.getItems()) {
                byProd.computeIfAbsent(item.getProduct().getName(), k -> new ArrayList<>()).add(item);
            }
        }
        return byProd.entrySet().stream().map(e -> {
            int qty = e.getValue().stream().mapToInt(InvoiceItem::getQuantity).sum();
            BigDecimal revenue = e.getValue().stream().map(InvoiceItem::getTaxableAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal avgPrice = qty > 0
                    ? revenue.divide(BigDecimal.valueOf(qty), 2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;
            return new ProductSalesRow(e.getKey(), qty, revenue, avgPrice);
        }).toList();
    }

    // 4. GST Summary (GSTR-1 / 3B shaped)
    public List<GstSummaryRow> getGstSummary(LocalDate from, LocalDate to) {
        var invoices = filterInvoices(from, to, null).stream()
                .filter(i -> "ACTIVE".equals(i.getStatus())).toList();
        Map<String, List<InvoiceItem>> grouped = new LinkedHashMap<>();
        for (Invoice i : invoices) {
            String supplyType = (i.getCustomer().getGstin() != null && !i.getCustomer().getGstin().isBlank())
                    ? "B2B" : "B2C";
            for (InvoiceItem item : i.getItems()) {
                String key = supplyType + "|" + item.getGstRate().setScale(2, RoundingMode.HALF_UP);
                grouped.computeIfAbsent(key, k -> new ArrayList<>()).add(item);
            }
        }
        return grouped.entrySet().stream().map(e -> {
            String[] parts = e.getKey().split("\\|");
            var items = e.getValue();
            BigDecimal taxable = items.stream().map(InvoiceItem::getTaxableAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal cgst = items.stream().map(InvoiceItem::getCgstAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal sgst = items.stream().map(InvoiceItem::getSgstAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal igst = items.stream().map(InvoiceItem::getIgstAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
            return new GstSummaryRow(parts[0], new BigDecimal(parts[1]), taxable, cgst, sgst, igst,
                    taxable.add(cgst).add(sgst).add(igst));
        }).toList();
    }

    // 5. Purchase Register
    public List<PurchaseRegisterRow> getPurchaseRegister(LocalDate from, LocalDate to) {
        List<Purchase> purchases = (from != null && to != null)
                ? purchaseRepo.findByPurchaseDateBetween(from, to)
                : purchaseRepo.findAll();
        List<PurchaseRegisterRow> rows = new ArrayList<>();
        for (Purchase p : purchases) {
            for (PurchaseItem item : p.getItems()) {
                String matName = "RAW_MATERIAL".equals(item.getItemType())
                        ? item.getRawMaterial().getName()
                        : item.getPackagingMaterial().getName();
                rows.add(new PurchaseRegisterRow(
                        p.getReferenceNumber(), p.getPurchaseDate().toString(),
                        p.getSupplier().getName(), p.getStatus(), matName,
                        item.getQuantity(), item.getUnit().getSymbol(),
                        item.getUnitRate(), item.getTaxableAmount(),
                        item.getGstAmount(), item.getTotalAmount()
                ));
            }
        }
        return rows;
    }

    // 6. Stock Summary
    public List<StockSummaryRow> getStockSummary() {
        List<InventoryBalance> balances = inventoryService.getAllBalances();
        return balances.stream().map(b -> {
            String name;
            String unitSymbol;
            if ("RAW_MATERIAL".equals(b.getItemType()) && b.getRawMaterial() != null) {
                name = b.getRawMaterial().getName();
                unitSymbol = b.getRawMaterial().getUnit().getSymbol();
            } else if ("PACKAGING".equals(b.getItemType()) && b.getPackagingMaterial() != null) {
                name = b.getPackagingMaterial().getName();
                unitSymbol = b.getPackagingMaterial().getUnit().getSymbol();
            } else if (b.getProduct() != null) {
                name = b.getProduct().getName();
                unitSymbol = b.getProduct().getUnit() != null ? b.getProduct().getUnit().getSymbol() : "pkt";
            } else {
                name = "Unknown";
                unitSymbol = "";
            }
            return new StockSummaryRow(
                    b.getItemType(), name, unitSymbol,
                    b.getCurrentQuantity(), b.getWeightedAvgCost(), b.getTotalValue()
            );
        }).toList();
    }

    // 7. Stock Ledger
    public List<StockLedgerRow> getStockLedger() {
        return inventoryService.getAllMovements().stream()
                .sorted(Comparator.comparing(StockMovement::getCreatedAt))
                .map(m -> {
                    String name = "RAW_MATERIAL".equals(m.getItemType()) && m.getRawMaterial() != null
                            ? m.getRawMaterial().getName()
                            : "PACKAGING".equals(m.getItemType()) && m.getPackagingMaterial() != null
                            ? m.getPackagingMaterial().getName()
                            : m.getProduct() != null ? m.getProduct().getName() : "Unknown";
                    return new StockLedgerRow(
                            m.getMovementDate().toString(), name, m.getItemType(),
                            m.getMovementType(), m.getSourceDocumentType(), m.getReferenceNumber(),
                            m.getQtyIn(), m.getQtyOut(), m.getRunningBalance(),
                            m.getUnitCost(), m.getTotalValue()
                    );
                }).toList();
    }

    // 8. Production Report
    public List<ProductionReportRow> getProductionReport(LocalDate from, LocalDate to) {
        List<ProductionRun> runs = (from != null && to != null)
                ? runRepo.findByRunDateBetween(from, to)
                : runRepo.findAllByOrderByCreatedAtDesc();
        return runs.stream().map(r -> new ProductionReportRow(
                r.getReferenceNumber(), r.getRunDate().toString(), r.getProduct().getName(),
                r.getStatus(), r.getPlannedOutput(), r.getActualGoodOutput(),
                r.getWastagePackets(), r.getYieldVariancePercent(), r.isYieldFlagged(),
                r.getTotalMaterialCost(), r.getTotalPackagingCost(),
                r.getTotalLabourCost(), r.getTotalOverheadCost(),
                r.getTotalCost(), r.getCostPerPacket()
        )).toList();
    }

    // 9. Collections Report
    public List<CollectionRow> getCollections(LocalDate from, LocalDate to) {
        List<Payment> payments = (from != null && to != null)
                ? paymentRepo.findByPaymentDateBetween(from, to)
                : paymentRepo.findAllByOrderByCreatedAtDesc();
        return payments.stream().map(p -> new CollectionRow(
                p.getReferenceNumber(), p.getPaymentDate().toString(),
                p.getCustomer().getName(), p.getPaymentMethod(),
                p.getAmount(), p.getBankReference()
        )).toList();
    }

    // =========================================================
    // EXPORT HELPERS (CSV, XLSX, PDF)
    // =========================================================

    public byte[] exportCsv(List<String> headers, List<List<String>> rows) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.join(",", headers.stream().map(this::csvEscape).toList())).append("\n");
        for (List<String> row : rows) {
            sb.append(String.join(",", row.stream().map(this::csvEscape).toList())).append("\n");
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    public byte[] exportExcel(String sheetName, List<String> headers, List<List<String>> rows) {
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet(sheetName);
            CellStyle headerStyle = wb.createCellStyle();
            org.apache.poi.ss.usermodel.Font font = wb.createFont();
            font.setBold(true);
            headerStyle.setFont(font);

            Row headerRow = sheet.createRow(0);
            for (int c = 0; c < headers.size(); c++) {
                Cell cell = headerRow.createCell(c);
                cell.setCellValue(headers.get(c));
                cell.setCellStyle(headerStyle);
            }
            for (int r = 0; r < rows.size(); r++) {
                Row row = sheet.createRow(r + 1);
                List<String> vals = rows.get(r);
                for (int c = 0; c < vals.size(); c++) {
                    row.createCell(c).setCellValue(vals.get(c) != null ? vals.get(c) : "");
                }
            }
            for (int c = 0; c < headers.size(); c++) {
                sheet.autoSizeColumn(c);
            }
            wb.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new BusinessException("Excel export failed: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    public byte[] exportPdf(String title, List<String> headers, List<List<String>> rows) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document doc = new Document(PageSize.A4.rotate(), 24, 24, 24, 24);
            PdfWriter.getInstance(doc, out);
            doc.open();

            Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14);
            Font headFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8);
            Font bodyFont = FontFactory.getFont(FontFactory.HELVETICA, 8);

            doc.add(new Paragraph("FactoryFlow Report: " + title, titleFont));
            doc.add(new Paragraph("Generated: " + LocalDate.now(), bodyFont));
            doc.add(new Paragraph(" "));

            PdfPTable table = new PdfPTable(headers.size());
            table.setWidthPercentage(100);
            for (String h : headers) {
                PdfPCell cell = new PdfPCell(new Phrase(h, headFont));
                cell.setBackgroundColor(new Color(235, 238, 242));
                cell.setPadding(4);
                table.addCell(cell);
            }
            for (List<String> row : rows) {
                for (String val : row) {
                    table.addCell(new Phrase(val != null ? val : "", bodyFont));
                }
            }
            doc.add(table);
            doc.close();
            return out.toByteArray();
        } catch (Exception e) {
            throw new BusinessException("PDF export failed: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private String csvEscape(String val) {
        if (val == null) return "";
        if (val.contains(",") || val.contains("\"") || val.contains("\n")) {
            return "\"" + val.replace("\"", "\"\"") + "\"";
        }
        return val;
    }

    private List<Invoice> filterInvoices(LocalDate from, LocalDate to, UUID customerId) {
        List<Invoice> list = (from != null && to != null)
                ? invoiceRepo.findByInvoiceDateBetween(from, to)
                : invoiceRepo.findAllByOrderByCreatedAtDesc();
        if (customerId != null) {
            list = list.stream().filter(i -> i.getCustomer().getId().equals(customerId)).toList();
        }
        return list;
    }

    // Report Row DTOs
    public record SalesRegisterRow(String invoiceNumber, String invoiceDate, String customerName,
                                   String gstin, String placeOfSupply, String status,
                                   BigDecimal taxableAmount, BigDecimal cgstAmount, BigDecimal sgstAmount,
                                   BigDecimal igstAmount, BigDecimal totalAmount) {}

    public record CustomerSalesRow(UUID customerId, String customerName, int invoiceCount,
                                   BigDecimal totalSales, BigDecimal totalCollections, BigDecimal outstanding) {}

    public record ProductSalesRow(String productName, int totalQuantity, BigDecimal totalRevenue, BigDecimal avgPrice) {}

    public record GstSummaryRow(String supplyType, BigDecimal gstRate, BigDecimal taxableValue,
                                BigDecimal cgstAmount, BigDecimal sgstAmount, BigDecimal igstAmount,
                                BigDecimal totalValue) {}

    public record PurchaseRegisterRow(String referenceNumber, String purchaseDate, String supplierName,
                                      String status, String materialName, BigDecimal quantity, String unitSymbol,
                                      BigDecimal unitRate, BigDecimal taxableAmount, BigDecimal gstAmount,
                                      BigDecimal totalAmount) {}

    public record StockSummaryRow(String itemType, String itemName, String unitSymbol,
                                  BigDecimal closingQuantity, BigDecimal weightedAvgCost, BigDecimal totalValue) {}

    public record StockLedgerRow(String date, String itemName, String itemType, String movementType,
                                 String sourceDocumentType, String referenceNumber,
                                 BigDecimal qtyIn, BigDecimal qtyOut, BigDecimal runningBalance,
                                 BigDecimal unitCost, BigDecimal totalValue) {}

    public record ProductionReportRow(String referenceNumber, String runDate, String productName, String status,
                                      int plannedOutput, Integer actualGoodOutput, int wastagePackets,
                                      BigDecimal yieldVariancePercent, boolean yieldFlagged,
                                      BigDecimal materialCost, BigDecimal packagingCost,
                                      BigDecimal labourCost, BigDecimal overheadCost,
                                      BigDecimal totalCost, BigDecimal costPerPacket) {}

    public record CollectionRow(String referenceNumber, String paymentDate, String customerName,
                                String paymentMethod, BigDecimal amount, String bankReference) {}
}
