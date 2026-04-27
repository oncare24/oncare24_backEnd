package com.oncare.oncare24.auth.security;

import com.oncare.oncare24.user.entity.User;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/**
 * Spring Security의 {@link UserDetails} 구현체.
 * <p>
 * 컨트롤러에서 {@code @AuthenticationPrincipal CustomUserDetails userDetails}로 받아서
 * {@code userDetails.getUserId()} 호출 가능.
 * <p>
 * <b>중요</b>: {@link #getUsername()}는 userId 문자열을 반환합니다 (전화번호 X).
 * JWT subject로 userId를 쓰기 때문에, UserDetailsService도 userId 기준으로 조회합니다.
 */
@Getter
@RequiredArgsConstructor
public class CustomUserDetails implements UserDetails {

    private final User user;

    public Long getUserId() {
        return user.getId();
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()));
    }

    @Override
    public String getPassword() {
        return user.getPassword();
    }

    @Override
    public String getUsername() {
        return String.valueOf(user.getId());
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}
