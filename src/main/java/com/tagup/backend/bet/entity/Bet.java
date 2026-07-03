package com.tagup.backend.bet.entity;

import com.tagup.backend.game.entity.Game;
import com.tagup.backend.room.entity.Room;
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

@Entity
@Table(name = "bets", indexes = {
        @Index(name = "idx_bets_room_id", columnList = "room_id"),
        @Index(name = "idx_bets_game_id", columnList = "game_id")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class Bet {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "proposer_id", nullable = false)
    private User proposer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "receiver_id", nullable = false)
    private User receiver;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id", nullable = false)
    private Room room;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "game_id", nullable = false)
    private Game game;

    // 내기 내용: "커피 한 잔", "점심 밥"
    @Column(nullable = false, length = 100)
    private String content;

    // 제안자가 응원하는 팀 ID (홈 or 원정)
    @Column(nullable = false)
    private Long betOnTeamId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BetStatus status;

    // 제안자 기준 결과 (정산 후 기록)
    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private BetResult proposerResult;

    @CreatedDate
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;

    @Builder
    public Bet(User proposer, User receiver, Room room, Game game,
               String content, Long betOnTeamId) {
        this.proposer = proposer;
        this.receiver = receiver;
        this.room = room;
        this.game = game;
        this.content = content;
        this.betOnTeamId = betOnTeamId;
        this.status = BetStatus.PENDING;
    }

    public void accept() {
        this.status = BetStatus.ACCEPTED;
    }

    public void cancel() {
        this.status = BetStatus.CANCELLED;
    }

    public void settle(BetResult result) {
        this.status = BetStatus.FINISHED;
        this.proposerResult = result;
    }
}
