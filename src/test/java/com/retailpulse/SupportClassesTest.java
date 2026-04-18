package com.retailpulse;

import com.retailpulse.controller.ErrorResponse;
import com.retailpulse.dto.SalesTransactionDetailsDto;
import com.retailpulse.entity.SalesDetails;
import com.retailpulse.entity.SalesTax;
import com.retailpulse.entity.SalesTransaction;
import com.retailpulse.entity.TaxType;
import com.retailpulse.exception.ApplicationException;
import com.retailpulse.exception.BusinessException;
import com.retailpulse.exception.StockUpdateException;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.boot.SpringApplication;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mockStatic;

class SupportClassesTest {

    @Test
    void salesMicroserviceMain_delegatesToSpringApplication() {
        try (MockedStatic<SpringApplication> springApplication = mockStatic(SpringApplication.class)) {
            SalesMicroservice.main(new String[]{"--spring.main.web-application-type=none"});

            springApplication.verify(() -> SpringApplication.run(
                    SalesMicroservice.class,
                    new String[]{"--spring.main.web-application-type=none"}
            ));
        }
    }

    @Test
    void errorResponse_allowsReadingAndUpdatingFields() {
        ErrorResponse errorResponse = new ErrorResponse("ERR_001", "original");

        assertEquals("ERR_001", errorResponse.getCode());
        assertEquals("original", errorResponse.getMessage());

        errorResponse.setCode("ERR_002");
        errorResponse.setMessage("updated");

        assertEquals("ERR_002", errorResponse.getCode());
        assertEquals("updated", errorResponse.getMessage());
    }

    @Test
    void applicationAndBusinessExceptionsExposeCodesAndMessages() {
        ApplicationException applicationException = new ApplicationException("APP_001", "application message");
        BusinessException businessException = new BusinessException("BUS_001", "business message");

        assertEquals("APP_001", applicationException.getErrorCode());
        assertEquals("application message", applicationException.getMessage());
        assertEquals("BUS_001", businessException.getErrorCode());
        assertEquals("BUS_001", businessException.getCode());
        assertEquals("business message", businessException.getMessage());
    }

    @Test
    void stockUpdateException_supportsBothConstructors() {
        RuntimeException cause = new RuntimeException("root cause");
        StockUpdateException withMessageOnly = new StockUpdateException("inventory failure");
        StockUpdateException withCause = new StockUpdateException("inventory failure", cause);

        assertEquals("inventory failure", withMessageOnly.getMessage());
        assertEquals("inventory failure", withCause.getMessage());
        assertSame(cause, withCause.getCause());
    }

    @Test
    void salesTransactionDetailsDto_exposesStateAndMapThrowsUnsupportedOperationException() {
        SalesTransaction salesTransaction = new SalesTransaction(1L, new SalesTax(TaxType.GST, new BigDecimal("0.09")));
        List<SalesDetails> details = List.of(new SalesDetails(10L, 2, new BigDecimal("12.50")));
        SalesTransactionDetailsDto dto = new SalesTransactionDetailsDto(salesTransaction, details);

        assertSame(salesTransaction, dto.getSalesTransaction());
        assertEquals(details, dto.getDetails());
        assertThrows(UnsupportedOperationException.class, () -> dto.map(new Object()));
    }
}
