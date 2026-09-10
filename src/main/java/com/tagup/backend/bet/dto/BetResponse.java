package com.tagup.backend.bet.dto;

import com.tagup.backend.bet.entity.Bet;
import com.tagup.backend.bet.entity.BetResult;
import com.tagup.backend.bet.entity.BetStatus;
import com.tagup.backend.team.entity.Team;

import java.time.LocalDateTime;

public record BetResponse(
        Long id,
        ProposerInfo proposer,
        ReceiverInfo receiver,
        Long betOnTeamId,
        TeamInfo betOnTeam,
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

    public static BetResponse from(Bet bet) {
        Team homeTeam = bet.getGame().getHomeTeam();
        Team awayTeam = bet.getGame().getAwayTeam();
        Team betTeam = bet.getBetOnTeamId().equals(homeTeam.getId()) ? homeTeam : awayTeam;

        return new BetResponse(
                bet.getId(),
                new ProposerInfo(bet.getProposer().getId(), bet.getProposer().getNickname()),
                bet.getReceiver() != null
                        ? new ReceiverInfo(bet.getReceiver().getId(), bet.getReceiver().getNickname())
                        : null,
                bet.getBetOnTeamId(),
                new TeamInfo(betTeam.getId(), betTeam.getShortName()),
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
