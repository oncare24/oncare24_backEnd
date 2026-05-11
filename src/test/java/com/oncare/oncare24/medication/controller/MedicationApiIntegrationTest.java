package com.oncare.oncare24.medication.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.oncare.oncare24.analysis.entity.ActivityEventType;
import com.oncare.oncare24.analysis.entity.EncryptedActivityLog;
import com.oncare.oncare24.analysis.service.AnalysisRefreshService;
import com.oncare.oncare24.analysis.service.EncryptedSourceEventService;
import com.oncare.oncare24.auth.security.CustomUserDetails;
import com.oncare.oncare24.guardian.entity.GuardianWard;
import com.oncare.oncare24.guardian.repository.GuardianWardRepository;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class MedicationApiIntegrationTest {

    private static final String MEDICATION_NAME = "morning pill";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private GuardianWardRepository guardianWardRepository;

    @Autowired
    private MedicationScheduleRepository medicationScheduleRepository;

    @Autowired
    private MedicationLogRepository medicationLogRepository;

    @MockitoBean
    private EncryptedSourceEventService encryptedSourceEventService;

    @MockitoBean
    private AnalysisRefreshService analysisRefreshService;

    @BeforeEach
    void setUpEncryptedSourceEventService() {
        when(encryptedSourceEventService.saveRequiredSourceEvent(
                any(),
                any(),
                any(),
                any(),
                any(),
                any()
        )).thenReturn(encryptedLog());
    }

    @Test
    void elderCanCreateMedicationSchedule() throws Exception {
        User elder = saveUser(UserRole.ELDER);

        mockMvc.perform(post("/api/medications/schedules")
                        .with(user(new CustomUserDetails(elder)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(scheduleRequest(elder.getId(), MEDICATION_NAME).toString()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.medicationName").value(MEDICATION_NAME))
                .andExpect(jsonPath("$.data.scheduledTime").value("08:00:00"))
                .andExpect(jsonPath("$.data.allowedEarlyMinutes").value(10))
                .andExpect(jsonPath("$.data.allowedDelayMinutes").value(30));

        List<MedicationSchedule> schedules =
                medicationScheduleRepository.findByWardIdOrderByScheduledTimeAsc(elder.getId());
        assertThat(schedules).hasSize(1);
        assertThat(schedules.get(0).getMedicationName()).isNull();
        assertThat(schedules.get(0).getScheduledTime()).isNull();
    }

    @Test
    void elderCanFindOwnMedicationSchedules() throws Exception {
        User elder = saveUser(UserRole.ELDER);
        MedicationSchedule schedule = saveSchedule(elder.getId(), MEDICATION_NAME);

        mockMvc.perform(get("/api/medications/schedules")
                        .param("wardId", String.valueOf(elder.getId()))
                        .with(user(new CustomUserDetails(elder))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].scheduleId").value(schedule.getId()))
                .andExpect(jsonPath("$.data[0].medicationName").value(MEDICATION_NAME));
    }

    @Test
    void elderCanCreateMedicationLog() throws Exception {
        User elder = saveUser(UserRole.ELDER);
        MedicationSchedule schedule = saveSchedule(elder.getId(), MEDICATION_NAME);

        mockMvc.perform(post("/api/medications/logs")
                        .with(user(new CustomUserDetails(elder)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(logRequest(elder.getId(), schedule.getId()).toString()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.wardId").value(elder.getId()))
                .andExpect(jsonPath("$.data.scheduleId").value(schedule.getId()))
                .andExpect(jsonPath("$.data.medicationName").value(MEDICATION_NAME))
                .andExpect(jsonPath("$.data.logSource").value("USER_INPUT"));

        List<MedicationLog> logs = medicationLogRepository.findByScheduleIdOrderByTakenAtDesc(schedule.getId());
        assertThat(logs).hasSize(1);
        assertThat(logs.get(0).getWardId()).isEqualTo(elder.getId());
        assertThat(logs.get(0).getTakenAt()).isNull();
    }

    @Test
    void unlinkedGuardianCannotAccessWardMedicationSchedule() throws Exception {
        User elder = saveUser(UserRole.ELDER);
        User guardian = saveUser(UserRole.GUARDIAN);

        mockMvc.perform(post("/api/medications/schedules")
                        .with(user(new CustomUserDetails(guardian)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(scheduleRequest(elder.getId(), MEDICATION_NAME).toString()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void acceptedGuardianCanCreateWardMedicationSchedule() throws Exception {
        User elder = saveUser(UserRole.ELDER);
        User guardian = saveUser(UserRole.GUARDIAN);
        saveAcceptedGuardianWard(elder.getId(), guardian.getId());

        mockMvc.perform(post("/api/medications/schedules")
                        .with(user(new CustomUserDetails(guardian)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(scheduleRequest(elder.getId(), MEDICATION_NAME).toString()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.wardId").value(elder.getId()));

        assertThat(medicationScheduleRepository.findByWardIdOrderByScheduledTimeAsc(elder.getId()))
                .hasSize(1);
    }

    @Test
    void medicationLogFailsWhenScheduleDoesNotBelongToWard() throws Exception {
        User elderA = saveUser(UserRole.ELDER);
        User elderB = saveUser(UserRole.ELDER);
        MedicationSchedule scheduleA = saveSchedule(elderA.getId(), MEDICATION_NAME);

        mockMvc.perform(post("/api/medications/logs")
                        .with(user(new CustomUserDetails(elderB)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(logRequest(elderB.getId(), scheduleA.getId()).toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));

        assertThat(medicationLogRepository.findByScheduleIdOrderByTakenAtDesc(scheduleA.getId()))
                .isEmpty();
    }

    private User saveUser(UserRole role) {
        String suffix = role.name() + System.nanoTime();
        return userRepository.saveAndFlush(User.builder()
                .phone("010" + Math.abs(suffix.hashCode()))
                .password("encoded-password")
                .name(role.name().toLowerCase())
                .role(role)
                .email(null)
                .build());
    }

    private MedicationSchedule saveSchedule(Long wardId, String medicationName) {
        return medicationScheduleRepository.saveAndFlush(MedicationSchedule.builder()
                .wardId(wardId)
                .medicationName(medicationName)
                .scheduledTime(LocalTime.of(8, 0))
                .allowedEarlyMinutes(10)
                .allowedDelayMinutes(30)
                .scheduleType(MedicationScheduleType.DAILY)
                .dayOfWeek(null)
                .build());
    }

    private void saveAcceptedGuardianWard(Long wardId, Long guardianId) {
        GuardianWard guardianWard = GuardianWard.builder()
                .wardId(wardId)
                .guardianId(guardianId)
                .inviteCode("T" + Math.abs((wardId + ":" + guardianId + ":" + System.nanoTime()).hashCode()))
                .relationship("family")
                .expiresAt(LocalDateTime.now().plusDays(1))
                .build();
        guardianWard.accept();
        guardianWardRepository.saveAndFlush(guardianWard);
    }

    private ObjectNode scheduleRequest(Long wardId, String medicationName) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("wardId", wardId);
        node.put("medicationName", medicationName);
        node.put("scheduledTime", "08:00");
        node.put("allowedEarlyMinutes", 10);
        node.put("allowedDelayMinutes", 30);
        node.put("scheduleType", "DAILY");
        node.putNull("dayOfWeek");
        return node;
    }

    private ObjectNode logRequest(Long wardId, Long scheduleId) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("wardId", wardId);
        node.put("scheduleId", scheduleId);
        node.put("takenAt", "2026-05-08T07:55:00");
        node.put("medicationName", MEDICATION_NAME);
        node.put("logSource", MedicationLogSource.USER_INPUT.name());
        return node;
    }

    private EncryptedActivityLog encryptedLog() {
        return EncryptedActivityLog.builder()
                .wardId(1L)
                .dataKeyId("datakey-test")
                .eventType(ActivityEventType.MEDICATION_EVENT)
                .sourceTable("medication_schedule")
                .sourceId(1L)
                .occurredAt(LocalDateTime.now())
                .encryptedPackage(new byte[]{1, 2, 3})
                .aadJson("{}")
                .build();
    }
}
