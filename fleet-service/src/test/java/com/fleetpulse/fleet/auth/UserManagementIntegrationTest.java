package com.fleetpulse.fleet.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Seeds its own admin directly through the repository rather than relying on
 * "the first person to register becomes admin" — this test class may share a
 * Spring context (and H2 instance) with other @SpringBootTest classes in the
 * same run, so which registration is "first" isn't something to depend on.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class UserManagementIntegrationTest {

    private static final String ADMIN_EMAIL = "seed-admin@fleetpulse.dev";
    private static final String ADMIN_PASSWORD = "seed-admin-password-123";

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private String adminToken;

    @BeforeEach
    void seedAdmin() {
        userRepository.findByEmail(ADMIN_EMAIL).orElseGet(() -> userRepository.save(User.builder()
                .email(ADMIN_EMAIL)
                .passwordHash(passwordEncoder.encode(ADMIN_PASSWORD))
                .fullName("Seed Admin")
                .role(UserRole.ADMIN)
                .build()));

        LoginRequest loginRequest = new LoginRequest(ADMIN_EMAIL, ADMIN_PASSWORD);
        ResponseEntity<AuthResponse> loginResponse =
                restTemplate.postForEntity("/api/v1/auth/login", loginRequest, AuthResponse.class);
        adminToken = loginResponse.getBody().token();
    }

    private HttpHeaders withToken(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return headers;
    }

    private String registerAndGetToken(String email) {
        RegisterRequest request = new RegisterRequest(email, "a-strong-password-1", "Test User");
        ResponseEntity<AuthResponse> response =
                restTemplate.postForEntity("/api/v1/auth/register", request, AuthResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        // the admin already exists (seeded above), so this registration is
        // guaranteed to land as FLEET_MANAGER, not another ADMIN
        assertThat(response.getBody().role()).isEqualTo(UserRole.FLEET_MANAGER);
        return response.getBody().token();
    }

    @Test
    void adminCanListUsers() {
        ResponseEntity<UserResponse[]> response = restTemplate.exchange(
                "/api/v1/users", HttpMethod.GET, new HttpEntity<>(withToken(adminToken)), UserResponse[].class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).extracting(UserResponse::email).contains(ADMIN_EMAIL);
    }

    @Test
    void nonAdminIsForbiddenFromListingUsers() {
        String managerToken = registerAndGetToken("non-admin-list-test@fleetpulse.dev");

        ResponseEntity<String> response = restTemplate.exchange(
                "/api/v1/users", HttpMethod.GET, new HttpEntity<>(withToken(managerToken)), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void adminCanPromoteAFleetManagerToAdmin() {
        registerAndGetToken("promote-test@fleetpulse.dev");
        List<UserResponse> users = List.of(restTemplate.exchange(
                "/api/v1/users", HttpMethod.GET, new HttpEntity<>(withToken(adminToken)), UserResponse[].class)
                .getBody());
        UUID targetId = users.stream()
                .filter(u -> u.email().equals("promote-test@fleetpulse.dev"))
                .findFirst().orElseThrow().id();

        ResponseEntity<UserResponse> response = restTemplate.exchange(
                "/api/v1/users/" + targetId + "/role?role=ADMIN", HttpMethod.PATCH,
                new HttpEntity<>(withToken(adminToken)), UserResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().role()).isEqualTo(UserRole.ADMIN);
    }

    // the "can't demote the last admin" rule is covered by UserServiceTest
    // instead — this integration test class shares a database with other
    // @SpringBootTest classes in the same run, so "how many admins currently
    // exist" isn't something to build an assertion on here
}
