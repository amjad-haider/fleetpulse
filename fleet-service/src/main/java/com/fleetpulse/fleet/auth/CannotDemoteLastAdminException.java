package com.fleetpulse.fleet.auth;

public class CannotDemoteLastAdminException extends RuntimeException {

    public CannotDemoteLastAdminException() {
        super("cannot change the role of the last remaining admin");
    }
}
