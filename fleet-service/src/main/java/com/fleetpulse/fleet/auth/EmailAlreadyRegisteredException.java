package com.fleetpulse.fleet.auth;

public class EmailAlreadyRegisteredException extends RuntimeException {

    public EmailAlreadyRegisteredException(String email) {
        super("email already registered: " + email);
    }
}
