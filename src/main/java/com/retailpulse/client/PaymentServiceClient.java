package com.retailpulse.client;

import com.retailpulse.dto.request.PaymentRequestDto;
import com.retailpulse.dto.response.PaymentResponseDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(
  name = "payment-service", 
  url = "${payment-service.url}",
  configuration = com.retailpulse.config.FeignConfig.class
  )
public interface PaymentServiceClient {
    @PostMapping("/api/payments/create-payment-intent")
    PaymentResponseDto createPaymentIntent(@RequestBody PaymentRequestDto request);
}