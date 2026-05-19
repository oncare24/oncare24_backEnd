package com.oncare.oncare24.guardian.service;

import com.oncare.oncare24.guardian.dto.ReceivedInvitationResponse;
import com.oncare.oncare24.guardian.entity.GuardianWard;
import com.oncare.oncare24.guardian.repository.GuardianWardRepository;
import com.oncare.oncare24.guardian.util.InviteCodeGenerator;
import com.oncare.oncare24.notification.sender.SmsSender;
import com.oncare.oncare24.notification.service.NotificationService;
import com.oncare.oncare24.security.envelope.KeyEnvelopeProvisionService;
import com.oncare.oncare24.user.entity.User;
import com.oncare.oncare24.user.entity.UserRole;
import com.oncare.oncare24.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.util.ReflectionTestUtils;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InvitationServiceTest {

    private static final Long WARD_ID = 10L;
    private static final Long GUARDIAN_ID = 20L;
    private static final Long INVITATION_ID = 30L;

    @Mock
    private GuardianWardRepository guardianWardRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private InviteCodeGenerator inviteCodeGenerator;
    @Mock
    private SmsSender smsSender;
    @Mock
    private NotificationService notificationService;
    @Mock
    private KeyEnvelopeProvisionService keyEnvelopeProvisionService;

    private InvitationService invitationService;

    @BeforeEach
    void setUp() {
        invitationService = new InvitationService(
                guardianWardRepository,
                userRepository,
                inviteCodeGenerator,
                smsSender,
                notificationService,
                keyEnvelopeProvisionService
        );
    }

    @Test
    void acceptProvisionsRetroactiveGuardianAccessForExistingWardLogs() {
        User ward = user(WARD_ID, UserRole.ELDER);
        User guardian = user(GUARDIAN_ID, UserRole.GUARDIAN);
        GuardianWard invitation = GuardianWard.builder()
                .wardId(WARD_ID)
                .guardianId(GUARDIAN_ID)
                .inviteCode("INV123")
                .relationship("family")
                .expiresAt(LocalDateTime.now().plusHours(1))
                .build();
        ReflectionTestUtils.setField(invitation, "id", INVITATION_ID);

        when(userRepository.findById(WARD_ID)).thenReturn(Optional.of(ward));
        when(userRepository.findById(GUARDIAN_ID)).thenReturn(Optional.of(guardian));
        when(guardianWardRepository.findById(INVITATION_ID)).thenReturn(Optional.of(invitation));

        ReceivedInvitationResponse response = invitationService.accept(WARD_ID, INVITATION_ID);

        assertThat(response.status().name()).isEqualTo("ACCEPTED");
        verify(keyEnvelopeProvisionService).provisionForAcceptedGuardian(WARD_ID, GUARDIAN_ID);
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
