package com.oncare.oncare24.medication.service;

import com.oncare.oncare24.medication.dto.MedicationAnalysisResult;
import com.oncare.oncare24.medication.entity.MedicationAnalysisStatus;
import com.oncare.oncare24.medication.entity.MedicationLog;
import com.oncare.oncare24.medication.entity.MedicationLogSource;
import com.oncare.oncare24.medication.entity.MedicationSchedule;
import com.oncare.oncare24.medication.entity.MedicationScheduleType;
import com.oncare.oncare24.medication.repository.MedicationLogRepository;
import com.oncare.oncare24.medication.repository.MedicationScheduleRepository;
import com.oncare.oncare24.user.entity.User;
import com.oncare.oncare24.user.entity.UserRole;
import com.oncare.oncare24.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MedicationAnalysisServiceTest {

    private static final Long WARD_ID = 1L;
    private static final Long SCHEDULE_ID = 10L;

    @Mock
    private MedicationScheduleRepository medicationScheduleRepository;

    @Mock
    private MedicationLogRepository medicationLogRepository;

    @Mock
    private UserRepository userRepository;

    private MedicationAnalysisService medicationAnalysisService;

    @BeforeEach
    void setUp() {
        medicationAnalysisService = new MedicationAnalysisService(
                medicationScheduleRepository,
                medicationLogRepository,
                userRepository
        );

        User ward = User.builder()
                .phone("01012345678")
                .password("encoded-password")
                .name("ward")
                .role(UserRole.ELDER)
                .build();

        when(userRepository.findById(WARD_ID)).thenReturn(Optional.of(ward));
    }

    @Test
    void analyzeWardMedication_returnsOnTimeWhenTakenWithinEarlyWindow() {
        LocalDate analysisDate = LocalDate.now();
        MedicationAnalysisResult result = analyzeWithLog(
                analysisDate,
                LocalDateTime.of(analysisDate, LocalTime.of(7, 55))
        );

        assertThat(result.status()).isEqualTo(MedicationAnalysisStatus.ON_TIME);
        assertThat(result.windowStartAt()).isEqualTo(LocalDateTime.of(analysisDate, LocalTime.of(7, 50)));
        assertThat(result.allowedEarlyMinutes()).isEqualTo(10);
    }

    @Test
    void analyzeWardMedication_doesNotTreatTooEarlyLogAsOnTime() {
        LocalDate analysisDate = LocalDate.now().minusDays(1);
        MedicationAnalysisResult result = analyzeWithLogs(
                analysisDate,
                List.of(log(LocalDateTime.of(analysisDate, LocalTime.of(7, 40))))
        );

        assertThat(result.status()).isEqualTo(MedicationAnalysisStatus.MISSED);
        assertThat(result.takenAt()).isNull();
    }

    @Test
    void analyzeWardMedication_returnsOnTimeWhenTakenBeforeDeadline() {
        LocalDate analysisDate = LocalDate.now();
        MedicationAnalysisResult result = analyzeWithLog(
                analysisDate,
                LocalDateTime.of(analysisDate, LocalTime.of(8, 20))
        );

        assertThat(result.status()).isEqualTo(MedicationAnalysisStatus.ON_TIME);
    }

    @Test
    void analyzeWardMedication_returnsDelayedWhenTakenAfterDeadline() {
        LocalDate analysisDate = LocalDate.now();
        MedicationAnalysisResult result = analyzeWithLog(
                analysisDate,
                LocalDateTime.of(analysisDate, LocalTime.of(8, 40))
        );

        assertThat(result.status()).isEqualTo(MedicationAnalysisStatus.DELAYED);
    }

    @Test
    void analyzeWardMedication_returnsMissedWhenDeadlinePassedWithoutValidLog() {
        LocalDate analysisDate = LocalDate.now().minusDays(1);
        MedicationAnalysisResult result = analyzeWithLogs(analysisDate, List.of());

        assertThat(result.status()).isEqualTo(MedicationAnalysisStatus.MISSED);
    }

    @Test
    void analyzeWardMedication_returnsPendingWhenDeadlineHasNotPassedWithoutValidLog() {
        LocalDate analysisDate = LocalDate.now().plusDays(1);
        MedicationAnalysisResult result = analyzeWithLogs(analysisDate, List.of());

        assertThat(result.status()).isEqualTo(MedicationAnalysisStatus.PENDING);
    }

    private MedicationAnalysisResult analyzeWithLog(LocalDate analysisDate, LocalDateTime takenAt) {
        return analyzeWithLogs(analysisDate, List.of(log(takenAt)));
    }

    private MedicationAnalysisResult analyzeWithLogs(LocalDate analysisDate, List<MedicationLog> logs) {
        MedicationSchedule schedule = schedule();

        when(medicationScheduleRepository.findByWardIdAndActiveTrueOrderByScheduledTimeAsc(WARD_ID))
                .thenReturn(List.of(schedule));
        when(medicationLogRepository.findByScheduleIdInAndTakenAtBetweenOrderByTakenAtAsc(
                anyList(),
                any(LocalDateTime.class),
                any(LocalDateTime.class)
        )).thenReturn(logs);

        return medicationAnalysisService.analyzeWardMedication(WARD_ID, analysisDate).get(0);
    }

    private MedicationSchedule schedule() {
        MedicationSchedule schedule = MedicationSchedule.builder()
                .wardId(WARD_ID)
                .medicationName("morning pill")
                .scheduledTime(LocalTime.of(8, 0))
                .allowedEarlyMinutes(10)
                .allowedDelayMinutes(30)
                .scheduleType(MedicationScheduleType.DAILY)
                .build();
        ReflectionTestUtils.setField(schedule, "id", SCHEDULE_ID);
        return schedule;
    }

    private MedicationLog log(LocalDateTime takenAt) {
        return MedicationLog.builder()
                .wardId(WARD_ID)
                .scheduleId(SCHEDULE_ID)
                .takenAt(takenAt)
                .medicationName("morning pill")
                .logSource(MedicationLogSource.USER_INPUT)
                .build();
    }
}
