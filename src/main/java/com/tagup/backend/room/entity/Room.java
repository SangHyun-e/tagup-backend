package com.tagup.backend.room.entity;

import com.tagup.backend.game.entity.Game;
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

    /**
     * 이 더그아웃이 <b>오늘</b> 같이 보는 경기.
     *
     * <p>더그아웃은 영구 그룹이고 경기는 매일 바뀌므로, 팀을 방에 고정하지 않고
     * 날마다 이 값을 갈아끼운다. 스코어보드·라이브 피드·타석 배팅이 이 경기를 기준으로 붙고,
     * 배팅을 만들 때 경기를 매번 고르지 않아도 된다.
     *
     * <p>{@link com.tagup.backend.room.scheduler.RoomWatchingGameScheduler}가 매일 아침
     * 멤버들의 응원팀을 보고 자동으로 정하며, 사용자가 직접 바꿀 수도 있다. 비어 있을 수 있다.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "watching_game_id")
    private Game watchingGame;

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

    /** @param game null이면 관전 경기 해제 */
    public void setWatchingGame(Game game) {
        this.watchingGame = game;
    }
}
