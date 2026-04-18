package com.retailpulse.entity;

import com.retailpulse.dto.request.SalesDetailsDto;
import com.retailpulse.util.DateUtil;
import jakarta.persistence.*;
import lombok.Getter;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@Getter
@Entity
public class SalesTransaction {

    // Suspended transactions are never persisted, so they need a synthetic ID
    // that stays unique even when multiple suspends happen in the same millisecond.
    private static final AtomicLong MEMENTO_TRANSACTION_ID_SEQUENCE = new AtomicLong(System.currentTimeMillis());

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long businessEntityId;

    @ManyToOne
    @JoinColumn(name = "sales_tax_id")
    private SalesTax salesTax;

    private BigDecimal salesTaxAmount;

    private BigDecimal subtotal;

    private BigDecimal total;

    private TransactionStatus status;

    private Long paymentId;

    private String paymentIntentId;

    private Instant paymentEventDate;

    @Column(nullable = false)
    @CreationTimestamp
    private Instant transactionDate;

    @OneToMany(cascade = CascadeType.ALL, mappedBy = "salesTransaction", orphanRemoval = true)
    @MapKey(name = "productId")
    private Map<Long, SalesDetails> salesDetailEntities = new HashMap<>();

    protected SalesTransaction() {}

    public SalesTransaction(Long businessEntityId, SalesTax salesTax) {
        this.businessEntityId = businessEntityId;
        this.salesTax = salesTax;
        this.status = TransactionStatus.PENDING_PAYMENT;
    }

    public void addSalesDetails(Map<Long, SalesDetails> details) {
        this.salesDetailEntities = details.entrySet().stream()
            .peek(entry -> entry.getValue().setSalesTransaction(this))
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                Map.Entry::getValue
            ));
        recalculateTotal(); 
    }

    public void setStatus(TransactionStatus status) {
        this.status = status;
    }

    public void setPaymentId(Long paymentId) {
        this.paymentId = paymentId;
    }

    public void setPaymentIntentId(String paymentIntentId) {
        this.paymentIntentId = paymentIntentId;
    }

    public void setPaymentEventDate(Instant paymentEventDate) {
        this.paymentEventDate = paymentEventDate;
    }

    public void updateSalesDetails(Map<Long, SalesDetails> details) {
        this.salesDetailEntities.clear();
        this.addSalesDetails(details);
    }

    public SalesTransactionMemento saveToMemento() {
        return new SalesTransactionMemento(
                MEMENTO_TRANSACTION_ID_SEQUENCE.incrementAndGet(),
                this.businessEntityId,
                this.subtotal.toPlainString(),
                this.salesTax.getTaxType().name(),
                this.salesTax.getTaxRate().toPlainString(),
                this.salesTaxAmount.toPlainString(),
                this.total.toPlainString(),
                this.salesDetailEntities.values().stream().map(
                        salesDetails -> new SalesDetailsDto(
                                salesDetails.getProductId(),
                                salesDetails.getQuantity(),
                                salesDetails.getSalesPricePerUnit().toString()
                        )
                ).toList(),
                this.status.name(),
                DateUtil.convertInstantToString(Instant.now(), DateUtil.DATE_TIME_FORMAT)
        );
    }

    public SalesTransaction restoreFromMemento(SalesTransactionMemento memento) {
        this.id = memento.transactionId();
        this.businessEntityId = memento.businessEntityId();
        this.salesTax = new SalesTax(TaxType.valueOf(memento.taxType()), new BigDecimal(memento.taxRate()));
        this.subtotal = new BigDecimal(memento.subTotal());
        this.salesTaxAmount = new BigDecimal(memento.taxAmount());
        this.total = new BigDecimal(memento.totalAmount());
        this.transactionDate = DateUtil.convertStringToInstant(memento.transactionDateTime(), DateUtil.DATE_TIME_FORMAT);

        Map<Long, SalesDetails> restoredDetails = new HashMap<>();
        for (SalesDetailsDto dto : memento.salesDetails()) {
            SalesDetails detail = new SalesDetails(
                    dto.productId(),
                    dto.quantity(),
                    new BigDecimal(dto.salesPricePerUnit())
            );
            restoredDetails.put(dto.productId(), detail);           
        }
        this.addSalesDetails(restoredDetails);

        return this;
    }

    private void recalculateTotal() {
        BigDecimal subtotal = salesDetailEntities.values().stream()
                .map(SalesDetails::getSubTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
        this.subtotal = subtotal;
        this.salesTaxAmount = salesTax.calculateTax(subtotal);
        this.total = subtotal.add(salesTaxAmount).setScale(2, RoundingMode.HALF_UP);
    }
}
