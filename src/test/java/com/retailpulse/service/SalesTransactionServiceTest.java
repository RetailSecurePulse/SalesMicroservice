package com.retailpulse.service;

import com.retailpulse.client.PaymentServiceClient;
import com.retailpulse.dto.request.PaymentRequestDto;
import com.retailpulse.dto.request.SalesDetailsDto;
import com.retailpulse.dto.request.SalesTransactionRequestDto;
import com.retailpulse.dto.request.SuspendedTransactionDto;
import com.retailpulse.dto.response.*;
import com.retailpulse.entity.*;
import com.retailpulse.exception.BusinessException;
import com.retailpulse.repository.SalesTaxRepository;
import com.retailpulse.repository.SalesTransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class SalesTransactionServiceTest {

  @Mock
  private SalesTransactionRepository salesTransactionRepository;

  @Mock
  private SalesTaxRepository salesTaxRepository;

  @Mock
  private StockUpdateService stockUpdateService;

  @Mock
  private PaymentServiceClient paymentServiceClient;

  @Mock
  private SalesTransactionHistory salesTransactionHistory;

  @InjectMocks
  private SalesTransactionService salesTransactionService;

  SalesTransactionRequestDto salesTransactionRequestDto;
  List<SalesDetailsDto> salesDetailsDtos;
  SalesTransaction dummySalesTransaction;
  SalesTax dummySalesTax;
  
  private final Long testTransactionId = 1L;
  private final TransactionStatus initialStatus = TransactionStatus.PENDING_PAYMENT;
  private final TransactionStatus newStatus = TransactionStatus.COMPLETED;

  @BeforeEach
  public void setUp() {
    SalesDetailsDto dto1 = new SalesDetailsDto(1L, 2, "50.0");
    SalesDetailsDto dto2 = new SalesDetailsDto(2L, 3, "100.0");
    SalesDetailsDto dto3 = new SalesDetailsDto(3L, 4, "200.0");
    salesDetailsDtos = List.of(dto1, dto2, dto3);
    salesTransactionRequestDto = new SalesTransactionRequestDto(1L, "108.00", "1308.00", salesDetailsDtos);

    dummySalesTax = new SalesTax(TaxType.GST, new BigDecimal("0.09"));
    
    Map<Long, SalesDetails> salesDetails = salesDetailsDtos.stream()
      .collect(Collectors.toMap(
        SalesDetailsDto::productId,
        dto -> new SalesDetails(dto.productId(), dto.quantity(), new BigDecimal(dto.salesPricePerUnit())),
        (_, replacement) -> replacement
      ));

    dummySalesTransaction = new SalesTransaction(1L, dummySalesTax);
    dummySalesTransaction.addSalesDetails(salesDetails);
    
    // Manually set the ID to simulate a persisted entity
    setPrivateField(dummySalesTransaction, "id", testTransactionId);
    setPrivateField(dummySalesTransaction, "transactionDate", Instant.now());
    setPrivateField(dummySalesTransaction, "paymentId", 1L);
    setPrivateField(dummySalesTransaction, "paymentIntentId", "pi_123");    
    setPrivateField(dummySalesTransaction, "status", initialStatus);
  }

  @Test
  public void testCalculateSalesTax() {
    when(salesTaxRepository.save(any(SalesTax.class))).thenReturn(dummySalesTax);

    TaxResultDto result = salesTransactionService.calculateSalesTax(salesDetailsDtos);

    assertEquals("GST", result.taxType());
    assertEquals("0.09", result.taxRate());
    assertEquals("1200.00", result.subTotalAmount());
    assertEquals("108.00", result.taxAmount());
    assertEquals("1308.00", result.totalAmount());
  }

  @Test
  public void testCreateSalesTransaction_success() {
    when(salesTaxRepository.findSalesTaxByTaxType(TaxType.GST)).thenReturn(Optional.of(dummySalesTax));
    when(salesTransactionRepository.save(any(SalesTransaction.class)))
    .thenAnswer(invocation -> {
      SalesTransaction savedTransaction = invocation.getArgument(0);
      // Sanity check if needed (optional)
      assertNotNull(savedTransaction, "Transaction passed to saveAndFlush should not be null");
      if (savedTransaction.getId() == null) {                
          setPrivateField(savedTransaction, "id", testTransactionId);
      }
      if (savedTransaction.getTransactionDate() == null) {
          setPrivateField(savedTransaction, "transactionDate", Instant.now());
      }
      if (savedTransaction.getPaymentIntentId() == null) {
          setPrivateField(savedTransaction, "paymentIntentId", "pi_123");
      }
      return savedTransaction; // Return the object that was passed in
    });
    
    PaymentResponseDto mockPaymentResponse = new PaymentResponseDto(
      "mock_client_secret_abc123", // clientSecret
      "pi_mxyz",// paymentIntentId
      1L,                       // paymentId (example)
      1L,                       // transactionId
      1308.00,                 // totalPrice
      "SGD",                    // currency
      PaymentStatus.PROCESSING,    // paymentStatus
      null  
    );
    when(paymentServiceClient.createPaymentIntent(any(PaymentRequestDto.class)))
      .thenReturn(mockPaymentResponse);
      
    assertNotNull(dummySalesTransaction, "dummySalesTransaction must be initialized before the test");

    CreateTransactionResponseDto response = salesTransactionService.createSalesTransaction(salesTransactionRequestDto);

    assertEquals(1L, response.transaction().businessEntityId());
    assertEquals("1200.00", response.transaction().subTotalAmount());
    assertEquals("108.00", response.transaction().taxAmount());
    assertEquals("1308.00", response.transaction().totalAmount());
    
    verify(stockUpdateService, times(1)).updateStocks(eq(1L), any());
    verify(salesTransactionRepository, times(2)).save(any(SalesTransaction.class));
    verify(paymentServiceClient, times(1)).createPaymentIntent(any(PaymentRequestDto.class));
  }

  @Test
  void updateTransactionStatus_Success() {
    // Arrange
    when(salesTransactionRepository.findById(testTransactionId)).thenReturn(Optional.of(dummySalesTransaction));
    when(salesTransactionRepository.saveAndFlush(any(SalesTransaction.class))).thenAnswer(invocation -> invocation.getArgument(0)); // Return the saved object
    Instant paymentEventDate = Instant.now();

    // Act
    assertDoesNotThrow(() -> salesTransactionService.updateTransactionStatus(testTransactionId, newStatus, paymentEventDate));

    // Assert
    assertEquals(newStatus, dummySalesTransaction.getStatus(), "Transaction status should be updated.");
    verify(salesTransactionRepository, times(1)).findById(testTransactionId);
    verify(salesTransactionRepository, times(1)).saveAndFlush(dummySalesTransaction); // Verify save was called
  }

  @Test
  void updateTransactionStatus_TransactionNotFound_ThrowsBusinessException() {
    // Arrange
    Long nonExistentId = 999L;
    Instant paymentEventDate = Instant.now();
    when(salesTransactionRepository.findById(nonExistentId)).thenReturn(Optional.empty());

    // Act & Assert
    BusinessException exception = assertThrows(BusinessException.class, () -> salesTransactionService.updateTransactionStatus(nonExistentId, newStatus, paymentEventDate));

    assertEquals("NOT_FOUND", exception.getErrorCode(), "Error code should be NOT_FOUND.");    
    assertTrue(exception.getMessage().contains("Sales transaction not found for id: " + nonExistentId), "Message should contain transaction ID.");
    verify(salesTransactionRepository, times(1)).findById(nonExistentId);
    verify(salesTransactionRepository, never()).save(any(SalesTransaction.class)); // Verify save was NOT called
  }

  @Test
  public void testUpdateSalesTransaction_success() {
    when(salesTransactionRepository.findById(any())).thenReturn(Optional.of(dummySalesTransaction));
    when(salesTransactionRepository.saveAndFlush(any())).thenReturn(dummySalesTransaction);

    SalesDetailsDto updatedDto1 = new SalesDetailsDto(1L, 3, "50.0");
    SalesDetailsDto updatedDto2 = new SalesDetailsDto(2L, 0, "100.0");
    SalesDetailsDto newDto = new SalesDetailsDto(4L, 2, "150.0");
    List<SalesDetailsDto> updatedDtos = List.of(updatedDto1, updatedDto2, newDto);

    SalesTransactionResponseDto response = salesTransactionService.updateSalesTransaction(1L, updatedDtos);

    assertEquals(1L, response.businessEntityId());
    assertEquals(3, response.salesDetails().size());

    verify(stockUpdateService, times(1)).updateStocks(eq(1L), any());
    verify(salesTransactionRepository, times(1)).saveAndFlush(any(SalesTransaction.class));
  }

  @Test
  public void testUpdateSalesTransaction_emptyInput_throwsException() {
    BusinessException ex = assertThrows(BusinessException.class, () ->
      salesTransactionService.updateSalesTransaction(1L, List.of())
    );
    assertEquals("EMPTY_UPDATE", ex.getErrorCode());
  }

  @Test
  public void testSuspendTransaction_success() {
    when(salesTaxRepository.findSalesTaxByTaxType(TaxType.GST)).thenReturn(Optional.of(dummySalesTax));

    SalesTransactionMemento memento = dummySalesTransaction.saveToMemento();
    Map<Long, SalesTransactionMemento> historyMap = Map.of(1L, memento);
    when(salesTransactionHistory.addTransaction(eq(1L), any())).thenReturn(historyMap);

    SuspendedTransactionDto suspendedDto = new SuspendedTransactionDto(1L, salesDetailsDtos);
    List<TransientSalesTransactionDto> result = salesTransactionService.suspendTransaction(suspendedDto);

    assertEquals(1, result.size());
    assertEquals("GST", result.getFirst().taxType());
    assertEquals("1308.00", result.getFirst().totalAmount());

    verify(salesTransactionHistory, times(1)).addTransaction(eq(1L), any());
  }

  @Test
  public void testRestoreTransaction_success() {
    SalesTransactionMemento remainingMemento = dummySalesTransaction.saveToMemento();
    SalesTransaction transactionToRestore = new SalesTransaction(1L, dummySalesTax);
    transactionToRestore.addSalesDetails(Map.of(4L, new SalesDetails(4L, 1, new BigDecimal("25.00"))));
    SalesTransactionMemento restoredMemento = transactionToRestore.saveToMemento();
    Map<Long, SalesTransactionMemento> remainingHistoryMap = Map.of(remainingMemento.transactionId(), remainingMemento);
    when(salesTransactionHistory.deleteTransaction(1L, restoredMemento.transactionId())).thenReturn(remainingHistoryMap);

    List<TransientSalesTransactionDto> result = salesTransactionService.restoreTransaction(1L, restoredMemento.transactionId());

    assertEquals(1, result.size());
    assertEquals("GST", result.getFirst().taxType());
    assertEquals("1308.00", result.getFirst().totalAmount());
    assertEquals(remainingMemento.transactionId(), result.getFirst().transactionId());

    verify(salesTransactionHistory, times(1)).deleteTransaction(1L, restoredMemento.transactionId());
  }

  private <T, V> void setPrivateField(T targetObject, String fieldName, V value) {
    try {
      Field field = targetObject.getClass().getDeclaredField(fieldName);
      field.setAccessible(true);
      field.set(targetObject, value);
    } catch (NoSuchFieldException | IllegalAccessException e) {
      throw new RuntimeException("Failed to set field '" + fieldName + "' on " + targetObject.getClass().getSimpleName(), e);
    }
  }
}
