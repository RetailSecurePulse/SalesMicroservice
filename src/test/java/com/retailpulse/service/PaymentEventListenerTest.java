package com.retailpulse.service;

import com.retailpulse.dto.PaymentEventDto;
import com.retailpulse.entity.PaymentStatus;
import com.retailpulse.entity.TransactionStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PaymentEventListenerTest {

    @Mock
    private SalesTransactionService salesTransactionService;

    @InjectMocks
    private PaymentEventListener paymentEventListener;

    @Test
    void handlePaymentEvent_mapsEachSupportedPaymentStatus() {
        List<PaymentEventDto> events = List.of(
                paymentEvent(PaymentStatus.SUCCEEDED, LocalDateTime.of(2026, 4, 18, 10, 30), 101L),
                paymentEvent(PaymentStatus.FAILED, LocalDateTime.of(2026, 4, 18, 10, 31), 102L),
                paymentEvent(PaymentStatus.CANCELED, LocalDateTime.of(2026, 4, 18, 10, 32), 103L),
                paymentEvent(PaymentStatus.PROCESSING, LocalDateTime.of(2026, 4, 18, 10, 33), 104L)
        );

        events.forEach(paymentEventListener::handlePaymentEvent);

        verify(salesTransactionService).updateTransactionStatus(
                eq(101L),
                eq(TransactionStatus.COMPLETED),
                eq(toSingaporeInstant(LocalDateTime.of(2026, 4, 18, 10, 30)))
        );
        verify(salesTransactionService).updateTransactionStatus(
                eq(102L),
                eq(TransactionStatus.REJECTED),
                eq(toSingaporeInstant(LocalDateTime.of(2026, 4, 18, 10, 31)))
        );
        verify(salesTransactionService).updateTransactionStatus(
                eq(103L),
                eq(TransactionStatus.CANCELLED),
                eq(toSingaporeInstant(LocalDateTime.of(2026, 4, 18, 10, 32)))
        );
        verify(salesTransactionService).updateTransactionStatus(
                eq(104L),
                eq(TransactionStatus.PENDING_PAYMENT),
                eq(toSingaporeInstant(LocalDateTime.of(2026, 4, 18, 10, 33)))
        );
    }

    @Test
    void handlePaymentEvent_ignoresNullPaymentStatus() {
        paymentEventListener.handlePaymentEvent(paymentEvent(null, LocalDateTime.of(2026, 4, 18, 10, 30), 101L));

        verify(salesTransactionService, never()).updateTransactionStatus(any(), any(), any());
    }

    @Test
    void handlePaymentEvent_usesNowWhenPaymentDateMissingAndSwallowsServiceFailure() {
        PaymentEventDto paymentEvent = paymentEvent(PaymentStatus.PROCESSING, null, 200L);
        doThrow(new RuntimeException("update failed"))
                .when(salesTransactionService)
                .updateTransactionStatus(eq(200L), eq(TransactionStatus.PENDING_PAYMENT), any(Instant.class));

        Instant before = Instant.now();
        assertDoesNotThrow(() -> paymentEventListener.handlePaymentEvent(paymentEvent));
        Instant after = Instant.now();

        ArgumentCaptor<Instant> instantCaptor = ArgumentCaptor.forClass(Instant.class);
        verify(salesTransactionService, times(1))
                .updateTransactionStatus(eq(200L), eq(TransactionStatus.PENDING_PAYMENT), instantCaptor.capture());
        assertNotNull(instantCaptor.getValue());
        assertFalse(instantCaptor.getValue().isBefore(before));
        assertFalse(instantCaptor.getValue().isAfter(after));
    }

    private PaymentEventDto paymentEvent(PaymentStatus paymentStatus, LocalDateTime paymentDate, Long transactionId) {
        return new PaymentEventDto(
                10L,
                "pi_123",
                transactionId,
                new BigDecimal("1308.00"),
                "SGD",
                "customer@example.com",
                paymentStatus,
                paymentDate
        );
    }

    private Instant toSingaporeInstant(LocalDateTime dateTime) {
        return dateTime.atZone(ZoneId.of("Asia/Singapore")).toInstant();
    }
}
