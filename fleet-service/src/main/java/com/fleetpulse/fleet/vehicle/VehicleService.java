package com.fleetpulse.fleet.vehicle;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class VehicleService {

    private final VehicleRepository vehicleRepository;

    public VehicleService(VehicleRepository vehicleRepository) {
        this.vehicleRepository = vehicleRepository;
    }

    @Transactional
    public VehicleResponse register(VehicleRequest request) {
        if (vehicleRepository.existsByVin(request.vin())) {
            throw new DuplicateVehicleException("vin already registered: " + request.vin());
        }
        if (vehicleRepository.existsByLicensePlate(request.licensePlate())) {
            throw new DuplicateVehicleException("license plate already registered: " + request.licensePlate());
        }

        Vehicle vehicle = Vehicle.builder()
                .vin(request.vin())
                .make(request.make())
                .model(request.model())
                .year(request.year())
                .licensePlate(request.licensePlate())
                .status(VehicleStatus.ACTIVE)
                .odometerKm(0)
                .build();

        return VehicleResponse.from(vehicleRepository.save(vehicle));
    }

    @Transactional(readOnly = true)
    public VehicleResponse getById(UUID id) {
        return VehicleResponse.from(findVehicleOrThrow(id));
    }

    @Transactional(readOnly = true)
    public List<VehicleResponse> listAll() {
        return vehicleRepository.findAll().stream()
                .map(VehicleResponse::from)
                .toList();
    }

    @Transactional
    public VehicleResponse updateStatus(UUID id, VehicleStatus status) {
        Vehicle vehicle = findVehicleOrThrow(id);
        vehicle.setStatus(status);
        return VehicleResponse.from(vehicleRepository.save(vehicle));
    }

    private Vehicle findVehicleOrThrow(UUID id) {
        return vehicleRepository.findById(id)
                .orElseThrow(() -> new VehicleNotFoundException(id));
    }
}
