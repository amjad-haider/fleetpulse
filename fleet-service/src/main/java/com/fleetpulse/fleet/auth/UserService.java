package com.fleetpulse.fleet.auth;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public List<UserResponse> listAll() {
        return userRepository.findAllByOrderByCreatedAtAsc().stream()
                .map(UserResponse::from)
                .toList();
    }

    @Transactional
    public UserResponse updateRole(UUID id, UserRole newRole) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException(id));

        boolean demotingAnAdmin = user.getRole() == UserRole.ADMIN && newRole != UserRole.ADMIN;
        if (demotingAnAdmin && userRepository.countByRole(UserRole.ADMIN) <= 1) {
            throw new CannotDemoteLastAdminException();
        }

        user.setRole(newRole);
        return UserResponse.from(userRepository.save(user));
    }
}
