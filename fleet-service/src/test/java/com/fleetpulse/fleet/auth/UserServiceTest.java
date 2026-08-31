package com.fleetpulse.fleet.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    private UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService(userRepository);
    }

    private User adminUser(UUID id) {
        return User.builder().id(id).email("admin@fleetpulse.dev").fullName("Admin").role(UserRole.ADMIN).build();
    }

    @Test
    void updateRoleThrowsWhenUserDoesNotExist() {
        UUID id = UUID.randomUUID();
        when(userRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.updateRole(id, UserRole.ADMIN))
                .isInstanceOf(UserNotFoundException.class);
    }

    @Test
    void canDemoteAnAdminWhenAnotherAdminStillExists() {
        UUID id = UUID.randomUUID();
        when(userRepository.findById(id)).thenReturn(Optional.of(adminUser(id)));
        when(userRepository.countByRole(UserRole.ADMIN)).thenReturn(2L);
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        UserResponse response = userService.updateRole(id, UserRole.FLEET_MANAGER);

        assertThat(response.role()).isEqualTo(UserRole.FLEET_MANAGER);
    }

    @Test
    void cannotDemoteTheLastAdmin() {
        UUID id = UUID.randomUUID();
        when(userRepository.findById(id)).thenReturn(Optional.of(adminUser(id)));
        when(userRepository.countByRole(UserRole.ADMIN)).thenReturn(1L);

        assertThatThrownBy(() -> userService.updateRole(id, UserRole.FLEET_MANAGER))
                .isInstanceOf(CannotDemoteLastAdminException.class);

        verify(userRepository, never()).save(any());
    }

    @Test
    void promotingAFleetManagerNeverTriggersTheLastAdminCheck() {
        UUID id = UUID.randomUUID();
        User manager = User.builder().id(id).email("manager@fleetpulse.dev").fullName("Manager").role(UserRole.FLEET_MANAGER).build();
        when(userRepository.findById(id)).thenReturn(Optional.of(manager));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        UserResponse response = userService.updateRole(id, UserRole.ADMIN);

        assertThat(response.role()).isEqualTo(UserRole.ADMIN);
        verify(userRepository, never()).countByRole(any());
    }
}
