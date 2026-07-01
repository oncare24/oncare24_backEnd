package com.oncare.oncare24.medication.service;

import com.oncare.oncare24.analysis.entity.ActivityEventType;
import com.oncare.oncare24.analysis.entity.EncryptedActivityLog;
import com.oncare.oncare24.analysis.event.MedicationAnalysisRefreshRequestedEvent;
import com.oncare.oncare24.analysis.service.EncryptedSourceEventService;
import com.oncare.oncare24.global.exception.CustomException;
import com.oncare.oncare24.guardian.entity.GuardianWardStatus;
import com.oncare.oncare24.guardian.repository.GuardianWardRepository;
import com.oncare.oncare24.medication.dto.AutoRegisterResult;
import com.oncare.oncare24.medication.dto.CreateMedicationScheduleRequest;
import com.oncare.oncare24.medication.dto.MedicationSchedulePayload;
import com.oncare.oncare24.medication.dto.PrescriptionImportItem;
import com.oncare.oncare24.medication.dto.MedicationScheduleResponse;
import com.oncare.oncare24.medication.dto.MedicationScheduleSourceResponse;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.never;

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

    @Mock
    private EncryptedSourceEventService encryptedSourceEventService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private MedicationSourceQueryService sourceQueryService;

    @Mock
    private com.oncare.oncare24.notification.sender.FcmSender fcmSender;

    @Mock
    private CodefKeyHasher codefKeyHasher;

    private MedicationScheduleService medicationScheduleService;

    @BeforeEach
    void setUp() {
        medicationScheduleService = new MedicationScheduleService(
                medicationScheduleRepository,
                guardianWardRepository,
                userRepository,
                encryptedSourceEventService,
                eventPublisher,
                sourceQueryService,
                fcmSender,
                codefKeyHasher
        );
    }

    @Test
    void create_succeedsForDailyScheduleByElderSelf() {
        stubUser(WARD_ID, UserRole.ELDER);
        when(medicationScheduleRepository.save(any(MedicationSchedule.class)))
                .thenAnswer(invocation -> withId(invocation.getArgument(0), SCHEDULE_ID));
        when(encryptedSourceEventService.saveRequiredSourceEvent(
                eq(WARD_ID),
                eq(ActivityEventType.MEDICATION_EVENT),
                eq("medication_schedule"),
                eq(SCHEDULE_ID),
                any(),
                any()
        )).thenReturn(encryptedLog(900L));

        MedicationScheduleResponse response = medicationScheduleService.create(
                WARD_ID,
                createRequest(MedicationScheduleType.DAILY, null)
        );

        assertThat(response.wardId()).isEqualTo(WARD_ID);
        assertThat(response.scheduleType()).isEqualTo(MedicationScheduleType.DAILY);
        assertThat(response.active()).isTrue();

        ArgumentCaptor<MedicationSchedule> scheduleCaptor = ArgumentCaptor.forClass(MedicationSchedule.class);
        verify(medicationScheduleRepository).save(scheduleCaptor.capture());
        MedicationSchedule savedSchedule = scheduleCaptor.getValue();
        assertThat(savedSchedule.getMedicationName()).isNull();
        assertThat(savedSchedule.getScheduledTime()).isNull();
        assertThat(savedSchedule.getAllowedEarlyMinutes()).isNull();
        assertThat(savedSchedule.getAllowedDelayMinutes()).isNull();
        assertThat(savedSchedule.getScheduleType()).isNull();
        assertThat(savedSchedule.getDayOfWeek()).isNull();
        assertThat(savedSchedule.getEncryptedActivityLogId()).isEqualTo(900L);

        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
        verify(encryptedSourceEventService).saveRequiredSourceEvent(
                eq(WARD_ID),
                eq(ActivityEventType.MEDICATION_EVENT),
                eq("medication_schedule"),
                eq(SCHEDULE_ID),
                any(),
                payloadCaptor.capture()
        );
        MedicationSchedulePayload capturedPayload = (MedicationSchedulePayload) payloadCaptor.getValue();
        assertThat(capturedPayload.action()).isEqualTo("CREATED");
        assertThat(capturedPayload.scheduleId()).isEqualTo(SCHEDULE_ID);
        assertThat(capturedPayload.medicationName()).isEqualTo("morning pill");
        assertThat(capturedPayload.scheduledTime()).isEqualTo(LocalTime.of(8, 0));
        assertThat(capturedPayload.allowedDelayMinutes()).isEqualTo(30);
        verifyMedicationRefreshEventPublished();
    }

    @Test
    void create_succeedsForWeeklyScheduleWithDayOfWeek() {
        stubUser(WARD_ID, UserRole.ELDER);
        when(medicationScheduleRepository.save(org.mockito.ArgumentMatchers.any(MedicationSchedule.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(encryptedSourceEventService.saveRequiredSourceEvent(
                eq(WARD_ID),
                eq(ActivityEventType.MEDICATION_EVENT),
                eq("medication_schedule"),
                any(),
                any(),
                any()
        )).thenReturn(encryptedLog(901L));

        MedicationScheduleResponse response = medicationScheduleService.create(
                WARD_ID,
                createRequest(MedicationScheduleType.WEEKLY, DayOfWeek.MONDAY)
        );

        assertThat(response.scheduleType()).isEqualTo(MedicationScheduleType.WEEKLY);
        assertThat(response.dayOfWeek()).isEqualTo(DayOfWeek.MONDAY);
    }

    @Test
    void create_throwsAndLeavesScheduleUnlinkedWhenRequiredEncryptionFails() {
        stubUser(WARD_ID, UserRole.ELDER);
        when(medicationScheduleRepository.save(any(MedicationSchedule.class)))
                .thenAnswer(invocation -> withId(invocation.getArgument(0), SCHEDULE_ID));
        when(encryptedSourceEventService.saveRequiredSourceEvent(
                eq(WARD_ID),
                eq(ActivityEventType.MEDICATION_EVENT),
                eq("medication_schedule"),
                eq(SCHEDULE_ID),
                any(),
                any()
        )).thenThrow(new IllegalStateException("crypto disabled"));

        assertThatThrownBy(() -> medicationScheduleService.create(
                WARD_ID,
                createRequest(MedicationScheduleType.DAILY, null)
        )).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("crypto disabled");

        ArgumentCaptor<MedicationSchedule> scheduleCaptor = ArgumentCaptor.forClass(MedicationSchedule.class);
        verify(medicationScheduleRepository).save(scheduleCaptor.capture());
        MedicationSchedule savedSchedule = scheduleCaptor.getValue();
        assertThat(savedSchedule.getMedicationName()).isNull();
        assertThat(savedSchedule.getScheduledTime()).isNull();
        assertThat(savedSchedule.getAllowedEarlyMinutes()).isNull();
        assertThat(savedSchedule.getAllowedDelayMinutes()).isNull();
        assertThat(savedSchedule.getScheduleType()).isNull();
        assertThat(savedSchedule.getDayOfWeek()).isNull();
        assertThat(savedSchedule.getEncryptedActivityLogId()).isNull();
        verifyNoInteractions(eventPublisher);
        verifyNoMoreInteractions(medicationScheduleRepository);
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
    void update_deactivatesGroupWhenActiveFalse() {
        // active=false 요청이면 현재 update는 sourceQueryService로 그룹을 찾아
        // 그룹 전체를 비활성화(암호화 DEACTIVATED 이벤트)한다.
        MedicationSchedule schedule = schedule();
        stubUser(WARD_ID, UserRole.ELDER);
        when(medicationScheduleRepository.findById(SCHEDULE_ID)).thenReturn(Optional.of(schedule));
        when(sourceQueryService.findMedicationSchedules(WARD_ID, WARD_ID, false))
                .thenReturn(List.of(new MedicationScheduleSourceResponse(
                        SCHEDULE_ID,
                        "혈압약",
                        LocalTime.of(8, 0),
                        10,
                        30,
                        MedicationScheduleType.DAILY,
                        null,
                        true,
                        java.time.LocalDateTime.now())));
        when(encryptedSourceEventService.saveRequiredSourceEvent(
                eq(WARD_ID),
                eq(ActivityEventType.MEDICATION_EVENT),
                eq("medication_schedule"),
                eq(SCHEDULE_ID),
                any(),
                any()
        )).thenReturn(encryptedLog(902L));

        medicationScheduleService.update(
                WARD_ID,
                SCHEDULE_ID,
                new UpdateMedicationScheduleRequest(
                        "혈압약",
                        LocalTime.of(8, 0),
                        10,
                        30,
                        MedicationScheduleType.DAILY,
                        null,
                        null,
                        false,
                        null,
                        null
                )
        );

        assertThat(schedule.isActive()).isFalse();
        assertThat(schedule.getEncryptedActivityLogId()).isEqualTo(902L);
        verifyMedicationRefreshEventPublished();
    }

    @Test
    void deactivate_setsScheduleInactive() {
        MedicationSchedule schedule = schedule();
        stubUser(WARD_ID, UserRole.ELDER);
        when(medicationScheduleRepository.findById(SCHEDULE_ID)).thenReturn(Optional.of(schedule));
        when(encryptedSourceEventService.saveRequiredSourceEvent(
                eq(WARD_ID),
                eq(ActivityEventType.MEDICATION_EVENT),
                eq("medication_schedule"),
                eq(SCHEDULE_ID),
                any(),
                any()
        )).thenReturn(encryptedLog(903L));

        medicationScheduleService.deactivate(WARD_ID, SCHEDULE_ID);

        assertThat(schedule.isActive()).isFalse();
        verifyMedicationRefreshEventPublished();
    }

    @Test
    void create_succeedsForWeeklyScheduleWithMultipleDaysOfWeek() {
        stubUser(WARD_ID, UserRole.ELDER);
        when(medicationScheduleRepository.save(any(MedicationSchedule.class)))
                .thenAnswer(invocation -> withId(invocation.getArgument(0), SCHEDULE_ID))
                .thenAnswer(invocation -> withId(invocation.getArgument(0), SCHEDULE_ID + 1))
                .thenAnswer(invocation -> withId(invocation.getArgument(0), SCHEDULE_ID + 2));
        when(encryptedSourceEventService.saveRequiredSourceEvent(
                eq(WARD_ID),
                eq(ActivityEventType.MEDICATION_EVENT),
                eq("medication_schedule"),
                any(),
                any(),
                any()
        )).thenReturn(encryptedLog(901L));

        MedicationScheduleResponse response = medicationScheduleService.create(
                WARD_ID,
                new CreateMedicationScheduleRequest(
                        WARD_ID,
                        "morning pill",
                        LocalTime.of(8, 0),
                        10,
                        30,
                        MedicationScheduleType.WEEKLY,
                        null,
                        List.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY),
                        null,
                        null
                )
        );

        assertThat(response.scheduleId()).isEqualTo(SCHEDULE_ID);
        assertThat(response.scheduleIds()).containsExactly(SCHEDULE_ID, SCHEDULE_ID + 1, SCHEDULE_ID + 2);
        assertThat(response.daysOfWeek()).containsExactly(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY);
        verify(medicationScheduleRepository, times(3)).save(any(MedicationSchedule.class));
        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
        verify(encryptedSourceEventService, times(3)).saveRequiredSourceEvent(
                eq(WARD_ID),
                eq(ActivityEventType.MEDICATION_EVENT),
                eq("medication_schedule"),
                any(),
                any(),
                payloadCaptor.capture()
        );
        assertThat(payloadCaptor.getAllValues())
                .map(MedicationSchedulePayload.class::cast)
                .extracting(MedicationSchedulePayload::dayOfWeek)
                .containsExactly(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY);
        verifyMedicationRefreshEventPublished();
    }

    @Test
    void create_deduplicatesWeeklyDaysOfWeek() {
        stubUser(WARD_ID, UserRole.ELDER);
        when(medicationScheduleRepository.save(any(MedicationSchedule.class)))
                .thenAnswer(invocation -> withId(invocation.getArgument(0), SCHEDULE_ID))
                .thenAnswer(invocation -> withId(invocation.getArgument(0), SCHEDULE_ID + 1));
        when(encryptedSourceEventService.saveRequiredSourceEvent(
                eq(WARD_ID),
                eq(ActivityEventType.MEDICATION_EVENT),
                eq("medication_schedule"),
                any(),
                any(),
                any()
        )).thenReturn(encryptedLog(901L));

        MedicationScheduleResponse response = medicationScheduleService.create(
                WARD_ID,
                new CreateMedicationScheduleRequest(
                        WARD_ID,
                        "morning pill",
                        LocalTime.of(8, 0),
                        10,
                        30,
                        MedicationScheduleType.WEEKLY,
                        DayOfWeek.MONDAY,
                        List.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.MONDAY),
                        null,
                        null
                )
        );

        assertThat(response.scheduleIds()).containsExactly(SCHEDULE_ID, SCHEDULE_ID + 1);
        assertThat(response.daysOfWeek()).containsExactly(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY);
        verify(medicationScheduleRepository, times(2)).save(any(MedicationSchedule.class));
    }

    @Test
    void autoRegister_deduplicatesViaFallback_whenCodefKeyNull() {
        // 처방번호/약품코드가 없어 hash()는 null → hashFallback 키로 중복 검사해야 함.
        // (기존엔 null이면 가드를 스킵해 재분석마다 중복 등록되던 버그)
        when(codefKeyHasher.hash(any(), any())).thenReturn(null);
        when(codefKeyHasher.hashFallback(any(), any(), any())).thenReturn("fb-key");
        when(medicationScheduleRepository.existsByWardIdAndCodefKeyBidx(WARD_ID, "fb-key"))
                .thenReturn(true);

        AutoRegisterResult result = medicationScheduleService.autoRegisterFromPrescriptions(
                WARD_ID,
                List.of(new PrescriptionImportItem(
                        "타이레놀", "3", "5", "20260601", null, null)));

        assertThat(result.duplicates()).containsExactly("타이레놀");
        assertThat(result.registered()).isEmpty();
        verify(medicationScheduleRepository, never()).save(any());
    }

    private void verifyMedicationRefreshEventPublished() {
        ArgumentCaptor<MedicationAnalysisRefreshRequestedEvent> eventCaptor =
                ArgumentCaptor.forClass(MedicationAnalysisRefreshRequestedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().wardId()).isEqualTo(WARD_ID);
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

    private MedicationSchedule withId(MedicationSchedule schedule, Long id) {
        ReflectionTestUtils.setField(schedule, "id", id);
        return schedule;
    }

    private EncryptedActivityLog encryptedLog(Long id) {
        EncryptedActivityLog log = EncryptedActivityLog.builder()
                .wardId(WARD_ID)
                .dataKeyId("datakey-test")
                .eventType(ActivityEventType.MEDICATION_EVENT)
                .sourceTable("medication_schedule")
                .sourceId(SCHEDULE_ID)
                .occurredAt(java.time.LocalDateTime.now())
                .encryptedPackage(new byte[]{1, 2, 3})
                .aadJson("{}")
                .build();
        ReflectionTestUtils.setField(log, "id", id);
        return log;
    }
}
