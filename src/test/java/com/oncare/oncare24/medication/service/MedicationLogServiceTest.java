package com.oncare.oncare24.medication.service;

import com.oncare.oncare24.global.exception.CustomException;
import com.oncare.oncare24.guardian.entity.GuardianWardStatus;
import com.oncare.oncare24.guardian.repository.GuardianWardRepository;
import com.oncare.oncare24.medication.dto.CreateMedicationLogRequest;
import com.oncare.oncare24.medication.dto.MedicationLogResponse;
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

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MedicationLogServiceTest {

    private static final Long WARD_ID = 1L;
    private static final Long GUARDIAN_ID = 2L;
    private static final Long OTHER_WARD_ID = 3L;
    private static final Long SCHEDULE_ID = 10L;
    private static final LocalDateTime TAKEN_AT = LocalDateTime.of(2026, 5, 8, 7, 55);

    @Mock
    private MedicationLogRepository medicationLogRepository;

    @Mock
    private MedicationScheduleRepository medicationScheduleRepository;

    @Mock
    private GuardianWardRepository guardianWardRepository;

    @Mock
    private UserRepository userRepository;

    private MedicationLogService medicationLogService;

    @BeforeEach
    void setUp() {
        medicationLogService = new MedicationLogService(
                medicationLogRepository,
                medicationScheduleRepository,
                guardianWardRepository,
                userRepository
        );
    }

    @Test
    void create_succeedsWithScheduleAndUsesScheduleMedicationName() {
        stubUser(WARD_ID, UserRole.ELDER, WARD_ID);
        when(medicationScheduleRepository.findById(SCHEDULE_ID)).thenReturn(Optional.of(schedule(WARD_ID, true)));
        when(medicationLogRepository.save(org.mockito.ArgumentMatchers.any(MedicationLog.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        MedicationLogResponse response = medicationLogService.create(
                WARD_ID,
                new CreateMedicationLogRequest(
                        WARD_ID,
                        SCHEDULE_ID,
                        TAKEN_AT,
                        "client name",
                        null
                )
        );

        assertThat(response.wardId()).isEqualTo(WARD_ID);
        assertThat(response.scheduleId()).isEqualTo(SCHEDULE_ID);
        assertThat(response.medicationName()).isEqualTo("morning pill");
        assertThat(response.logSource()).isEqualTo(MedicationLogSource.USER_INPUT);
    }

    @Test
    void create_failsWhenScheduleBelongsToDifferentWard() {
        stubUser(WARD_ID, UserRole.ELDER, WARD_ID);
        when(medicationScheduleRepository.findById(SCHEDULE_ID)).thenReturn(Optional.of(schedule(OTHER_WARD_ID, true)));

        assertThatThrownBy(() -> medicationLogService.create(
                WARD_ID,
                new CreateMedicationLogRequest(WARD_ID, SCHEDULE_ID, TAKEN_AT, null, null)
        )).isInstanceOf(CustomException.class);
    }

    @Test
    void create_failsForInactiveSchedule() {
        stubUser(WARD_ID, UserRole.ELDER, WARD_ID);
        when(medicationScheduleRepository.findById(SCHEDULE_ID)).thenReturn(Optional.of(schedule(WARD_ID, false)));

        assertThatThrownBy(() -> medicationLogService.create(
                WARD_ID,
                new CreateMedicationLogRequest(WARD_ID, SCHEDULE_ID, TAKEN_AT, null, null)
        )).isInstanceOf(CustomException.class);
    }

    @Test
    void create_allowsAcceptedGuardianAndSetsGuardianInputWhenSourceIsDefault() {
        stubUser(GUARDIAN_ID, UserRole.GUARDIAN, WARD_ID);
        when(guardianWardRepository.existsByGuardianIdAndWardIdAndStatus(
                GUARDIAN_ID,
                WARD_ID,
                GuardianWardStatus.ACCEPTED
        )).thenReturn(true);
        when(medicationScheduleRepository.findById(SCHEDULE_ID)).thenReturn(Optional.of(schedule(WARD_ID, true)));
        when(medicationLogRepository.save(org.mockito.ArgumentMatchers.any(MedicationLog.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        MedicationLogResponse response = medicationLogService.create(
                GUARDIAN_ID,
                new CreateMedicationLogRequest(WARD_ID, SCHEDULE_ID, TAKEN_AT, null, null)
        );

        assertThat(response.logSource()).isEqualTo(MedicationLogSource.GUARDIAN_INPUT);
    }

    @Test
    void create_rejectsUnlinkedGuardian() {
        stubUser(GUARDIAN_ID, UserRole.GUARDIAN, WARD_ID);
        when(guardianWardRepository.existsByGuardianIdAndWardIdAndStatus(
                GUARDIAN_ID,
                WARD_ID,
                GuardianWardStatus.ACCEPTED
        )).thenReturn(false);

        assertThatThrownBy(() -> medicationLogService.create(
                GUARDIAN_ID,
                new CreateMedicationLogRequest(WARD_ID, SCHEDULE_ID, TAKEN_AT, null, null)
        )).isInstanceOf(CustomException.class);
    }

    private MedicationSchedule schedule(Long wardId, boolean active) {
        MedicationSchedule schedule = MedicationSchedule.builder()
                .wardId(wardId)
                .medicationName("morning pill")
                .scheduledTime(LocalTime.of(8, 0))
                .allowedEarlyMinutes(10)
                .allowedDelayMinutes(30)
                .scheduleType(MedicationScheduleType.DAILY)
                .build();
        ReflectionTestUtils.setField(schedule, "id", SCHEDULE_ID);
        if (!active) {
            schedule.deactivate();
        }
        return schedule;
    }

    private void stubUser(Long currentUserId, UserRole currentRole, Long wardId) {
        User currentUser = User.builder()
                .phone("01000000000")
                .password("encoded")
                .name("current")
                .role(currentRole)
                .build();
        User ward = User.builder()
                .phone("01011111111")
                .password("encoded")
                .name("ward")
                .role(UserRole.ELDER)
                .build();

        when(userRepository.findById(currentUserId)).thenReturn(Optional.of(currentUser));
        when(userRepository.findById(wardId)).thenReturn(Optional.of(ward));
    }
}
