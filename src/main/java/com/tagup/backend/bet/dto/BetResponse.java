package com.tagup.backend.bet.dto;

import com.tagup.backend.bet.entity.Bet;
import com.tagup.backend.bet.entity.BetResult;
import com.tagup.backend.bet.entity.BetStatus;
import com.tagup.backend.bet.entity.BetType;
import com.tagup.backend.game.live.AtBatResult;
import com.tagup.backend.team.entity.Team;

import java.time.LocalDateTime;

public record BetResponse(
        Long id,
        BetType type,
        ProposerInfo proposer,
        ReceiverInfo receiver,
        /** 승패 배팅에서만 채워진다 */
        Long betOnTeamId,
        TeamInfo betOnTeam,
        /** 타석 배팅에서만 채워진다 */
        AtBatInfo atBat,
        String content,
        BetStatus status,
        BetResult proposerResult,
        GameSummary game,
        LocalDateTime createdAt
) {
    public record ProposerInfo(Long id, String nickname) {}
    public record ReceiverInfo(Long id, String nickname) {}
    public record TeamInfo(Long id, String shortName) {}
    public record GameSummary(Long id, String homeTeam, String awayTeam, String gameDate) {}

    /** 어느 타석에 무엇을 걸었는지 */
    public record AtBatInfo(Integer inning, String half, String batter, AtBatResult betOnResult) {}

    public static BetResponse from(Bet bet) {
        Team homeTeam = bet.getGame().getHomeTeam();
        Team awayTeam = bet.getGame().getAwayTeam();
        Team betTeam = bet.getBetOnTeamId() == null ? null
                : bet.getBetOnTeamId().equals(homeTeam.getId()) ? homeTeam : awayTeam;

        return new BetResponse(
                bet.getId(),
                bet.getType(),
                new ProposerInfo(bet.getProposer().getId(), bet.getProposer().getNickname()),
                bet.getReceiver() != null
                        ? new ReceiverInfo(bet.getReceiver().getId(), bet.getReceiver().getNickname())
                        : null,
                bet.getBetOnTeamId(),
                betTeam == null ? null : new TeamInfo(betTeam.getId(), betTeam.getShortName()),
                bet.getBetOnAtBatResult() == null ? null : new AtBatInfo(
                        bet.getAtBatInning(),
                        bet.getAtBatHalf() == null ? null : bet.getAtBatHalf().korean(),
                        bet.getAtBatBatter(),
                        bet.getBetOnAtBatResult()),
                bet.getContent(),
                bet.getStatus(),
                bet.getProposerResult(),
                new GameSummary(
                        bet.getGame().getId(),
                        homeTeam.getShortName(),
                        awayTeam.getShortName(),
                        bet.getGame().getGameDate().toString()
                ),
                bet.getCreatedAt()
        );
    }
}
