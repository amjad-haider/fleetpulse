package com.fleetpulse.fleet.auth;

import java.util.UUID;

public class UserNotFoundException extends RuntimeException {

    public UserNotFoundException(UUID id) {
        super("user not found: " + id);
    }
}
