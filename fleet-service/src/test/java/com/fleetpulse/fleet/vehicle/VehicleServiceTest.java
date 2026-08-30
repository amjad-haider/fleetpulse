package com.fleetpulse.fleet.vehicle;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VehicleServiceTest {

    @Mock
    private VehicleRepository vehicleRepository;

    private VehicleService vehicleService;

    @BeforeEach
    void setUp() {
        vehicleService = new VehicleService(vehicleRepository);
    }

    @Test
    void registerSavesNewVehicleWhenVinAndPlateAreFree() {
        VehicleRequest request = new VehicleRequest("1HGCM82633A004352", "Mercedes", "Actros", 2022, "KL-AB-123");
        when(vehicleRepository.existsByVin(request.vin())).thenReturn(false);
        when(vehicleRepository.existsByLicensePlate(request.licensePlate())).thenReturn(false);
        when(vehicleRepository.save(any(Vehicle.class))).thenAnswer(invocation -> {
            Vehicle vehicle = invocation.getArgument(0);
            vehicle.setId(UUID.randomUUID());
            return vehicle;
        });

        VehicleResponse response = vehicleService.register(request);

        assertThat(response.vin()).isEqualTo(request.vin());
        assertThat(response.status()).isEqualTo(VehicleStatus.ACTIVE);

        ArgumentCaptor<Vehicle> captor = ArgumentCaptor.forClass(Vehicle.class);
        verify(vehicleRepository).save(captor.capture());
        assertThat(captor.getValue().getMake()).isEqualTo("Mercedes");
    }

    @Test
    void registerRejectsDuplicateVin() {
        VehicleRequest request = new VehicleRequest("1HGCM82633A004352", "Mercedes", "Actros", 2022, "KL-AB-123");
        when(vehicleRepository.existsByVin(request.vin())).thenReturn(true);

        assertThatThrownBy(() -> vehicleService.register(request))
                .isInstanceOf(DuplicateVehicleException.class)
                .hasMessageContaining(request.vin());

        verify(vehicleRepository, never()).save(any());
    }

    @Test
    void getByIdThrowsWhenVehicleMissing() {
        UUID id = UUID.randomUUID();
        when(vehicleRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> vehicleService.getById(id))
                .isInstanceOf(VehicleNotFoundException.class);
    }

    @Test
    void updateStatusChangesAndPersists() {
        UUID id = UUID.randomUUID();
        Vehicle vehicle = Vehicle.builder()
                .id(id)
                .vin("1HGCM82633A004352")
                .make("Mercedes")
                .model("Actros")
                .year(2022)
                .licensePlate("KL-AB-123")
                .status(VehicleStatus.ACTIVE)
                .build();
        when(vehicleRepository.findById(id)).thenReturn(Optional.of(vehicle));
        when(vehicleRepository.save(any(Vehicle.class))).thenAnswer(invocation -> invocation.getArgument(0));

        VehicleResponse response = vehicleService.updateStatus(id, VehicleStatus.IN_MAINTENANCE);

        assertThat(response.status()).isEqualTo(VehicleStatus.IN_MAINTENANCE);
    }
}
