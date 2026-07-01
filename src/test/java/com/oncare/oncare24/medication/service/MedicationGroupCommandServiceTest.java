package com.oncare.oncare24.medication.service;

import com.oncare.oncare24.analysis.entity.EncryptedActivityLog;
import com.oncare.oncare24.analysis.event.MedicationAnalysisRefreshRequestedEvent;
import com.oncare.oncare24.analysis.service.EncryptedSourceEventService;
import com.oncare.oncare24.guardian.repository.GuardianWardRepository;
import com.oncare.oncare24.medication.dto.MedicationScheduleSourceResponse;
import com.oncare.oncare24.medication.entity.MedicationSchedule;
import com.oncare.oncare24.medication.entity.MedicationScheduleType;
import com.oncare.oncare24.medication.entity.MedicationSource;
import com.oncare.oncare24.medication.repository.MedicationScheduleRepository;
import com.oncare.oncare24.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MedicationGroupCommandServiceTest {

    private static final Long WARD_ID = 2L;
    private static final String GROUP_ID = "codef:rx:R1";

    @Mock
    private MedicationScheduleRepository medicationScheduleRepository;
    @Mock
    private MedicationSourceQueryService sourceQueryService;
    @Mock
    private MedicationGroupQueryService groupQueryService;
    @Mock
    private EncryptedSourceEventService encryptedSourceEventService;
    @Mock
    private GuardianWardRepository guardianWardRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    private MedicationGroupCommandService service;

    @BeforeEach
    void setUp() {
        service = new MedicationGroupCommandService(
                medicationScheduleRepository,
                sourceQueryService,
                groupQueryService,
                encryptedSourceEventService,
                guardianWardRepository,
                userRepository,
                eventPublisher
        );
    }

    @Test
    void movePacketTime_movesAllItemsInPacketTogether() {
        LocalTime from = LocalTime.of(8, 0);
        LocalTime to = LocalTime.of(9, 0);

        // 같은 봉지(group)·같은 시각(08:00)에 성분 2개 + 다른 시각(20:00) 1개
        MedicationScheduleSourceResponse item1 = sourceRow(101L, "암로디핀", from);
        MedicationScheduleSourceResponse item2 = sourceRow(102L, "로사르탄", from);
        MedicationScheduleSourceResponse other = sourceRow(103L, "메트포르민", LocalTime.of(20, 0));
        when(sourceQueryService.findMedicationSchedules(WARD_ID, WARD_ID, false))
                .thenReturn(List.of(item1, item2, other));

        MedicationSchedule s1 = schedule(from);
        MedicationSchedule s2 = schedule(from);
        when(medicationScheduleRepository.findById(101L)).thenReturn(Optional.of(s1));
        when(medicationScheduleRepository.findById(102L)).thenReturn(Optional.of(s2));
        EncryptedActivityLog enc = encLog(900L);
        when(encryptedSourceEventService.saveRequiredSourceEvent(
                any(), any(), any(), any(), any(), any()))
                .thenReturn(enc);

        service.movePacketTime(WARD_ID, WARD_ID, GROUP_ID, from, to);

        // 08:00 봉지의 두 성분이 모두 09:00으로 이동 (한 줄만 옮겨 잔존하는 버그 없음)
        assertThat(s1.getScheduledTime()).isEqualTo(to);
        assertThat(s2.getScheduledTime()).isEqualTo(to);
        // 다른 시각(20:00) 봉지는 건드리지 않음
        verify(medicationScheduleRepository, never()).findById(103L);
        // 성분 수만큼 새 암호화 이벤트 저장
        verify(encryptedSourceEventService, times(2))
                .saveRequiredSourceEvent(any(), any(), any(), any(), any(), any());
        verify(eventPublisher).publishEvent(any(MedicationAnalysisRefreshRequestedEvent.class));
    }

    private MedicationScheduleSourceResponse sourceRow(Long scheduleId, String name, LocalTime time) {
        return new MedicationScheduleSourceResponse(
                scheduleId, name, time, 10, 30,
                MedicationScheduleType.DAILY, null, List.of(), true,
                LocalDateTime.of(2026, 6, 30, 9, 0), null, null,
                GROUP_ID, MedicationSource.AUTO);
    }

    private MedicationSchedule schedule(LocalTime time) {
        return MedicationSchedule.builder()
                .wardId(WARD_ID)
                .scheduledTime(time)
                .groupId(GROUP_ID)
                .source(MedicationSource.AUTO)
                .build();
    }

    private EncryptedActivityLog encLog(Long id) {
        EncryptedActivityLog log = mock(EncryptedActivityLog.class);
        when(log.getId()).thenReturn(id);
        return log;
    }
}
