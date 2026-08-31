package com.fleetpulse.alert.ingest;

/**
 * Mirrors health-engine's HealthDecision by name so it deserializes cleanly
 * off the wire, without alert-service depending on health-engine's proto.
 */
public enum AlertSeverity {
    OK,
    MONITOR,
    SERVICE_NOW
}
