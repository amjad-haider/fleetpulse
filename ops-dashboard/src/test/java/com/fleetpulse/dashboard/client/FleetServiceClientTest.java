package com.fleetpulse.dashboard.client;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class FleetServiceClientTest {

    private MockRestServiceServer mockServer;
    private FleetServiceClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        mockServer = MockRestServiceServer.bindTo(builder).build();
        client = new FleetServiceClient(builder, "http://fleet-service.test");
    }

    @Test
    void loginReturnsTokenOnSuccess() {
        mockServer.expect(requestTo("http://fleet-service.test/api/v1/auth/login"))
                .andExpect(method(org.springframework.http.HttpMethod.POST))
                .andRespond(withSuccess(
                        """
                        {"token":"signed-token","expiresAt":"2026-01-01T00:00:00Z","role":"FLEET_MANAGER"}
                        """,
                        MediaType.APPLICATION_JSON));

        LoginResponse response = client.login("ops@fleetpulse.dev", "hunter2hunter2");

        assertThat(response.token()).isEqualTo("signed-token");
        assertThat(response.role()).isEqualTo("FLEET_MANAGER");
    }

    @Test
    void loginThrowsOnInvalidCredentials() {
        mockServer.expect(requestTo("http://fleet-service.test/api/v1/auth/login"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertThatThrownBy(() -> client.login("ops@fleetpulse.dev", "wrong-password"))
                .isInstanceOf(HttpClientErrorException.class);
    }

    @Test
    void listVehiclesSendsBearerTokenAndParsesTheList() {
        mockServer.expect(requestTo("http://fleet-service.test/api/v1/vehicles"))
                .andExpect(method(org.springframework.http.HttpMethod.GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer test-token"))
                .andRespond(withSuccess(
                        """
                        [{"id":"1","vin":"1HGCM82633A004352","make":"Mercedes","model":"Actros","year":2022,"licensePlate":"KL-AB-123","status":"ACTIVE","odometerKm":1000.0}]
                        """,
                        MediaType.APPLICATION_JSON));

        List<VehicleDto> vehicles = client.listVehicles("test-token");

        assertThat(vehicles).hasSize(1);
        assertThat(vehicles.get(0).vin()).isEqualTo("1HGCM82633A004352");
    }
}
