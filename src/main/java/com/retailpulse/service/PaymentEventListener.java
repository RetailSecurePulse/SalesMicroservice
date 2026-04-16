package com.retailpulse.service;


import com.retailpulse.dto.PaymentEventDto;
import com.retailpulse.entity.TransactionStatus;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.ZoneId;
import java.util.logging.Logger;

/**
 * Kafka listener for processing payment-related events.
 */
@Component
@ConditionalOnProperty( 
    value = "spring.kafka.consumer.enabled", // The property key to check
    havingValue = "true",           // The value that enables the bean
    matchIfMissing = false           // If property is missing, don't create the bean
)
public class PaymentEventListener {

    private static final Logger logger = Logger.getLogger(PaymentEventListener.class.getName());

    private final SalesTransactionService salesTransactionService;

    // Inject the service that contains the logic to update the transaction
    public PaymentEventListener(SalesTransactionService salesTransactionService) {
        this.salesTransactionService = salesTransactionService;
    }

    /**
     * Listens for PaymentEventDto events on the 'payment-events' topic.
     *
     * @param paymentEvent The deserialized PaymentEventDto received from Kafka.
     */
    @KafkaListener(
        topics = "${spring.kafka.consumer.topics.payment}", // "payment-events",
        groupId = "${spring.kafka.consumer.group-ids.payment}" // Ensure this group ID is unique for the Sales service
        // containerFactory = "..." // Optional: specify if you created a custom container factory
    )
    public void handlePaymentEvent(PaymentEventDto paymentEvent) {
        logger.info(String.format("Received Payment Event: Payment ID '%s', Intent ID '%s', Transaction ID '%s', Status '%s'",
                paymentEvent.paymentId(),
                paymentEvent.paymentIntentId(),
                paymentEvent.transactionId(), 
                paymentEvent.paymentStatus()));

        // 1. Extract transaction ID (assuming it's a String, adapt if it's Long) and payment event date       
        Long transactionId = paymentEvent.transactionId();
        Instant paymentEventDate = null;
        if (paymentEvent.paymentEventDate() != null) {
          paymentEventDate = paymentEvent.paymentEventDate().atZone(ZoneId.of("Asia/Singapore")).toInstant();
        }
        else{
          paymentEventDate = java.time.Instant.now();
        }
        
        // 2. Map PaymentStatus to TransactionStatus
        TransactionStatus newTransactionStatus = mapPaymentStatusToTransactionStatus(paymentEvent.paymentStatus());

        if (newTransactionStatus == null) {
            logger.warning(String.format("Unknown or unmapped PaymentStatus '%s' received. Payment ID: '%s', Intent ID: '%s', Transaction ID: '%s'",
                    paymentEvent.paymentStatus(), paymentEvent.paymentId(), paymentEvent.paymentIntentId(), transactionId));
            // Consider sending to a DLQ or alerting
            return;
        }

        // 3. Delegate the status update to the SalesTransactionService
        try {
            salesTransactionService.updateTransactionStatus(transactionId, newTransactionStatus, paymentEventDate);
            logger.info(String.format("Successfully updated transaction ID '%s' status to '%s' based on payment event.",
                    transactionId, newTransactionStatus));
        } catch (Exception e) { // Catch specific exceptions from your service if possible (e.g., TransactionNotFoundException)
            logger.severe(String.format("Failed to update transaction ID '%s' status to '%s' based on payment event. Payment ID: '%s', Intent ID: '%s'",
                    transactionId, newTransactionStatus, paymentEvent.paymentId(), paymentEvent.paymentIntentId()));
            logger.severe(e.getMessage());
        }
    }

    /**
     * Maps the incoming PaymentStatus to the internal TransactionStatus.
     *
     * @param paymentStatus The status from the payment event.
     * @return The corresponding TransactionStatus, or null if unmapped.
     */
    private TransactionStatus mapPaymentStatusToTransactionStatus(com.retailpulse.entity.PaymentStatus paymentStatus) {
        if (paymentStatus == null) {
            return null;
        }
        return switch (paymentStatus) {
            case SUCCEEDED -> TransactionStatus.COMPLETED;
            case FAILED -> TransactionStatus.REJECTED; 
            case CANCELED -> TransactionStatus.CANCELLED;
            case PROCESSING -> TransactionStatus.PENDING_PAYMENT;             
        };
    }
}