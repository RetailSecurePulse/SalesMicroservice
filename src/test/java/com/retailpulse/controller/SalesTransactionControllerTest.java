package com.retailpulse.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.retailpulse.dto.request.SalesDetailsDto;
import com.retailpulse.dto.request.SalesTransactionRequestDto;
import com.retailpulse.dto.response.CreateTransactionResponseDto;
import com.retailpulse.dto.response.PaymentResponseDto;
import com.retailpulse.dto.response.SalesTransactionResponseDto;
import com.retailpulse.dto.response.TaxResultDto;
import com.retailpulse.entity.PaymentStatus;
import com.retailpulse.entity.TaxType;
import com.retailpulse.service.SalesTransactionService;
import com.retailpulse.util.DateUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
public class SalesTransactionControllerTest {

    private MockMvc mockMvc;

    @Mock
    private SalesTransactionService salesTransactionService;

    @InjectMocks
    private SalesTransactionController salesTransactionController;

    private final ObjectMapper objectMapper = new ObjectMapper();

    SalesTransactionRequestDto salesTransactionRequestDto;

    @BeforeEach
    public void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(salesTransactionController).build();
        SalesDetailsDto salesDetailsDto1 = new SalesDetailsDto(1L, 2, "50.0");
        SalesDetailsDto salesDetailsDto2 = new SalesDetailsDto(2L, 3, "100.0");
        SalesDetailsDto salesDetailsDto3 = new SalesDetailsDto(3L, 4, "200.0");
        List<SalesDetailsDto> salesDetailsDtos = List.of(salesDetailsDto1, salesDetailsDto2, salesDetailsDto3);
        salesTransactionRequestDto = new SalesTransactionRequestDto(
                1L, "108.000", "1308.000", salesDetailsDtos
        );
    }

    @Test
    public void testCalculateSalesTax() throws Exception {
        // Given
        TaxResultDto taxResultDto = new TaxResultDto(
                "1200.00",
                "GST",
                "0.09",
                "108.00",
                "1308.00", salesTransactionRequestDto.salesDetails());
        when(salesTransactionService.calculateSalesTax(ArgumentMatchers.anyList())).thenReturn(taxResultDto);

        // When & Then
        mockMvc.perform(post("/api/sales/calculateSalesTax")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(salesTransactionRequestDto.salesDetails())))
                .andExpect(status().isOk());
    }

    @Test
    public void testCreateSalesTransaction() throws Exception {
        SalesTransactionResponseDto transactionResponseDto = new SalesTransactionResponseDto(
                1L,
                1L,
                "1200.00",
                TaxType.GST.name(),
                "0.09",
                "108.00",
                "1308.00",
                salesTransactionRequestDto.salesDetails(),
                DateUtil.convertInstantToString(Instant.now(), DateUtil.DATE_TIME_FORMAT)
        );
        PaymentResponseDto paymentResponseDto = new PaymentResponseDto(
                "client_secret_123",
                "pi_123",
                10L,
                1L,
                1308.00,
                "SGD",
                PaymentStatus.PROCESSING,
                null
        );
        CreateTransactionResponseDto responseDto = new CreateTransactionResponseDto(
                transactionResponseDto,
                paymentResponseDto
        );
        when(salesTransactionService.createSalesTransaction(ArgumentMatchers.any()))
                .thenReturn(responseDto);

        mockMvc.perform(post("/api/sales/createTransaction")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(salesTransactionRequestDto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transaction.businessEntityId").value(1))
                .andExpect(jsonPath("$.transaction.totalAmount").value("1308.00"))
                .andExpect(jsonPath("$.paymentIntent.paymentIntentId").value("pi_123"))
                .andExpect(jsonPath("$.paymentIntent.paymentStatus").value(PaymentStatus.PROCESSING.name()));
    }

    @Test
    public void testUpdateSalesTransaction() throws Exception {
        // Given
        SalesTransactionResponseDto responseDto = new SalesTransactionResponseDto(
                1L,
                1L,
                "1200.0",
                TaxType.GST.name(),
                "0.09",
                "108.000",
                "1308.000",
                salesTransactionRequestDto.salesDetails(),
                DateUtil.convertInstantToString(Instant.now(), DateUtil.DATE_TIME_FORMAT)
        );
        when(salesTransactionService.updateSalesTransaction(ArgumentMatchers.eq(5L), ArgumentMatchers.anyList()))
                .thenReturn(responseDto);

        // When & Then
        mockMvc.perform(put("/api/sales/updateSalesTransaction/5")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(salesTransactionRequestDto.salesDetails())))
                .andExpect(status().isOk());
    }

}
