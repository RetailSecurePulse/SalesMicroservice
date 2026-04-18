package com.retailpulse.client;

import com.retailpulse.dto.request.InventoryUpdateRequestDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(
  name = "inventory-service", 
  url = "${inventory-service.url}",
  configuration = com.retailpulse.config.FeignConfig.class
  )
public interface InventoryServiceClient {    
    @PostMapping("/api/inventory/salesUpdate")
    void updateStocks(@RequestBody InventoryUpdateRequestDto request);
}