package com.oncare.oncare24.analysis.controller;

import com.oncare.oncare24.analysis.dto.AnalysisStateItemResponse;
import com.oncare.oncare24.analysis.dto.AnalysisStateResponse;
import com.oncare.oncare24.analysis.service.AnalysisStateQueryService;
import com.oncare.oncare24.auth.security.CustomUserDetails;
import com.oncare.oncare24.global.response.ApiResponse;
import com.oncare.oncare24.user.entity.User;
import com.oncare.oncare24.user.entity.UserRole;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AnalysisStateControllerTest {

    @Test
    void findWardAnalysisStateReturnsLatestMedicationAndInactivityState() {
        AnalysisStateQueryService queryService = mock(AnalysisStateQueryService.class);
        AnalysisStateController controller = new AnalysisStateController(queryService);
        User elder = user(1L, UserRole.ELDER);
        AnalysisStateResponse serviceResponse = new AnalysisStateResponse(
                1L,
                new AnalysisStateItemResponse(0, "ON_TIME", LocalDateTime.of(2026, 5, 11, 21, 30)),
                new AnalysisStateItemResponse(1, "INACTIVE", LocalDateTime.of(2026, 5, 11, 21, 30))
        );
        when(queryService.findWardAnalysisState(1L, 1L)).thenReturn(serviceResponse);

        ApiResponse<AnalysisStateResponse> response = controller.findWardAnalysisState(
                new CustomUserDetails(elder),
                1L
        );

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getData().wardId()).isEqualTo(1L);
        assertThat(response.getData().medication().statusCode()).isZero();
        assertThat(response.getData().medication().status()).isEqualTo("ON_TIME");
        assertThat(response.getData().inactivity().statusCode()).isEqualTo(1);
        assertThat(response.getData().inactivity().status()).isEqualTo("INACTIVE");
        verify(queryService).findWardAnalysisState(1L, 1L);
    }

    private User user(Long id, UserRole role) {
        User user = User.builder()
                .phone("010" + id)
                .password("encoded")
                .name(role.name().toLowerCase())
                .role(role)
                .build();
        org.springframework.test.util.ReflectionTestUtils.setField(user, "id", id);
        return user;
    }
}
