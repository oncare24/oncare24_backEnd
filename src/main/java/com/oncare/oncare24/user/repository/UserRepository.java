package com.oncare.oncare24.user.repository;

import com.oncare.oncare24.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByPhone(String phone);

    boolean existsByPhone(String phone);

    boolean existsByEmail(String email);

    /**
     * 무효한 FCM 토큰을 가진 모든 유저의 토큰을 null로 정리.
     * FirebaseFcmSender 가 UNREGISTERED 응답 받았을 때 호출.
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE User u SET u.fcmToken = NULL WHERE u.fcmToken = :token")
    int clearFcmToken(@Param("token") String token);
}
