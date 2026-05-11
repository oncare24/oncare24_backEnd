package com.oncare.oncare24.analysis.service;

import com.oncare.oncare24.analysis.dto.AnalysisStateResponse;
import com.oncare.oncare24.analysis.entity.AnalysisType;
import com.oncare.oncare24.analysis.entity.WardAnalysisState;
import com.oncare.oncare24.analysis.repository.WardAnalysisStateRepository;
import com.oncare.oncare24.global.exception.CustomException;
import com.oncare.oncare24.global.exception.ErrorCode;
import com.oncare.oncare24.guardian.entity.GuardianWardStatus;
import com.oncare.oncare24.guardian.repository.GuardianWardRepository;
import com.oncare.oncare24.user.entity.User;
import com.oncare.oncare24.user.entity.UserRole;
import com.oncare.oncare24.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AnalysisStateQueryServiceTest {

    private static final Long WARD_ID = 1L;
    private static final Long GUARDIAN_ID = 2L;

    private WardAnalysisStateRepository wardAnalysisStateRepository;
    private UserRepository userRepository;
    private GuardianWardRepository guardianWardRepository;
    private AnalysisStateQueryService queryService;

    @BeforeEach
    void setUp() {
        wardAnalysisStateRepository = mock(WardAnalysisStateRepository.class);
        userRepository = mock(UserRepository.class);
        guardianWardRepository = mock(GuardianWardRepository.class);
        queryService = new AnalysisStateQueryService(
                wardAnalysisStateRepository,
                userRepository,
                guardianWardRepository
        );
    }

    @Test
    void findWardAnalysisStateMapsMedicationAndInactivityStatus() {
        LocalDateTime analyzedAt = LocalDateTime.of(2026, 5, 11, 21, 30);
        stubElderAccess();
        when(wardAnalysisStateRepository.findByWardIdAndAnalysisType(WARD_ID, AnalysisType.MEDICATION))
                .thenReturn(Optional.of(state(AnalysisType.MEDICATION, 0, analyzedAt)));
        when(wardAnalysisStateRepository.findByWardIdAndAnalysisType(WARD_ID, AnalysisType.INACTIVITY))
                .thenReturn(Optional.of(state(AnalysisType.INACTIVITY, 1, analyzedAt)));

        AnalysisStateResponse response = queryService.findWardAnalysisState(WARD_ID, WARD_ID);

        assertThat(response.wardId()).isEqualTo(WARD_ID);
        assertThat(response.medication().statusCode()).isZero();
        assertThat(response.medication().status()).isEqualTo("ON_TIME");
        assertThat(response.medication().analyzedAt()).isEqualTo(analyzedAt);
        assertThat(response.inactivity().statusCode()).isEqualTo(1);
        assertThat(response.inactivity().status()).isEqualTo("INACTIVE");
        assertThat(response.inactivity().analyzedAt()).isEqualTo(analyzedAt);
    }

    @Test
    void findWardAnalysisStateReturnsOnlyExistingState() {
        stubElderAccess();
        when(wardAnalysisStateRepository.findByWardIdAndAnalysisType(WARD_ID, AnalysisType.MEDICATION))
                .thenReturn(Optional.of(state(AnalysisType.MEDICATION, 2, LocalDateTime.of(2026, 5, 11, 22, 0))));
        when(wardAnalysisStateRepository.findByWardIdAndAnalysisType(WARD_ID, AnalysisType.INACTIVITY))
                .thenReturn(Optional.empty());

        AnalysisStateResponse response = queryService.findWardAnalysisState(WARD_ID, WARD_ID);

        assertThat(response.medication().statusCode()).isEqualTo(2);
        assertThat(response.medication().status()).isEqualTo("MISSED");
        assertThat(response.inactivity()).isNull();
    }

    @Test
    void findWardAnalysisStateReturnsNullItemsWhenNoStateExists() {
        stubElderAccess();
        when(wardAnalysisStateRepository.findByWardIdAndAnalysisType(WARD_ID, AnalysisType.MEDICATION))
                .thenReturn(Optional.empty());
        when(wardAnalysisStateRepository.findByWardIdAndAnalysisType(WARD_ID, AnalysisType.INACTIVITY))
                .thenReturn(Optional.empty());

        AnalysisStateResponse response = queryService.findWardAnalysisState(WARD_ID, WARD_ID);

        assertThat(response.medication()).isNull();
        assertThat(response.inactivity()).isNull();
    }

    @Test
    void acceptedGuardianCanFindWardAnalysisState() {
        User guardian = user(GUARDIAN_ID, UserRole.GUARDIAN);
        User ward = user(WARD_ID, UserRole.ELDER);
        when(userRepository.findById(GUARDIAN_ID)).thenReturn(Optional.of(guardian));
        when(userRepository.findById(WARD_ID)).thenReturn(Optional.of(ward));
        when(guardianWardRepository.existsByGuardianIdAndWardIdAndStatus(
                GUARDIAN_ID,
                WARD_ID,
                GuardianWardStatus.ACCEPTED
        )).thenReturn(true);
        when(wardAnalysisStateRepository.findByWardIdAndAnalysisType(WARD_ID, AnalysisType.MEDICATION))
                .thenReturn(Optional.empty());
        when(wardAnalysisStateRepository.findByWardIdAndAnalysisType(WARD_ID, AnalysisType.INACTIVITY))
                .thenReturn(Optional.of(state(AnalysisType.INACTIVITY, 2, LocalDateTime.of(2026, 5, 11, 23, 0))));

        AnalysisStateResponse response = queryService.findWardAnalysisState(GUARDIAN_ID, WARD_ID);

        assertThat(response.medication()).isNull();
        assertThat(response.inactivity().statusCode()).isEqualTo(2);
        assertThat(response.inactivity().status()).isEqualTo("UNKNOWN");
    }

    @Test
    void unlinkedGuardianCannotFindWardAnalysisState() {
        User guardian = user(GUARDIAN_ID, UserRole.GUARDIAN);
        User ward = user(WARD_ID, UserRole.ELDER);
        when(userRepository.findById(GUARDIAN_ID)).thenReturn(Optional.of(guardian));
        when(userRepository.findById(WARD_ID)).thenReturn(Optional.of(ward));
        when(guardianWardRepository.existsByGuardianIdAndWardIdAndStatus(
                GUARDIAN_ID,
                WARD_ID,
                GuardianWardStatus.ACCEPTED
        )).thenReturn(false);

        assertThatThrownBy(() -> queryService.findWardAnalysisState(GUARDIAN_ID, WARD_ID))
                .isInstanceOf(CustomException.class)
                .extracting(error -> ((CustomException) error).getErrorCode())
                .isEqualTo(ErrorCode.NOT_LINKED_TO_WARD);
        verifyNoInteractions(wardAnalysisStateRepository);
    }

    private void stubElderAccess() {
        User elder = user(WARD_ID, UserRole.ELDER);
        when(userRepository.findById(WARD_ID)).thenReturn(Optional.of(elder));
    }

    private WardAnalysisState state(AnalysisType analysisType, int statusCode, LocalDateTime analyzedAt) {
        return WardAnalysisState.builder()
                .wardId(WARD_ID)
                .analysisType(analysisType)
                .statusCode(statusCode)
                .analyzedAt(analyzedAt)
                .build();
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
