package com.oncare.oncare24.location.entity;

/**
 * 위치 보고가 어떤 경로로 들어왔는지.
 * 발표 답변용 + 운영 통계용 (성공률 측정).
 */
public enum LocationReportSource {
    /** 30분 주기 백그라운드 자동 보고 (정상 케이스) */
    BACKGROUND_SCHEDULED,
    /** 보호자가 FCM으로 깨워서 보고 (DISCONNECTED 복구 시도) */
    FCM_WAKEUP,
    /** 앱 포그라운드 진입 시 즉시 보고 */
    FOREGROUND_RESUME
}