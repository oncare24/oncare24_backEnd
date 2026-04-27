package com.oncare.oncare24.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * JPA Auditing 활성화.
 * <p>
 * {@link com.oncare.oncare24.global.common.BaseTimeEntity}의 @CreatedDate, @LastModifiedDate가
 * 자동으로 채워지려면 이 설정이 필요합니다.
 * <p>
 * 별도 클래스로 분리한 이유: @DataJpaTest에서 Auditing이 자동 실행되는 걸 막고, 필요할 때만
 * 명시적으로 import해서 사용할 수 있게 하기 위함.
 */
@Configuration
@EnableJpaAuditing
public class JpaAuditingConfig {
}
