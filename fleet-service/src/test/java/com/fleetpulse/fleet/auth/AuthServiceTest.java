package com.fleetpulse.fleet.auth;

import com.fleetpulse.fleet.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepository, passwordEncoder, jwtService);
    }

    @Test
    void registerCreatesFleetManagerWhenAnAdminAlreadyExists() {
        RegisterRequest request = new RegisterRequest("new.manager@fleetpulse.dev", "hunter2hunter2", "New Manager");
        when(userRepository.existsByEmail(request.email())).thenReturn(false);
        when(userRepository.existsByRole(UserRole.ADMIN)).thenReturn(true);
        when(passwordEncoder.encode(request.password())).thenReturn("hashed-password");
        when(jwtService.generateToken(any(User.class))).thenReturn("signed-token");
        when(jwtService.expiryOf("signed-token")).thenReturn(Instant.now().plusSeconds(3600));

        AuthResponse response = authService.register(request);

        assertThat(response.token()).isEqualTo("signed-token");
        assertThat(response.role()).isEqualTo(UserRole.FLEET_MANAGER);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getPasswordHash()).isEqualTo("hashed-password");
        assertThat(captor.getValue().getRole()).isEqualTo(UserRole.FLEET_MANAGER);
    }

    @Test
    void registerCreatesAdminWhenNoAdminExistsYet() {
        // bootstrap: the first person to register becomes ADMIN, since there's
        // no separate admin-provisioning flow
        RegisterRequest request = new RegisterRequest("first.user@fleetpulse.dev", "hunter2hunter2", "First User");
        when(userRepository.existsByEmail(request.email())).thenReturn(false);
        when(userRepository.existsByRole(UserRole.ADMIN)).thenReturn(false);
        when(passwordEncoder.encode(request.password())).thenReturn("hashed-password");
        when(jwtService.generateToken(any(User.class))).thenReturn("signed-token");
        when(jwtService.expiryOf("signed-token")).thenReturn(Instant.now().plusSeconds(3600));

        AuthResponse response = authService.register(request);

        assertThat(response.role()).isEqualTo(UserRole.ADMIN);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getRole()).isEqualTo(UserRole.ADMIN);
    }

    @Test
    void registerRejectsDuplicateEmail() {
        RegisterRequest request = new RegisterRequest("taken@fleetpulse.dev", "hunter2hunter2", "Someone");
        when(userRepository.existsByEmail(request.email())).thenReturn(true);

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(EmailAlreadyRegisteredException.class);

        verify(userRepository, never()).save(any());
    }

    @Test
    void loginRejectsUnknownEmail() {
        LoginRequest request = new LoginRequest("ghost@fleetpulse.dev", "whatever1");
        when(userRepository.findByEmail(request.email())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void loginRejectsWrongPassword() {
        LoginRequest request = new LoginRequest("manager@fleetpulse.dev", "wrong-password");
        User user = User.builder()
                .email(request.email())
                .passwordHash("hashed-correct-password")
                .fullName("Manager")
                .role(UserRole.FLEET_MANAGER)
                .build();
        when(userRepository.findByEmail(request.email())).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(eq(request.password()), anyString())).thenReturn(false);

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void loginReturnsTokenOnCorrectPassword() {
        LoginRequest request = new LoginRequest("manager@fleetpulse.dev", "correct-password");
        User user = User.builder()
                .email(request.email())
                .passwordHash("hashed-correct-password")
                .fullName("Manager")
                .role(UserRole.ADMIN)
                .build();
        when(userRepository.findByEmail(request.email())).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(request.password(), user.getPasswordHash())).thenReturn(true);
        when(jwtService.generateToken(user)).thenReturn("signed-token");
        when(jwtService.expiryOf("signed-token")).thenReturn(Instant.now().plusSeconds(3600));

        AuthResponse response = authService.login(request);

        assertThat(response.token()).isEqualTo("signed-token");
        assertThat(response.role()).isEqualTo(UserRole.ADMIN);
    }
}
