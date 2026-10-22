package com.tagup.backend.notification.entity;

import com.tagup.backend.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/** 푸시 발송 대상 기기. 한 유저가 여러 기기를 가질 수 있다. */
@Entity
@Table(name = "user_devices", indexes = @Index(name = "idx_user_devices_user_id", columnList = "user_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class UserDevice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** Expo push token (ExponentPushToken[...]). 기기 고유값이라 unique */
    @Column(nullable = false, unique = true, length = 255)
    private String pushToken;

    @Column(length = 20)
    private String platform;

    @CreatedDate
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;

    @Builder
    public UserDevice(User user, String pushToken, String platform) {
        this.user = user;
        this.pushToken = pushToken;
        this.platform = platform;
    }

    /** 같은 토큰이 다른 계정에서 재등록될 수 있음 (기기 공유/재로그인) */
    public void reassignTo(User user, String platform) {
        this.user = user;
        this.platform = platform;
    }
}
