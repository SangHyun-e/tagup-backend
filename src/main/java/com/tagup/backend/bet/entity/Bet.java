package com.tagup.backend.bet.entity;

import com.tagup.backend.game.entity.Game;
import com.tagup.backend.game.live.AtBatResult;
import com.tagup.backend.game.live.HalfInning;
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

    // 오픈 배팅: 콜하기 전까지 비어 있음
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "receiver_id")
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

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BetType type;

    // 승패 배팅: 제안자가 응원하는 팀 ID (홈 or 원정). 타석 배팅에서는 비어 있다
    private Long betOnTeamId;

    // ---- 아래는 타석 배팅(AT_BAT)에서만 채워진다 ----

    /** 제안자가 건 타석 결과 (아웃/세이프) */
    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private AtBatResult betOnAtBatResult;

    /**
     * 어느 타석에 걸었는지. KBO가 타석 ID를 주지 않으므로
     * <b>이닝 + 초/말 + 타자 이름</b>으로 식별한다.
     */
    private Integer atBatInning;

    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private HalfInning atBatHalf;

    @Column(length = 30)
    private String atBatBatter;

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
    public Bet(User proposer, Room room, Game game,
               String content, Long betOnTeamId) {
        this.proposer = proposer;
        this.room = room;
        this.game = game;
        this.content = content;
        this.betOnTeamId = betOnTeamId;
        this.type = BetType.WIN_LOSE;
        this.status = BetStatus.PENDING;
    }

    /**
     * 타석 배팅 생성.
     *
     * @param betOnAtBatResult 제안자가 건 결과 — {@link AtBatResult#OUT} 또는 {@link AtBatResult#SAFE}.
     *                         콜한 사람은 자동으로 반대편에 선다.
     */
    public static Bet forAtBat(User proposer, Room room, Game game, String content,
                               AtBatResult betOnAtBatResult,
                               Integer inning, HalfInning half, String batter) {
        Bet bet = new Bet();
        bet.proposer = proposer;
        bet.room = room;
        bet.game = game;
        bet.content = content;
        bet.type = BetType.AT_BAT;
        bet.status = BetStatus.PENDING;
        bet.betOnAtBatResult = betOnAtBatResult;
        bet.atBatInning = inning;
        bet.atBatHalf = half;
        bet.atBatBatter = batter;
        return bet;
    }

    /** 이 배팅이 가리키는 타석인지 */
    public boolean matchesAtBat(Integer inning, HalfInning half, String batter) {
        return type == BetType.AT_BAT
                && java.util.Objects.equals(atBatInning, inning)
                && atBatHalf == half
                && atBatBatter != null && atBatBatter.equals(batter);
    }

    public void accept(User receiver) {
        this.receiver = receiver;
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
