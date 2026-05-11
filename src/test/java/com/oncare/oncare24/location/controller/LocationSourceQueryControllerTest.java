package com.oncare.oncare24.location.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oncare.oncare24.auth.security.CustomUserDetails;
import com.oncare.oncare24.global.response.ApiResponse;
import com.oncare.oncare24.location.dto.DeviceStatusSourceResponse;
import com.oncare.oncare24.location.dto.LocationSourceResponse;
import com.oncare.oncare24.location.entity.DeviceState;
import com.oncare.oncare24.location.entity.LocationReportSource;
import com.oncare.oncare24.location.service.LocationSourceQueryService;
import com.oncare.oncare24.user.entity.User;
import com.oncare.oncare24.user.entity.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LocationSourceQueryControllerTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void findLocationRecordsReturnsSourceDtoWithoutCryptoFields() throws Exception {
        LocationSourceQueryService queryService = mock(LocationSourceQueryService.class);
        LocationSourceQueryController controller = new LocationSourceQueryController(queryService);
        User elder = user(1L, UserRole.ELDER);
        LocalDateTime from = LocalDateTime.of(2026, 5, 11, 0, 0);
        LocalDateTime to = LocalDateTime.of(2026, 5, 12, 0, 0);
        when(queryService.findLocationRecords(1L, 1L, from, to))
                .thenReturn(List.of(new LocationSourceResponse(
                        new BigDecimal("37.5665"),
                        new BigDecimal("126.9780"),
                        20.0,
                        LocalDateTime.of(2026, 5, 11, 21, 30),
                        LocationReportSource.BACKGROUND_SCHEDULED
                )));

        ApiResponse<List<LocationSourceResponse>> response =
                controller.findLocationRecords(new CustomUserDetails(elder), 1L, from, to);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getData()).hasSize(1);
        assertThat(response.getData().get(0).latitude()).isEqualByComparingTo("37.5665");
        verify(queryService).findLocationRecords(1L, 1L, from, to);
        assertNoCryptoFields(response);
    }

    @Test
    void findDeviceStatusRecordsReturnsSourceDtoWithoutCryptoFields() throws Exception {
        LocationSourceQueryService queryService = mock(LocationSourceQueryService.class);
        LocationSourceQueryController controller = new LocationSourceQueryController(queryService);
        User elder = user(1L, UserRole.ELDER);
        LocalDateTime from = LocalDateTime.of(2026, 5, 11, 0, 0);
        LocalDateTime to = LocalDateTime.of(2026, 5, 12, 0, 0);
        when(queryService.findDeviceStatusRecords(1L, 1L, from, to))
                .thenReturn(List.of(new DeviceStatusSourceResponse(
                        DeviceState.DISCONNECTED,
                        LocalDateTime.of(2026, 5, 11, 21, 30),
                        LocalDateTime.of(2026, 5, 11, 22, 5),
                        LocalDateTime.of(2026, 5, 11, 22, 5)
                )));

        ApiResponse<List<DeviceStatusSourceResponse>> response =
                controller.findDeviceStatusRecords(new CustomUserDetails(elder), 1L, from, to);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getData()).hasSize(1);
        assertThat(response.getData().get(0).deviceStatus()).isEqualTo(DeviceState.DISCONNECTED);
        verify(queryService).findDeviceStatusRecords(1L, 1L, from, to);
        assertNoCryptoFields(response);
    }

    private void assertNoCryptoFields(Object response) throws Exception {
        String json = objectMapper.writeValueAsString(response);
        JsonNode root = objectMapper.readTree(json);
        assertThat(root.findValues("encryptedPackage")).isEmpty();
        assertThat(root.findValues("aadJson")).isEmpty();
        assertThat(root.findValues("dataKeyId")).isEmpty();
        assertThat(root.findValues("sourceTable")).isEmpty();
        assertThat(root.findValues("sourceId")).isEmpty();
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
