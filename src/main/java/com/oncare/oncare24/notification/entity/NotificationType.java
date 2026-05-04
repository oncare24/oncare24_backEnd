package com.oncare.oncare24.notification.entity;

/**
 * 알림 종류.
 * Step 8: ZONE_EXIT, DEVICE_DISCONNECTED 두 종류만 사용.
 * Step 9-A: WARD_INVITATION 추가 (보호자→피보호자 초대 발송 시).
 * SOS, MEDICATION_MISSED 등은 후속 Step에서 추가.
 */
public enum NotificationType {
    ZONE_EXIT,
    DEVICE_DISCONNECTED,
    WARD_INVITATION,
    SOS,
    MEDICATION_MISSED,
    INACTIVITY_WARNING
}