package com.oncare.oncare24.medication.service;

import com.oncare.oncare24.global.exception.CustomException;
import com.oncare.oncare24.guardian.entity.GuardianWardStatus;
import com.oncare.oncare24.guardian.repository.GuardianWardRepository;
import com.oncare.oncare24.medication.dto.CreateMedicationScheduleRequest;
import com.oncare.oncare24.medication.dto.MedicationScheduleResponse;
import com.oncare.oncare24.medication.dto.UpdateMedicationScheduleRequest;
import com.oncare.oncare24.medication.entity.MedicationSchedule;
import com.oncare.oncare24.medication.entity.MedicationScheduleType;
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

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MedicationScheduleServiceTest {

    private static final Long WARD_ID = 1L;
    private static final Long GUARDIAN_ID = 2L;
    private static final Long OTHER_GUARDIAN_ID = 3L;
    private static final Long SCHEDULE_ID = 10L;

    @Mock
    private MedicationScheduleRepository medicationScheduleRepository;

    @Mock
    private GuardianWardRepository guardianWardRepository;

    @Mock
    private UserRepository userRepository;

    private MedicationScheduleService medicationScheduleService;

    @BeforeEach
    void setUp() {
        medicationScheduleService = new MedicationScheduleService(
                medicationScheduleRepository,
                guardianWardRepository,
                userRepository
        );
    }

    @Test
    void create_succeedsForDailyScheduleByElderSelf() {
        stubUser(WARD_ID, UserRole.ELDER);
        when(medicationScheduleRepository.save(org.mockito.ArgumentMatchers.any(MedicationSchedule.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        MedicationScheduleResponse response = medicationScheduleService.create(
                WARD_ID,
                createRequest(MedicationScheduleType.DAILY, null)
        );

        assertThat(response.wardId()).isEqualTo(WARD_ID);
        assertThat(response.scheduleType()).isEqualTo(MedicationScheduleType.DAILY);
        assertThat(response.active()).isTrue();
    }

    @Test
    void create_succeedsForWeeklyScheduleWithDayOfWeek() {
        stubUser(WARD_ID, UserRole.ELDER);
        when(medicationScheduleRepository.save(org.mockito.ArgumentMatchers.any(MedicationSchedule.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        MedicationScheduleResponse response = medicationScheduleService.create(
                WARD_ID,
                createRequest(MedicationScheduleType.WEEKLY, DayOfWeek.MONDAY)
        );

        assertThat(response.scheduleType()).isEqualTo(MedicationScheduleType.WEEKLY);
        assertThat(response.dayOfWeek()).isEqualTo(DayOfWeek.MONDAY);
    }

    @Test
    void create_rejectsWeeklyScheduleWithoutDayOfWeek() {
        assertThatThrownBy(() -> medicationScheduleService.create(
                WARD_ID,
                createRequest(MedicationScheduleType.WEEKLY, null)
        )).isInstanceOf(CustomException.class);
    }

    @Test
    void create_rejectsNegativeAllowedEarlyMinutes() {
        assertThatThrownBy(() -> medicationScheduleService.create(
                WARD_ID,
                new CreateMedicationScheduleRequest(
                        WARD_ID,
                        "morning pill",
                        LocalTime.of(8, 0),
                        -1,
                        30,
                        MedicationScheduleType.DAILY,
                        null
                )
        )).isInstanceOf(CustomException.class);
    }

    @Test
    void create_rejectsNegativeAllowedDelayMinutes() {
        assertThatThrownBy(() -> medicationScheduleService.create(
                WARD_ID,
                new CreateMedicationScheduleRequest(
                        WARD_ID,
                        "morning pill",
                        LocalTime.of(8, 0),
                        10,
                        -1,
                        MedicationScheduleType.DAILY,
                        null
                )
        )).isInstanceOf(CustomException.class);
    }

    @Test
    void update_succeedsAndChangesActiveState() {
        MedicationSchedule schedule = schedule();
        stubUser(WARD_ID, UserRole.ELDER);
        when(medicationScheduleRepository.findById(SCHEDULE_ID)).thenReturn(Optional.of(schedule));

        MedicationScheduleResponse response = medicationScheduleService.update(
                WARD_ID,
                SCHEDULE_ID,
                new UpdateMedicationScheduleRequest(
                        "evening pill",
                        LocalTime.of(20, 0),
                        5,
                        60,
                        MedicationScheduleType.DAILY,
                        null,
                        false
                )
        );

        assertThat(response.medicationName()).isEqualTo("evening pill");
        assertThat(response.allowedEarlyMinutes()).isEqualTo(5);
        assertThat(response.allowedDelayMinutes()).isEqualTo(60);
        assertThat(response.active()).isFalse();
    }

    @Test
    void deactivate_setsScheduleInactive() {
        MedicationSchedule schedule = schedule();
        stubUser(WARD_ID, UserRole.ELDER);
        when(medicationScheduleRepository.findById(SCHEDULE_ID)).thenReturn(Optional.of(schedule));

        medicationScheduleService.deactivate(WARD_ID, SCHEDULE_ID);

        assertThat(schedule.isActive()).isFalse();
    }

    @Test
    void findById_rejectsUnlinkedGuardian() {
        MedicationSchedule schedule = schedule();
        stubUser(OTHER_GUARDIAN_ID, UserRole.GUARDIAN);
        when(medicationScheduleRepository.findById(SCHEDULE_ID)).thenReturn(Optional.of(schedule));
        when(guardianWardRepository.existsByGuardianIdAndWardIdAndStatus(
                OTHER_GUARDIAN_ID,
                WARD_ID,
                GuardianWardStatus.ACCEPTED
        )).thenReturn(false);

        assertThatThrownBy(() -> medicationScheduleService.findById(OTHER_GUARDIAN_ID, SCHEDULE_ID))
                .isInstanceOf(CustomException.class);
    }

    @Test
    void findById_allowsAcceptedGuardian() {
        MedicationSchedule schedule = schedule();
        stubUser(GUARDIAN_ID, UserRole.GUARDIAN);
        when(medicationScheduleRepository.findById(SCHEDULE_ID)).thenReturn(Optional.of(schedule));
        when(guardianWardRepository.existsByGuardianIdAndWardIdAndStatus(
                GUARDIAN_ID,
                WARD_ID,
                GuardianWardStatus.ACCEPTED
        )).thenReturn(true);

        MedicationScheduleResponse response = medicationScheduleService.findById(GUARDIAN_ID, SCHEDULE_ID);

        assertThat(response.scheduleId()).isEqualTo(SCHEDULE_ID);
    }

    private CreateMedicationScheduleRequest createRequest(MedicationScheduleType type, DayOfWeek dayOfWeek) {
        return new CreateMedicationScheduleRequest(
                WARD_ID,
                "morning pill",
                LocalTime.of(8, 0),
                10,
                30,
                type,
                dayOfWeek
        );
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

    private void stubUser(Long currentUserId, UserRole currentRole) {
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
        when(userRepository.findById(WARD_ID)).thenReturn(Optional.of(ward));
    }
}
