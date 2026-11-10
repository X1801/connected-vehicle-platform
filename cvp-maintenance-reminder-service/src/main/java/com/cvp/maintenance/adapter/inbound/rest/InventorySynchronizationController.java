package com.cvp.maintenance.adapter.inbound.rest;
import com.cvp.maintenance.application.InventorySynchronizationService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/internal/v1/vehicles")
public class InventorySynchronizationController { private final InventorySynchronizationService service; public InventorySynchronizationController(InventorySynchronizationService service){this.service=service;} @PostMapping("/{vehicleId}/inventory-synchronizations") @ResponseStatus(HttpStatus.ACCEPTED) public void synchronize(@PathVariable String vehicleId){service.synchronize(vehicleId);} }
