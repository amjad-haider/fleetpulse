package com.fleetpulse.fleet.vehicle;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(VehicleController.class)
class VehicleControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private VehicleService vehicleService;

    @Test
    void registerReturns201WithLocation() throws Exception {
        VehicleRequest request = new VehicleRequest("1HGCM82633A004352", "Mercedes", "Actros", 2022, "KL-AB-123");
        UUID id = UUID.randomUUID();
        VehicleResponse response = new VehicleResponse(id, request.vin(), request.make(), request.model(),
                request.year(), request.licensePlate(), VehicleStatus.ACTIVE, 0, Instant.now());
        when(vehicleService.register(any(VehicleRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/vehicles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.vin").value(request.vin()))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void registerRejectsInvalidVinLength() throws Exception {
        VehicleRequest request = new VehicleRequest("TOO-SHORT", "Mercedes", "Actros", 2022, "KL-AB-123");

        mockMvc.perform(post("/api/v1/vehicles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getByIdReturns404WhenMissing() throws Exception {
        UUID id = UUID.randomUUID();
        when(vehicleService.getById(eq(id))).thenThrow(new VehicleNotFoundException(id));

        mockMvc.perform(get("/api/v1/vehicles/" + id))
                .andExpect(status().isNotFound());
    }

    @Test
    void listReturnsAllVehicles() throws Exception {
        VehicleResponse response = new VehicleResponse(UUID.randomUUID(), "1HGCM82633A004352", "Mercedes",
                "Actros", 2022, "KL-AB-123", VehicleStatus.ACTIVE, 0, Instant.now());
        when(vehicleService.listAll()).thenReturn(List.of(response));

        mockMvc.perform(get("/api/v1/vehicles"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].vin").value("1HGCM82633A004352"));
    }
}
