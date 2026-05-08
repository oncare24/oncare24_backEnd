package com.oncare.oncare24.inactivity.entity;

public enum InactivityAnalysisStatus {
    NORMAL,
    INACTIVE_WARNING,
    INACTIVE_DANGER,
    STALE_LOCATION_WARNING,
    STALE_LOCATION_DANGER,
    LOCATION_UNRELIABLE,
    DEVICE_DISCONNECTED
}
