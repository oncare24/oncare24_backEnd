package com.oncare.oncare24.safetyzone.entity;

/**
 * 안전구역 종류.
 * 프론트의 SafetyZoneType("home" | "senior_center" | "hospital" | "custom")에 1:1 매핑.
 * EnumType.STRING으로 저장하므로 DB에는 대문자 그대로 ("HOME") 들어감.
 * axios 변환 책임은 프론트에 있음.
 */
public enum SafetyZoneType {
    HOME,
    SENIOR_CENTER,
    HOSPITAL,
    CUSTOM
}