package com.tagup.backend.bet.dto;

import com.tagup.backend.bet.entity.Bet;
import com.tagup.backend.bet.entity.BetResult;
import com.tagup.backend.bet.entity.BetStatus;
import com.tagup.backend.game.dto.GameResponse;

import java.time.LocalDateTime;

public record BetResponse(
        Long id,
        ProposerInfo proposer,
        ReceiverInfo receiver,
        Long betOnTeamId,
        String content,
        BetStatus status,
        BetResult proposerResult,
        GameSummary game,
        LocalDateTime createdAt
) {
    public record ProposerInfo(Long id, String nickname) {}
    public record ReceiverInfo(Long id, String nickname) {}
    public record GameSummary(Long id, String homeTeam, String awayTeam, String gameDate) {}

    public static BetResponse from(Bet bet) {
        return new BetResponse(
                bet.getId(),
                new ProposerInfo(bet.getProposer().getId(), bet.getProposer().getNickname()),
                new ReceiverInfo(bet.getReceiver().getId(), bet.getReceiver().getNickname()),
                bet.getBetOnTeamId(),
                bet.getContent(),
                bet.getStatus(),
                bet.getProposerResult(),
                new GameSummary(
                        bet.getGame().getId(),
                        bet.getGame().getHomeTeam().getShortName(),
                        bet.getGame().getAwayTeam().getShortName(),
                        bet.getGame().getGameDate().toString()
                ),
                bet.getCreatedAt()
        );
    }
}
