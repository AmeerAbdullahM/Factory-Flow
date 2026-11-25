package com.factoryflow.reports;

import com.factoryflow.payments.PaymentService;
import com.factoryflow.reports.ReportService.*;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/reports")
@PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT','OWNER','PRODUCTION_SUPERVISOR')")
public class ReportController {

    private final ReportService reportService;
    private final PaymentService paymentService;

    public ReportController(ReportService reportService, PaymentService paymentService) {
        this.reportService = reportService;
        this.paymentService = paymentService;
    }

    @GetMapping("/sales")
    public ResponseEntity<?> salesRegister(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) UUID customerId,
            @RequestParam(defaultValue = "json") String format) {
        var rows = reportService.getSalesRegister(from, to, customerId);
        if ("json".equalsIgnoreCase(format)) return ResponseEntity.ok(rows);
        var headers = List.of("Invoice No", "Date", "Customer", "GSTIN", "Place of Supply", "Status", "Taxable", "CGST", "SGST", "IGST", "Total");
        var data = rows.stream().map(r -> List.of(
                r.invoiceNumber(), r.invoiceDate(), r.customerName(), r.gstin(), r.placeOfSupply(),
                r.status(), r.taxableAmount().toString(), r.cgstAmount().toString(),
                r.sgstAmount().toString(), r.igstAmount().toString(), r.totalAmount().toString()
        )).toList();
        return exportResponse("sales-register", "Sales Register", headers, data, format);
    }

    @GetMapping("/sales/by-customer")
    public ResponseEntity<?> customerSales(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(reportService.getCustomerWiseSales(from, to));
    }

    @GetMapping("/sales/by-product")
    public ResponseEntity<?> productSales(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(reportService.getProductWiseSales(from, to));
    }

    @GetMapping("/gst")
    public ResponseEntity<?> gstSummary(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "json") String format) {
        var rows = reportService.getGstSummary(from, to);
        if ("json".equalsIgnoreCase(format)) return ResponseEntity.ok(rows);
        var headers = List.of("Supply Type", "GST %", "Taxable", "CGST", "SGST", "IGST", "Total");
        var data = rows.stream().map(r -> List.of(
                r.supplyType(), r.gstRate().toString(), r.taxableValue().toString(),
                r.cgstAmount().toString(), r.sgstAmount().toString(), r.igstAmount().toString(), r.totalValue().toString()
        )).toList();
        return exportResponse("gst-summary", "GST Summary", headers, data, format);
    }

    @GetMapping("/purchases")
    public ResponseEntity<?> purchaseRegister(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "json") String format) {
        var rows = reportService.getPurchaseRegister(from, to);
        if ("json".equalsIgnoreCase(format)) return ResponseEntity.ok(rows);
        var headers = List.of("Ref", "Date", "Supplier", "Status", "Material", "Qty", "Unit", "Rate", "Taxable", "GST", "Total");
        var data = rows.stream().map(r -> List.of(
                r.referenceNumber(), r.purchaseDate(), r.supplierName(), r.status(), r.materialName(),
                r.quantity().toString(), r.unitSymbol(), r.unitRate().toString(),
                r.taxableAmount().toString(), r.gstAmount().toString(), r.totalAmount().toString()
        )).toList();
        return exportResponse("purchase-register", "Purchase Register", headers, data, format);
    }

    @GetMapping("/stock")
    public ResponseEntity<?> stockSummary(@RequestParam(defaultValue = "json") String format) {
        var rows = reportService.getStockSummary();
        if ("json".equalsIgnoreCase(format)) return ResponseEntity.ok(rows);
        var headers = List.of("Type", "Item", "Unit", "Closing Qty", "Weighted Avg Cost", "Total Value");
        var data = rows.stream().map(r -> List.of(
                r.itemType(), r.itemName(), r.unitSymbol(),
                r.closingQuantity().toString(), r.weightedAvgCost().toString(), r.totalValue().toString()
        )).toList();
        return exportResponse("stock-summary", "Stock Summary", headers, data, format);
    }

    @GetMapping("/stock-ledger")
    public ResponseEntity<?> stockLedger(@RequestParam(defaultValue = "json") String format) {
        var rows = reportService.getStockLedger();
        if ("json".equalsIgnoreCase(format)) return ResponseEntity.ok(rows);
        var headers = List.of("Date", "Item", "Category", "Movement", "Source", "Ref", "In", "Out", "Balance", "Unit Cost", "Value");
        var data = rows.stream().map(r -> List.of(
                r.date(), r.itemName(), r.itemType(), r.movementType(),
                r.sourceDocumentType() != null ? r.sourceDocumentType() : "",
                r.referenceNumber() != null ? r.referenceNumber() : "",
                r.qtyIn() != null ? r.qtyIn().toString() : "",
                r.qtyOut() != null ? r.qtyOut().toString() : "",
                r.runningBalance().toString(),
                r.unitCost() != null ? r.unitCost().toString() : "",
                r.totalValue() != null ? r.totalValue().toString() : ""
        )).toList();
        return exportResponse("stock-ledger", "Stock Ledger", headers, data, format);
    }

    @GetMapping("/production")
    public ResponseEntity<?> productionReport(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "json") String format) {
        var rows = reportService.getProductionReport(from, to);
        if ("json".equalsIgnoreCase(format)) return ResponseEntity.ok(rows);
        var headers = List.of("Ref", "Date", "Product", "Status", "Planned", "Actual", "Wastage", "Yield Var %", "Total Cost", "Cost/Pkt");
        var data = rows.stream().map(r -> List.of(
                r.referenceNumber(), r.runDate(), r.productName(), r.status(),
                String.valueOf(r.plannedOutput()),
                r.actualGoodOutput() != null ? r.actualGoodOutput().toString() : "",
                String.valueOf(r.wastagePackets()),
                r.yieldVariancePercent() != null ? r.yieldVariancePercent().toString() : "",
                r.totalCost() != null ? r.totalCost().toString() : "",
                r.costPerPacket() != null ? r.costPerPacket().toString() : ""
        )).toList();
        return exportResponse("production-report", "Production Report", headers, data, format);
    }

    @GetMapping("/collections")
    public ResponseEntity<?> collections(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(reportService.getCollections(from, to));
    }

    @GetMapping("/outstanding")
    public ResponseEntity<?> outstanding(@RequestParam(defaultValue = "json") String format) {
        var rows = paymentService.getAllOutstanding();
        if ("json".equalsIgnoreCase(format)) return ResponseEntity.ok(rows);
        var headers = List.of("Customer", "Invoice No", "Invoice Date", "Total", "Paid", "Outstanding", "Days", "Bucket", "Overdue");
        var data = rows.stream().map(r -> List.of(
                r.customerName(), r.invoiceNumber(), r.invoiceDate().toString(),
                r.invoiceTotal().toString(), r.paidAmount().toString(), r.outstanding().toString(),
                String.valueOf(r.daysSinceInvoice()), r.agingBucket(), String.valueOf(r.overdue())
        )).toList();
        return exportResponse("outstanding-ageing", "Outstanding Receivables", headers, data, format);
    }

    private ResponseEntity<byte[]> exportResponse(String filePrefix, String title,
                                                  List<String> headers, List<List<String>> rows, String format) {
        if ("csv".equalsIgnoreCase(format)) {
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + filePrefix + ".csv")
                    .contentType(MediaType.parseMediaType("text/csv"))
                    .body(reportService.exportCsv(headers, rows));
        } else if ("xlsx".equalsIgnoreCase(format) || "excel".equalsIgnoreCase(format)) {
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + filePrefix + ".xlsx")
                    .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .body(reportService.exportExcel(title, headers, rows));
        } else {
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + filePrefix + ".pdf")
                    .contentType(MediaType.APPLICATION_PDF)
                    .body(reportService.exportPdf(title, headers, rows));
        }
    }
}
