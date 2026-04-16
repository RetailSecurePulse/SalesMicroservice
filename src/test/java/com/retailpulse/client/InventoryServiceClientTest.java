package com.retailpulse.client;

import com.retailpulse.dto.request.InventoryUpdateRequestDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class InventoryServiceClientTest {

    @Mock
    private InventoryServiceClient inventoryServiceClient;

    @Test
    void shouldCallUpdateStockMethod() {
        // Given
        InventoryUpdateRequestDto.InventoryItem item = 
            new InventoryUpdateRequestDto.InventoryItem(1L, 5);
        InventoryUpdateRequestDto requestDto = new InventoryUpdateRequestDto(
            100L, List.of(item)
        );

        // When
        inventoryServiceClient.updateStocks(requestDto);

        // Then
        verify(inventoryServiceClient, times(1)).updateStocks(requestDto);
    }
}