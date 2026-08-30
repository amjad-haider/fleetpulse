package com.fleetpulse.fleet.auth;

import com.fleetpulse.fleet.vehicle.VehicleRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class AuthFlowIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void vehicleEndpointsRejectRequestsWithoutAToken() {
        ResponseEntity<String> response = restTemplate.getForEntity("/api/v1/vehicles", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void registerThenUseTokenToRegisterAVehicle() {
        RegisterRequest registerRequest = new RegisterRequest(
                "ops.manager@fleetpulse.dev", "a-strong-password", "Ops Manager");
        ResponseEntity<AuthResponse> registerResponse =
                restTemplate.postForEntity("/api/v1/auth/register", registerRequest, AuthResponse.class);

        assertThat(registerResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String token = registerResponse.getBody().token();
        assertThat(token).isNotBlank();

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        VehicleRequest vehicleRequest = new VehicleRequest("1HGCM82633A004352", "Mercedes", "Actros", 2022, "KL-AB-999");
        HttpEntity<VehicleRequest> requestEntity = new HttpEntity<>(vehicleRequest, headers);

        ResponseEntity<String> vehicleResponse =
                restTemplate.postForEntity("/api/v1/vehicles", requestEntity, String.class);

        assertThat(vehicleResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    void loginWithWrongPasswordIsRejected() {
        RegisterRequest registerRequest = new RegisterRequest(
                "second.manager@fleetpulse.dev", "the-real-password", "Second Manager");
        restTemplate.postForEntity("/api/v1/auth/register", registerRequest, AuthResponse.class);

        LoginRequest badLogin = new LoginRequest("second.manager@fleetpulse.dev", "not-the-password");
        ResponseEntity<String> response = restTemplate.postForEntity("/api/v1/auth/login", badLogin, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
