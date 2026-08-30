package com.fleetpulse.fleet.vehicle;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/vehicles")
public class VehicleController {

    private final VehicleService vehicleService;

    public VehicleController(VehicleService vehicleService) {
        this.vehicleService = vehicleService;
    }

    @PostMapping
    public ResponseEntity<VehicleResponse> register(@Valid @RequestBody VehicleRequest request) {
        VehicleResponse response = vehicleService.register(request);
        return ResponseEntity.created(URI.create("/api/v1/vehicles/" + response.id())).body(response);
    }

    @GetMapping("/{id}")
    public VehicleResponse getById(@PathVariable("id") UUID id) {
        return vehicleService.getById(id);
    }

    @GetMapping
    public List<VehicleResponse> list() {
        return vehicleService.listAll();
    }

    @PatchMapping("/{id}/status")
    public VehicleResponse updateStatus(@PathVariable("id") UUID id, @RequestParam("status") VehicleStatus status) {
        return vehicleService.updateStatus(id, status);
    }

    @ExceptionHandler(VehicleNotFoundException.class)
    public ResponseEntity<String> handleNotFound(VehicleNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ex.getMessage());
    }

    @ExceptionHandler(DuplicateVehicleException.class)
    public ResponseEntity<String> handleDuplicate(DuplicateVehicleException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ex.getMessage());
    }
}
