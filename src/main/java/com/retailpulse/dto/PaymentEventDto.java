package com.retailpulse.dto;

import com.retailpulse.entity.PaymentStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PaymentEventDto (
    Long paymentId,
    String paymentIntentId,
    Long transactionId,
    BigDecimal totalPrice,
    String currency,
    String customerEmail,
    PaymentStatus paymentStatus,
    LocalDateTime paymentEventDate
  ){}
