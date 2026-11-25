package com.factoryflow.invoicing;

import com.factoryflow.audit.AuditService;
import com.factoryflow.common.BusinessException;
import com.factoryflow.common.GstCalculator;
import com.factoryflow.common.NumberToWordsConverter;
import com.factoryflow.customers.Customer;
import com.factoryflow.inventory.InventoryService;
import com.factoryflow.sales.SalesOrder;
import com.factoryflow.sales.SalesOrderItem;
import com.factoryflow.sales.SalesOrderRepository;
import com.factoryflow.users.User;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import jakarta.validation.constraints.NotNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class InvoiceService {

    private final InvoiceRepository invoiceRepo;
    private final InvoiceSequenceRepository seqRepo;
    private final SalesOrderRepository orderRepo;
    private final CreditNoteRepository creditNoteRepo;
    private final InventoryService inventoryService;
    private final AuditService auditService;

    @Value("${app.factory.gstin:33AABCF1234A1Z5}")
    private String factoryGstin = "33AABCF1234A1Z5";

    @Value("${app.invoice.prefix:INV}")
    private String invoicePrefix = "INV";

    public InvoiceService(InvoiceRepository invoiceRepo, InvoiceSequenceRepository seqRepo,
                          SalesOrderRepository orderRepo, CreditNoteRepository creditNoteRepo,
                          InventoryService inventoryService, AuditService auditService) {
        this.invoiceRepo = invoiceRepo;
        this.seqRepo = seqRepo;
        this.orderRepo = orderRepo;
        this.creditNoteRepo = creditNoteRepo;
        this.inventoryService = inventoryService;
        this.auditService = auditService;
    }

    public List<InvoiceDto> getAll() {
        return invoiceRepo.findAllByOrderByCreatedAtDesc().stream().map(this::toDto).toList();
    }

    public InvoiceDto getById(UUID id) {
        return toDto(getEntity(id));
    }

    public List<InvoiceDto> getByCustomer(UUID customerId) {
        return invoiceRepo.findByCustomer_Id(customerId).stream().map(this::toDto).toList();
    }

    public Invoice getEntity(UUID id) {
        return invoiceRepo.findById(id)
                .orElseThrow(() -> new BusinessException("Invoice not found: " + id, HttpStatus.NOT_FOUND));
    }

    /**
     * Generate a GST invoice from an APPROVED sales order.
     * - Never auto-invoices unavailable FG quantity; checks available stock against requested qty.
     * - Concurrency-safe invoice numbering using PESSIMISTIC_WRITE lock on invoice_sequences.
     * - Rounding rule: round per line to 2 decimals, then sum.
     */
    @Transactional
    public InvoiceDto createInvoice(CreateInvoiceRequest req, User user) {
        SalesOrder order = orderRepo.findById(req.salesOrderId())
                .orElseThrow(() -> new BusinessException("Sales order not found", HttpStatus.NOT_FOUND));

        if (!"APPROVED".equals(order.getStatus())) {
            throw new BusinessException("Only APPROVED orders can be invoiced. Current status: " + order.getStatus(),
                    HttpStatus.CONFLICT);
        }

        LocalDate invDate = req.invoiceDate() != null ? req.invoiceDate() : LocalDate.now();
        String fy = getFinancialYear(invDate);

        // Concurrency-safe sequence increment
        InvoiceSequence seq = seqRepo.findByFinancialYearForUpdate(fy)
                .orElseGet(() -> {
                    InvoiceSequence s = new InvoiceSequence();
                    s.setFinancialYear(fy);
                    s.setLastSequence(0);
                    return seqRepo.save(s);
                });
        int nextSeq = seq.getLastSequence() + 1;
        seq.setLastSequence(nextSeq);
        seqRepo.save(seq);

        String invoiceNumber = String.format("%s/%s/%04d", invoicePrefix, fy, nextSeq);
        Customer customer = order.getCustomer();
        boolean interstate = GstCalculator.isInterstate(customer.getStateCode());

        Invoice invoice = new Invoice();
        invoice.setInvoiceNumber(invoiceNumber);
        invoice.setFinancialYear(fy);
        invoice.setSequenceNumber(nextSeq);
        invoice.setSalesOrder(order);
        invoice.setCustomer(customer);
        invoice.setInvoiceDate(invDate);
        invoice.setPlaceOfSupply(customer.getState() + " (" + customer.getStateCode() + ")");
        invoice.setSupplyStateCode(customer.getStateCode());
        invoice.setInterstate(interstate);
        invoice.setStatus("ACTIVE");
        invoice.setPaymentTerms(req.paymentTerms() != null ? req.paymentTerms()
                : customer.getPaymentTermsDays() + " days");
        invoice.setNotes(req.notes());
        invoice.setCreatedBy(user);

        BigDecimal totalTaxable = BigDecimal.ZERO;
        BigDecimal totalCgst = BigDecimal.ZERO;
        BigDecimal totalSgst = BigDecimal.ZERO;
        BigDecimal totalIgst = BigDecimal.ZERO;
        List<InvoiceItem> items = new ArrayList<>();

        for (SalesOrderItem soi : order.getItems()) {
            int qty = soi.getApprovedQuantity() != null ? soi.getApprovedQuantity() : soi.getOrderedQuantity();
            if (qty <= 0) continue;

            // Validate available FG stock so we never invoice unavailable quantity
            BigDecimal available = inventoryService.getCurrentStock(
                    "FINISHED_GOODS", null, null, soi.getProduct().getId());
            if (available.compareTo(BigDecimal.valueOf(qty)) < 0) {
                throw new BusinessException(
                        "Insufficient FG stock to invoice " + soi.getProduct().getName(),
                        "Available: " + available + ", Requested: " + qty + ". Adjust order quantity for partial fulfilment.",
                        HttpStatus.CONFLICT);
            }

            BigDecimal lineTaxable = soi.getUnitPrice().multiply(BigDecimal.valueOf(qty))
                    .setScale(2, RoundingMode.HALF_UP);
            var gst = GstCalculator.compute(lineTaxable, soi.getGstRate(), customer.getStateCode());

            InvoiceItem ii = new InvoiceItem();
            ii.setInvoice(invoice);
            ii.setProduct(soi.getProduct());
            ii.setSalesOrderItem(soi);
            ii.setHsnCode(soi.getProduct().getHsnCode());
            ii.setDescription(soi.getProduct().getName());
            ii.setQuantity(qty);
            ii.setUnitPrice(soi.getUnitPrice());
            ii.setTaxableAmount(lineTaxable);
            ii.setGstRate(soi.getGstRate());
            if (interstate) {
                ii.setIgstRate(soi.getGstRate());
                ii.setIgstAmount(gst.igst());
            } else {
                BigDecimal halfRate = soi.getGstRate().divide(new BigDecimal("2"), 2, RoundingMode.HALF_UP);
                ii.setCgstRate(halfRate);
                ii.setSgstRate(halfRate);
                ii.setCgstAmount(gst.cgst());
                ii.setSgstAmount(gst.sgst());
            }
            ii.setTotalAmount(gst.total());

            totalTaxable = totalTaxable.add(lineTaxable);
            totalCgst = totalCgst.add(gst.cgst());
            totalSgst = totalSgst.add(gst.sgst());
            totalIgst = totalIgst.add(gst.igst());
            items.add(ii);
        }

        if (items.isEmpty()) {
            throw new BusinessException("Cannot create invoice with zero quantity items");
        }

        BigDecimal grandTotal = totalTaxable.add(totalCgst).add(totalSgst).add(totalIgst);
        invoice.setItems(items);
        invoice.setTaxableAmount(totalTaxable);
        invoice.setCgstAmount(totalCgst);
        invoice.setSgstAmount(totalSgst);
        invoice.setIgstAmount(totalIgst);
        invoice.setTotalAmount(grandTotal);
        invoice.setAmountInWords(NumberToWordsConverter.convert(grandTotal));

        Invoice saved = invoiceRepo.save(invoice);

        order.setStatus("INVOICED");
        orderRepo.save(order);

        auditService.log(user, "INVOICE_CREATED", "INVOICE", saved.getId(),
                null, Map.of("invoiceNumber", saved.getInvoiceNumber(), "total", saved.getTotalAmount()),
                saved.getInvoiceNumber());

        return toDto(saved);
    }

    /**
     * Cancel an invoice ONLY via a Credit Note (never deleted).
     */
    @Transactional
    public CreditNoteDto issueCreditNote(UUID invoiceId, String reason, User user) {
        Invoice invoice = getEntity(invoiceId);
        if ("CANCELLED".equals(invoice.getStatus())) {
            throw new BusinessException("Invoice is already cancelled", HttpStatus.CONFLICT);
        }

        CreditNote cn = new CreditNote();
        cn.setCreditNoteNumber("CN/" + invoice.getFinancialYear() + "/" + String.format("%04d", invoice.getSequenceNumber()));
        cn.setInvoice(invoice);
        cn.setCustomer(invoice.getCustomer());
        cn.setCreditDate(LocalDate.now());
        cn.setReason(reason != null && !reason.isBlank() ? reason : "Invoice cancelled");
        cn.setAmount(invoice.getTotalAmount());
        cn.setCreatedBy(user);
        CreditNote savedCn = creditNoteRepo.save(cn);

        invoice.setStatus("CANCELLED");
        invoice.setCancelledBy(user);
        invoice.setCancelledAt(Instant.now());
        invoice.setCancellationReason(cn.getReason());
        invoiceRepo.save(invoice);

        auditService.log(user, "INVOICE_CANCELLED_CREDIT_NOTE", "INVOICE", invoice.getId(),
                Map.of("status", "ACTIVE"),
                Map.of("status", "CANCELLED", "creditNoteNumber", savedCn.getCreditNoteNumber()),
                invoice.getInvoiceNumber());

        return new CreditNoteDto(savedCn.getId(), savedCn.getCreditNoteNumber(), invoice.getId(),
                invoice.getInvoiceNumber(), savedCn.getCreditDate(), savedCn.getReason(), savedCn.getAmount());
    }

    public byte[] generatePdf(UUID invoiceId) {
        Invoice inv = getEntity(invoiceId);
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document doc = new Document(PageSize.A4, 36, 36, 36, 36);
            PdfWriter.getInstance(doc, out);
            doc.open();

            Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16);
            Font subFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10);
            Font bodyFont = FontFactory.getFont(FontFactory.HELVETICA, 9);

            Paragraph header = new Paragraph("TAX INVOICE (PROTOTYPE DOCUMENT)", titleFont);
            header.setAlignment(Element.ALIGN_CENTER);
            doc.add(header);

            doc.add(new Paragraph("Sri Murugan Snacks Pvt Ltd | Tamil Nadu | GSTIN: " + factoryGstin, subFont));
            doc.add(new Paragraph("Invoice No: " + inv.getInvoiceNumber()
                    + "   |   Date: " + inv.getInvoiceDate()
                    + "   |   FY: " + inv.getFinancialYear(), bodyFont));
            doc.add(new Paragraph("Place of Supply: " + inv.getPlaceOfSupply(), bodyFont));
            doc.add(new Paragraph(" "));

            doc.add(new Paragraph("Billed To: " + inv.getCustomer().getName(), subFont));
            doc.add(new Paragraph("GSTIN: " + (inv.getCustomer().getGstin() != null ? inv.getCustomer().getGstin() : "N/A")
                    + " | Address: " + (inv.getCustomer().getBillingAddress() != null ? inv.getCustomer().getBillingAddress() : ""), bodyFont));
            doc.add(new Paragraph(" "));

            PdfPTable table = new PdfPTable(7);
            table.setWidthPercentage(100);
            String[] cols = {"Description", "HSN", "Qty", "Rate", "Taxable", "GST", "Total"};
            for (String c : cols) {
                PdfPCell cell = new PdfPCell(new Phrase(c, subFont));
                cell.setBackgroundColor(new Color(235, 238, 242));
                cell.setPadding(5);
                table.addCell(cell);
            }

            for (InvoiceItem item : inv.getItems()) {
                BigDecimal gstAmt = item.getCgstAmount().add(item.getSgstAmount()).add(item.getIgstAmount());
                table.addCell(new Phrase(item.getDescription(), bodyFont));
                table.addCell(new Phrase(item.getHsnCode() != null ? item.getHsnCode() : "", bodyFont));
                table.addCell(new Phrase(String.valueOf(item.getQuantity()), bodyFont));
                table.addCell(new Phrase("Rs." + item.getUnitPrice(), bodyFont));
                table.addCell(new Phrase("Rs." + item.getTaxableAmount(), bodyFont));
                table.addCell(new Phrase("Rs." + gstAmt + " (" + item.getGstRate() + "%)", bodyFont));
                table.addCell(new Phrase("Rs." + item.getTotalAmount(), bodyFont));
            }
            doc.add(table);
            doc.add(new Paragraph(" "));

            doc.add(new Paragraph("Taxable Amount: Rs." + inv.getTaxableAmount(), bodyFont));
            if (inv.isInterstate()) {
                doc.add(new Paragraph("IGST: Rs." + inv.getIgstAmount(), bodyFont));
            } else {
                doc.add(new Paragraph("CGST: Rs." + inv.getCgstAmount() + "   |   SGST: Rs." + inv.getSgstAmount(), bodyFont));
            }
            doc.add(new Paragraph("Grand Total: Rs." + inv.getTotalAmount(), subFont));
            doc.add(new Paragraph("Amount in Words: " + inv.getAmountInWords(), bodyFont));
            doc.add(new Paragraph("Terms: " + (inv.getPaymentTerms() != null ? inv.getPaymentTerms() : "30 days"), bodyFont));
            doc.add(new Paragraph(" "));
            doc.add(new Paragraph("Note: Prototype document generated by FactoryFlow (not for live GST portal filing).", bodyFont));

            doc.close();
            return out.toByteArray();
        } catch (Exception e) {
            throw new BusinessException("Failed to generate PDF: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    public static String getFinancialYear(LocalDate date) {
        int year = date.getYear();
        int month = date.getMonthValue();
        if (month >= 4) {
            return year + "-" + String.valueOf(year + 1).substring(2);
        } else {
            return (year - 1) + "-" + String.valueOf(year).substring(2);
        }
    }

    public InvoiceDto toDto(Invoice i) {
        List<InvoiceItemDto> itemDtos = i.getItems().stream().map(item -> new InvoiceItemDto(
                item.getId(), item.getProduct().getId(), item.getProduct().getName(),
                item.getHsnCode(), item.getQuantity(), item.getUnitPrice(),
                item.getTaxableAmount(), item.getGstRate(),
                item.getCgstAmount(), item.getSgstAmount(), item.getIgstAmount(), item.getTotalAmount()
        )).toList();

        return new InvoiceDto(
                i.getId(), i.getInvoiceNumber(), i.getFinancialYear(),
                i.getSalesOrder() != null ? i.getSalesOrder().getId() : null,
                i.getCustomer().getId(), i.getCustomer().getName(),
                i.getCustomer().getGstin(), i.getCustomer().getBillingAddress(),
                i.getInvoiceDate(), i.getPlaceOfSupply(), i.isInterstate(),
                i.getStatus(), i.getTaxableAmount(), i.getCgstAmount(),
                i.getSgstAmount(), i.getIgstAmount(), i.getTotalAmount(),
                i.getAmountInWords(), i.getPaymentTerms(), itemDtos
        );
    }

    public record InvoiceDto(
            UUID id, String invoiceNumber, String financialYear, UUID salesOrderId,
            UUID customerId, String customerName, String customerGstin, String customerAddress,
            LocalDate invoiceDate, String placeOfSupply, boolean interstate, String status,
            BigDecimal taxableAmount, BigDecimal cgstAmount, BigDecimal sgstAmount,
            BigDecimal igstAmount, BigDecimal totalAmount, String amountInWords,
            String paymentTerms, List<InvoiceItemDto> items) {}

    public record InvoiceItemDto(
            UUID id, UUID productId, String productName, String hsnCode, int quantity,
            BigDecimal unitPrice, BigDecimal taxableAmount, BigDecimal gstRate,
            BigDecimal cgstAmount, BigDecimal sgstAmount, BigDecimal igstAmount, BigDecimal totalAmount) {}

    public record CreateInvoiceRequest(
            @NotNull UUID salesOrderId, LocalDate invoiceDate, String paymentTerms, String notes) {}

    public record CreditNoteDto(
            UUID id, String creditNoteNumber, UUID invoiceId, String invoiceNumber,
            LocalDate creditDate, String reason, BigDecimal amount) {}
}
