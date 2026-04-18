package com.retailpulse.service;

import com.retailpulse.client.PaymentServiceClient;
import com.retailpulse.dto.request.SalesDetailsDto;
import com.retailpulse.dto.request.SuspendedTransactionDto;
import com.retailpulse.dto.response.TransientSalesTransactionDto;
import com.retailpulse.entity.SalesTax;
import com.retailpulse.entity.TaxType;
import com.retailpulse.repository.SalesTaxRepository;
import com.retailpulse.repository.SalesTransactionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class SalesTransactionMementoTest {

    @Mock
    private SalesTaxRepository salesTaxRepository;

    @Mock
    private SalesTransactionRepository salesTransactionRepository;

    @Mock
    private StockUpdateService stockUpdateService;

    @Mock
    private PaymentServiceClient paymentServiceClient;

    @Test
    public void testSalesTransactionMemento() {
        SalesDetailsDto salesDetailsDto1 = new SalesDetailsDto(1L, 2, "50.0");
        SalesDetailsDto salesDetailsDto2 = new SalesDetailsDto(2L, 3, "100.0");
        SalesDetailsDto salesDetailsDto3 = new SalesDetailsDto(3L, 4, "200.0");
        List<SalesDetailsDto> salesDetailsDtos1 = List.of(salesDetailsDto1, salesDetailsDto2, salesDetailsDto3);
        List<SalesDetailsDto> salesDetailsDtos2 = List.of(salesDetailsDto1, salesDetailsDto2);
        List<SalesDetailsDto> salesDetailsDtos3 = List.of(salesDetailsDto1);

        SuspendedTransactionDto suspendedTransactionDto1 = new SuspendedTransactionDto(1L, salesDetailsDtos1);
        SuspendedTransactionDto suspendedTransactionDto2 = new SuspendedTransactionDto(1L, salesDetailsDtos2);
        SuspendedTransactionDto suspendedTransactionDto3 = new SuspendedTransactionDto(1L, salesDetailsDtos3);

        when(salesTaxRepository.save(any(SalesTax.class))).thenReturn(new SalesTax(TaxType.GST, new BigDecimal("0.09")));

        SalesTransactionHistory salesTransactionHistory = new SalesTransactionHistory();
       
        SalesTransactionService salesTransactionService = new SalesTransactionService(salesTransactionRepository, salesTaxRepository, salesTransactionHistory, stockUpdateService, paymentServiceClient);

        salesTransactionService.suspendTransaction(suspendedTransactionDto1);
        salesTransactionService.suspendTransaction(suspendedTransactionDto2);
        List<TransientSalesTransactionDto> suspendedTransactions = salesTransactionService.suspendTransaction(suspendedTransactionDto3);

        assertEquals(3, suspendedTransactions.size());
        assertEquals(
                Set.of(1, 2, 3),
                suspendedTransactions.stream().map(transaction -> transaction.salesDetails().size()).collect(java.util.stream.Collectors.toSet())
        );
        assertEquals(
                3,
                suspendedTransactions.stream().map(TransientSalesTransactionDto::transactionId).distinct().count()
        );

        Long transactionIdToRestore = suspendedTransactions.stream()
                .filter(transaction -> transaction.salesDetails().size() == 1)
                .map(TransientSalesTransactionDto::transactionId)
                .findFirst()
                .orElse(null);

        assertNotNull(transactionIdToRestore);

        suspendedTransactions = salesTransactionService.restoreTransaction(1L, transactionIdToRestore);

        assertEquals(2, suspendedTransactions.size());
        assertEquals(
                Set.of(2, 3),
                suspendedTransactions.stream().map(transaction -> transaction.salesDetails().size()).collect(java.util.stream.Collectors.toSet())
        );
       
    }
}
