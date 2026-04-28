package com.oncare.oncare24.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * @Scheduled 활성화.
 * <p>
 * <b>왜 별도 Config 클래스</b>: Application 클래스에 @EnableScheduling을 붙이면
 * 스케줄링이 항상 켜져서 통합 테스트 시 배치가 의도치 않게 도는 부작용이 있음.
 * 별도 Config로 분리해두면 테스트에서 @MockBean 또는 프로파일로 끄기 쉬움.
 *
 * <b>스레드 풀 크기</b>: 기본값(1)을 사용. 현재 배치 작업이 2종(5분/1분)이고 동시 실행 안 하니까 충분.
 * 작업 늘어나면 SchedulingConfigurer 구현해서 ThreadPoolTaskScheduler 명시적 설정.
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}