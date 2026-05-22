package com.oncare.oncare24.medication.repository;

import com.oncare.oncare24.medication.entity.MedicationMissRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * 약 미복용 감지 기록(medication_miss_record) 표를 꺼내고 넣기 위한 도구.
 * <p>
 * 이 단계에서는 배치가 INSERT만 함. 다음 단계 작업(연속 누락 감지, 일일 다이제스트)에서
 * 조회 메서드(findByWardIdAndScheduledDate 등)를 추가로 사용할 예정이라 미리 정의해둠.
 */
public interface MedicationMissRecordRepository extends JpaRepository<MedicationMissRecord, Long> {

    /**
     * 중복 감지 방지용. 배치가 같은 스케줄/같은 날짜에 대해 두 번 INSERT 시도하면
     * DB의 UNIQUE 제약에 걸려 예외가 난다. 그 전에 미리 체크해서 깔끔하게 건너뛴다.
     */
    boolean existsByScheduleIdAndScheduledDate(Long scheduleId, LocalDate scheduledDate);

    /**
     * 특정 스케줄의 가장 최근 미복용 기록 1건. 다음 단계 작업(연속 누락 감지)에서 사용.
     * 예: 오늘 빼먹은 약이 있을 때, 직전 예정일에도 빼먹었는지 확인.
     */
    Optional<MedicationMissRecord> findFirstByScheduleIdOrderByScheduledDateDesc(Long scheduleId);

    /**
     * 특정 어머니의 특정 날짜에 빼먹은 약 목록. 다음 단계 작업(일일 다이제스트)에서 사용.
     * 예: "오늘 박순자 어머님이 빼먹은 약 3개" 요약 만들 때.
     */
    List<MedicationMissRecord> findByWardIdAndScheduledDateOrderByScheduledTimeAsc(Long wardId, LocalDate scheduledDate);
}