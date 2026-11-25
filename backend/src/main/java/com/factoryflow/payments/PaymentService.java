package com.factoryflow.payments;

import com.factoryflow.audit.AuditService;
import com.factoryflow.common.BusinessException;
import com.factoryflow.customers.Customer;
import com.factoryflow.customers.CustomerRepository;
import com.factoryflow.invoicing.CreditNoteRepository;
import com.factoryflow.invoicing.Invoice;
import com.factoryflow.invoicing.InvoiceRepository;
import com.factoryflow.users.User;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class PaymentService {

    private final PaymentRepository paymentRepo;
    private final PaymentAllocationRepository allocRepo;
    private final CustomerRepository customerRepo;
    private final InvoiceRepository invoiceRepo;
    private final CreditNoteRepository creditNoteRepo;
    private final AuditService auditService;

    public PaymentService(PaymentRepository paymentRepo, PaymentAllocationRepository allocRepo,
                          CustomerRepository customerRepo, InvoiceRepository invoiceRepo,
                          CreditNoteRepository creditNoteRepo, AuditService auditService) {
        this.paymentRepo = paymentRepo;
        this.allocRepo = allocRepo;
        this.customerRepo = customerRepo;
        this.invoiceRepo = invoiceRepo;
        this.creditNoteRepo = creditNoteRepo;
        this.auditService = auditService;
    }

    public List<PaymentDto> getAll() {
        return paymentRepo.findAllByOrderByCreatedAtDesc().stream().map(this::toDto).toList();
    }

    /**
     * Record a customer payment and allocate it to one or more active invoices.
     * If allocations are omitted, auto-allocates FIFO across oldest unpaid active invoices.
     */
    @Transactional
    public PaymentDto recordPayment(CreatePaymentRequest req, User user) {
        Customer customer = customerRepo.findById(req.customerId())
                .orElseThrow(() -> new BusinessException("Customer not found", HttpStatus.NOT_FOUND));

        if (req.amount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("Payment amount must be positive");
        }

        Payment payment = new Payment();
        payment.setReferenceNumber(req.referenceNumber() != null && !req.referenceNumber().isBlank()
                ? req.referenceNumber()
                : "PAY-" + System.currentTimeMillis());
        payment.setCustomer(customer);
        payment.setPaymentDate(req.paymentDate() != null ? req.paymentDate() : LocalDate.now());
        payment.setPaymentMethod(req.paymentMethod().toUpperCase());
        payment.setAmount(req.amount());
        payment.setBankReference(req.bankReference());
        payment.setNotes(req.notes());
        payment.setCreatedBy(user);

        List<PaymentAllocation> allocs = new ArrayList<>();
        if (req.allocations() != null && !req.allocations().isEmpty()) {
            BigDecimal sumAlloc = BigDecimal.ZERO;
            for (PaymentAllocationRequest ar : req.allocations()) {
                Invoice inv = invoiceRepo.findById(ar.invoiceId())
                        .orElseThrow(() -> new BusinessException("Invoice not found", HttpStatus.NOT_FOUND));
                PaymentAllocation pa = new PaymentAllocation();
                pa.setPayment(payment);
                pa.setInvoice(inv);
                pa.setAllocatedAmount(ar.amount());
                allocs.add(pa);
                sumAlloc = sumAlloc.add(ar.amount());
            }
            if (sumAlloc.compareTo(req.amount()) > 0) {
                throw new BusinessException("Allocated amount exceeds payment amount");
            }
        } else {
            // Auto-allocate to oldest unpaid active invoices for this customer
            BigDecimal remaining = req.amount();
            List<Invoice> activeInvoices = invoiceRepo.findActiveByCustomer(customer.getId());
            for (Invoice inv : activeInvoices) {
                if (remaining.compareTo(BigDecimal.ZERO) <= 0) break;
                BigDecimal alreadyPaid = allocRepo.sumAllocatedForInvoice(inv.getId());
                BigDecimal credited = creditNoteRepo.sumByInvoice(inv.getId());
                BigDecimal due = inv.getTotalAmount().subtract(alreadyPaid).subtract(credited);
                if (due.compareTo(BigDecimal.ZERO) > 0) {
                    BigDecimal allocAmt = remaining.min(due);
                    PaymentAllocation pa = new PaymentAllocation();
                    pa.setPayment(payment);
                    pa.setInvoice(inv);
                    pa.setAllocatedAmount(allocAmt);
                    allocs.add(pa);
                    remaining = remaining.subtract(allocAmt);
                }
            }
        }
        payment.setAllocations(allocs);

        Payment saved = paymentRepo.save(payment);
        auditService.log(user, "PAYMENT_RECORDED", "PAYMENT", saved.getId(),
                null, Map.of("reference", saved.getReferenceNumber(), "amount", saved.getAmount(),
                        "customer", customer.getName()),
                saved.getReferenceNumber());

        return toDto(saved);
    }

    public List<OutstandingDto> getOutstandingForCustomer(UUID customerId) {
        return invoiceRepo.findActiveByCustomer(customerId).stream()
                .map(this::toOutstandingDto)
                .filter(o -> o.outstanding().compareTo(BigDecimal.ZERO) > 0)
                .toList();
    }

    public List<OutstandingDto> getAllOutstanding() {
        return invoiceRepo.findByStatus("ACTIVE").stream()
                .map(this::toOutstandingDto)
                .filter(o -> o.outstanding().compareTo(BigDecimal.ZERO) > 0)
                .toList();
    }

    private OutstandingDto toOutstandingDto(Invoice inv) {
        BigDecimal paid = allocRepo.sumAllocatedForInvoice(inv.getId());
        BigDecimal credited = creditNoteRepo.sumByInvoice(inv.getId());
        BigDecimal outstanding = inv.getTotalAmount().subtract(paid).subtract(credited);
        long days = ChronoUnit.DAYS.between(inv.getInvoiceDate(), LocalDate.now());
        if (days < 0) days = 0;
        String bucket = days <= 30 ? "0-30"
                : days <= 60 ? "31-60"
                : days <= 90 ? "61-90"
                : "90+";
        boolean overdue = days > inv.getCustomer().getPaymentTermsDays();

        return new OutstandingDto(
                inv.getCustomer().getId(), inv.getCustomer().getName(),
                inv.getId(), inv.getInvoiceNumber(), inv.getInvoiceDate(),
                inv.getTotalAmount(), paid, outstanding, days, bucket, overdue
        );
    }

    public PaymentDto toDto(Payment p) {
        return new PaymentDto(
                p.getId(), p.getReferenceNumber(), p.getCustomer().getId(), p.getCustomer().getName(),
                p.getPaymentDate(), p.getPaymentMethod(), p.getAmount(), p.getBankReference(), p.getNotes()
        );
    }

    public record PaymentDto(UUID id, String referenceNumber, UUID customerId, String customerName,
                             LocalDate paymentDate, String paymentMethod, BigDecimal amount,
                             String bankReference, String notes) {}

    public record CreatePaymentRequest(
            @NotNull UUID customerId, LocalDate paymentDate, @NotBlank String paymentMethod,
            @NotNull BigDecimal amount, String referenceNumber, String bankReference, String notes,
            List<PaymentAllocationRequest> allocations) {}

    public record PaymentAllocationRequest(@NotNull UUID invoiceId, @NotNull BigDecimal amount) {}

    public record OutstandingDto(
            UUID customerId, String customerName, UUID invoiceId, String invoiceNumber,
            LocalDate invoiceDate, BigDecimal invoiceTotal, BigDecimal paidAmount,
            BigDecimal outstanding, long daysSinceInvoice, String agingBucket, boolean overdue) {}
}
