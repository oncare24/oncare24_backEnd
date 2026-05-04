package com.oncare.oncare24.global.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.FileInputStream;
import java.io.IOException;

/**
 * Firebase Admin SDK 초기화.
 * <p>
 * <b>동작 조건</b>: {@code fcm.enabled=true} 일 때만 빈 등록.
 * 끄면 Firebase 초기화도 안 하므로 키 파일이 없어도 앱이 뜸 (개발 환경 친화적).
 *
 * <b>초기화 시점</b>: @PostConstruct 에서 한 번만. FirebaseApp.initializeApp()을
 * 두 번 호출하면 IllegalStateException 발생하므로 instance 존재 여부 먼저 체크.
 *
 * <b>credentials 파일</b>: 프로젝트 루트의 {@code firebase/service-account.json}.
 * 절대 깃에 커밋하지 않음 (.gitignore 필수).
 */
@Slf4j
@Configuration
@ConditionalOnProperty(name = "fcm.enabled", havingValue = "true")
@RequiredArgsConstructor
public class FirebaseConfig {

    @Value("${fcm.credentials-path}")
    private String credentialsPath;

    @Value("${fcm.project-id}")
    private String projectId;

    @PostConstruct
    public void initialize() throws IOException {
        if (!FirebaseApp.getApps().isEmpty()) {
            log.info("[FCM-INIT] FirebaseApp already initialized. skip.");
            return;
        }

        try (FileInputStream serviceAccount = new FileInputStream(credentialsPath)) {
            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                    .setProjectId(projectId)
                    .build();

            FirebaseApp.initializeApp(options);
            log.info("[FCM-INIT] FirebaseApp initialized. projectId={}", projectId);
        }
    }

    @Bean
    public FirebaseMessaging firebaseMessaging() {
        return FirebaseMessaging.getInstance();
    }
}