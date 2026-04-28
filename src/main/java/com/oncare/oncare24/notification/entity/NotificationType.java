package com.oncare.oncare24.notification.entity;

/**
 * 알림 종류.
 * Step 8에서는 ZONE_EXIT, DEVICE_DISCONNECTED 두 종류만 사용.
 * SOS, MEDICATION_MISSED 등은 후속 Step에서 추가.
 */
public enum NotificationType {
    ZONE_EXIT,
    DEVICE_DISCONNECTED,
    SOS,
    MEDICATION_MISSED,
    INACTIVITY_WARNING
}