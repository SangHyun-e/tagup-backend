package com.tagup.backend.game.entity;

import com.tagup.backend.team.entity.Team;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Entity
@Table(name = "games", indexes = {
        @Index(name = "idx_games_game_date", columnList = "game_date"),
        @Index(name = "idx_games_kbo_game_id", columnList = "kbo_game_id", unique = true)
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class Game {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "kbo_game_id", unique = true, nullable = false)
    private String kboGameId;

    @Column(name = "game_date", nullable = false)
    private LocalDate gameDate;

    @Column(name = "game_time")
    private LocalTime gameTime;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "home_team_id", nullable = false)
    private Team homeTeam;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "away_team_id", nullable = false)
    private Team awayTeam;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private GameStatus status;

    private Integer homeScore;
    private Integer awayScore;

    @Column(length = 50)
    private String stadium;

    @LastModifiedDate
    private LocalDateTime updatedAt;

    @Builder
    public Game(String kboGameId, LocalDate gameDate, LocalTime gameTime,
                Team homeTeam, Team awayTeam, GameStatus status,
                Integer homeScore, Integer awayScore, String stadium) {
        this.kboGameId = kboGameId;
        this.gameDate = gameDate;
        this.gameTime = gameTime;
        this.homeTeam = homeTeam;
        this.awayTeam = awayTeam;
        this.status = status;
        this.homeScore = homeScore;
        this.awayScore = awayScore;
        this.stadium = stadium;
    }

    public void updateResult(GameStatus status, Integer homeScore, Integer awayScore) {
        this.status = status;
        this.homeScore = homeScore;
        this.awayScore = awayScore;
    }
}
