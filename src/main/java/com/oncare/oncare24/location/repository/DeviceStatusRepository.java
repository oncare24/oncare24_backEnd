package com.oncare.oncare24.location.repository;

import com.oncare.oncare24.location.entity.DeviceState;
import com.oncare.oncare24.location.entity.DeviceStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface DeviceStatusRepository extends JpaRepository<DeviceStatus, Long> {

    Optional<DeviceStatus> findByUserId(Long userId);

    /**
     * 5분 배치용: ACTIVE 상태인데 lastReportAt이 임계 시각 이전인 단말 검색.
     * 이 결과들을 DISCONNECTED로 전환하고 보호자 알림 1회 발송.
     */
    List<DeviceStatus> findByStateAndLastReportAtBefore(DeviceState state, LocalDateTime threshold);
}