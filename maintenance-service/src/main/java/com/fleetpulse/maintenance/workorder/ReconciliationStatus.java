package com.fleetpulse.maintenance.workorder;

public enum ReconciliationStatus {
    /** a completed work order followed the alert within the response window */
    RESOLVED,
    /** the response window has passed with no completed work order */
    OVERDUE,
    /** still inside the response window, no completed work order yet */
    PENDING
}
