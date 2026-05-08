package com.oncare.oncare24.medication.repository;

import com.oncare.oncare24.medication.entity.MedicationSchedule;
import com.oncare.oncare24.medication.entity.MedicationScheduleType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.DayOfWeek;
import java.util.List;
import java.util.Optional;

public interface MedicationScheduleRepository extends JpaRepository<MedicationSchedule, Long> {

    Optional<MedicationSchedule> findByIdAndActiveTrue(Long id);

    List<MedicationSchedule> findByWardIdOrderByScheduledTimeAsc(Long wardId);

    List<MedicationSchedule> findByWardIdAndActiveTrueOrderByScheduledTimeAsc(Long wardId);

    List<MedicationSchedule> findByWardIdInAndActiveTrueOrderByWardIdAscScheduledTimeAsc(List<Long> wardIds);

    List<MedicationSchedule> findByActiveTrueOrderByWardIdAscScheduledTimeAsc();

    List<MedicationSchedule> findByWardIdAndScheduleTypeAndActiveTrueOrderByScheduledTimeAsc(
            Long wardId,
            MedicationScheduleType scheduleType
    );

    List<MedicationSchedule> findByWardIdAndDayOfWeekAndActiveTrueOrderByScheduledTimeAsc(
            Long wardId,
            DayOfWeek dayOfWeek
    );

    @Query("select distinct s.wardId from MedicationSchedule s where s.active = true order by s.wardId asc")
    List<Long> findDistinctWardIdsByActiveTrue();
}
