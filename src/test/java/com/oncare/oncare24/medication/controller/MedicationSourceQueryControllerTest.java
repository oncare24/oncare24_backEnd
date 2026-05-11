package com.oncare.oncare24.medication.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oncare.oncare24.auth.security.CustomUserDetails;
import com.oncare.oncare24.global.response.ApiResponse;
import com.oncare.oncare24.medication.dto.MedicationLogSourceResponse;
import com.oncare.oncare24.medication.dto.MedicationScheduleSourceResponse;
import com.oncare.oncare24.medication.entity.MedicationLogSource;
import com.oncare.oncare24.medication.entity.MedicationScheduleType;
import com.oncare.oncare24.medication.service.MedicationSourceQueryService;
import com.oncare.oncare24.user.entity.User;
import com.oncare.oncare24.user.entity.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MedicationSourceQueryControllerTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void findMedicationSchedulesReturnsSourceDtoWithoutCryptoFields() throws Exception {
        MedicationSourceQueryService queryService = mock(MedicationSourceQueryService.class);
        MedicationSourceQueryController controller = new MedicationSourceQueryController(queryService);
        User elder = user(1L, UserRole.ELDER);
        when(queryService.findMedicationSchedules(1L, 1L, false))
                .thenReturn(List.of(new MedicationScheduleSourceResponse(
                        10L,
                        "morning pill",
                        LocalTime.of(8, 0),
                        30,
                        60,
                        MedicationScheduleType.DAILY,
                        null,
                        true,
                        LocalDateTime.of(2026, 5, 11, 21, 30)
                )));

        ApiResponse<List<MedicationScheduleSourceResponse>> response =
                controller.findMedicationSchedules(new CustomUserDetails(elder), 1L, false);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getData()).hasSize(1);
        assertThat(response.getData().get(0).scheduleId()).isEqualTo(10L);
        verify(queryService).findMedicationSchedules(1L, 1L, false);
        assertNoCryptoFields(response);
    }

    @Test
    void findMedicationLogsPassesDateAndReturnsSourceDtoWithoutCryptoFields() throws Exception {
        MedicationSourceQueryService queryService = mock(MedicationSourceQueryService.class);
        MedicationSourceQueryController controller = new MedicationSourceQueryController(queryService);
        User elder = user(1L, UserRole.ELDER);
        LocalDate date = LocalDate.of(2026, 5, 11);
        when(queryService.findMedicationLogs(1L, 1L, date))
                .thenReturn(List.of(new MedicationLogSourceResponse(
                        10L,
                        "morning pill",
                        LocalDateTime.of(2026, 5, 11, 8, 0),
                        LocalDateTime.of(2026, 5, 11, 8, 10),
                        MedicationLogSource.USER_INPUT,
                        30,
                        60
                )));

        ApiResponse<List<MedicationLogSourceResponse>> response =
                controller.findMedicationLogs(new CustomUserDetails(elder), 1L, date);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getData()).hasSize(1);
        assertThat(response.getData().get(0).takenAt()).isEqualTo(LocalDateTime.of(2026, 5, 11, 8, 10));
        verify(queryService).findMedicationLogs(1L, 1L, date);
        assertNoCryptoFields(response);
    }

    private void assertNoCryptoFields(Object response) throws Exception {
        String json = objectMapper.writeValueAsString(response);
        JsonNode root = objectMapper.readTree(json);
        assertThat(root.findValues("encryptedPackage")).isEmpty();
        assertThat(root.findValues("aadJson")).isEmpty();
        assertThat(root.findValues("dataKeyId")).isEmpty();
    }

    private User user(Long id, UserRole role) {
        User user = User.builder()
                .phone("010" + id)
                .password("encoded")
                .name(role.name().toLowerCase())
                .role(role)
                .build();
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }
}
