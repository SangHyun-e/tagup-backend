package com.tagup.backend.room.entity;

import com.tagup.backend.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "rooms")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class Room {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false, length = 6)
    private String tagCode;

    // Firestore 채팅 경로 키 (rooms/{chatKey}/messages).
    // 숫자 방 ID는 DB 초기화·행 삭제 시 재사용될 수 있어 채팅 경로에는 UUID를 쓴다
    @Column(unique = true, nullable = false, updatable = false, length = 36)
    private String chatKey;

    @Column(nullable = false, length = 50)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    @Column(nullable = false)
    private boolean chatEnabled = false;

    @CreatedDate
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @Builder
    public Room(String tagCode, String name, User createdBy) {
        this.tagCode = tagCode;
        this.name = name;
        this.createdBy = createdBy;
        this.chatKey = UUID.randomUUID().toString();
    }

    public void setChatEnabled(boolean chatEnabled) {
        this.chatEnabled = chatEnabled;
    }
}
