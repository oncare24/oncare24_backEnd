package com.oncare.oncare24.auth.service;

import com.oncare.oncare24.auth.dto.SignUpRequest;
import com.oncare.oncare24.auth.jwt.JwtProperties;
import com.oncare.oncare24.auth.jwt.JwtProvider;
import com.oncare.oncare24.inactivity.service.InactivityRuleProvisionService;
import com.oncare.oncare24.security.key.MlKemKeyProvisionService;
import com.oncare.oncare24.user.entity.User;
import com.oncare.oncare24.user.entity.UserRole;
import com.oncare.oncare24.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final Long ELDER_ID = 88L;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtProvider jwtProvider;

    @Mock
    private JwtProperties jwtProperties;

    @Mock
    private RefreshTokenService refreshTokenService;

    @Mock
    private MlKemKeyProvisionService mlKemKeyProvisionService;

    @Mock
    private InactivityRuleProvisionService inactivityRuleProvisionService;

    @Mock
    private com.oncare.oncare24.notification.service.NotificationPreferenceProvisionService notificationPreferenceProvisionService;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(
                userRepository,
                passwordEncoder,
                jwtProvider,
                jwtProperties,
                refreshTokenService,
                mlKemKeyProvisionService,
                inactivityRuleProvisionService,
                notificationPreferenceProvisionService
        );
    }

    @Test
    void signUpProvisionsDefaultInactivityRuleForElder() {
        when(userRepository.existsByPhone("01012345678")).thenReturn(false);
        when(passwordEncoder.encode("123456")).thenReturn("encoded");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> withId(invocation.getArgument(0), ELDER_ID));

        var response = authService.signUp(new SignUpRequest(
                "01012345678",
                "123456",
                "ward",
                UserRole.ELDER,
                75,
                false
        ));

        assertThat(response.userId()).isEqualTo(ELDER_ID);
        verify(mlKemKeyProvisionService).provisionUserMlKemKey(ELDER_ID);
        verify(inactivityRuleProvisionService).provisionDefaultRule(ELDER_ID);
    }

    @Test
    void signUpDoesNotProvisionInactivityRuleForGuardian() {
        when(userRepository.existsByPhone("01087654321")).thenReturn(false);
        when(passwordEncoder.encode("123456")).thenReturn("encoded");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> withId(invocation.getArgument(0), 99L));

        authService.signUp(new SignUpRequest(
                "01087654321",
                "123456",
                "guardian",
                UserRole.GUARDIAN,
                null,
                null
        ));

        verify(inactivityRuleProvisionService, never()).provisionDefaultRule(any());
    }

    private User withId(User user, Long id) {
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }
}
