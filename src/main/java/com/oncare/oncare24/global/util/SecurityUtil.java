package com.oncare.oncare24.global.util;

import com.oncare.oncare24.auth.security.CustomUserDetails;
import com.oncare.oncare24.global.exception.CustomException;
import com.oncare.oncare24.global.exception.ErrorCode;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * SecurityContext에서 현재 인증된 사용자 정보를 꺼내는 유틸리티.
 * <p>
 * 컨트롤러에서는 보통 {@code @AuthenticationPrincipal CustomUserDetails} 파라미터를 쓰지만,
 * Service/Component 같은 곳에서 현재 사용자 ID가 필요할 때 사용합니다.
 *
 * <pre>{@code
 * Long userId = SecurityUtil.getCurrentUserId();
 * }</pre>
 */
public final class SecurityUtil {

    private SecurityUtil() {
    }

    public static Long getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null
                || !auth.isAuthenticated()
                || !(auth.getPrincipal() instanceof CustomUserDetails details)) {
            throw new CustomException(ErrorCode.UNAUTHORIZED);
        }
        return details.getUserId();
    }
}
