package com.tagup.backend.game.dto;

import com.tagup.backend.game.entity.Game;
import com.tagup.backend.game.entity.GameStatus;

import java.time.LocalDate;
import java.time.LocalTime;

public record GameResponse(
        Long id,
        String kboGameId,
        LocalDate gameDate,
        LocalTime gameTime,
        TeamInfo homeTeam,
        TeamInfo awayTeam,
        GameStatus status,
        Integer homeScore,
        Integer awayScore,
        Integer inning,
        String stadium
) {
    public record TeamInfo(Long id, String name, String shortName, String logoUrl) {}

    public static GameResponse from(Game game) {
        return new GameResponse(
                game.getId(),
                game.getKboGameId(),
                game.getGameDate(),
                game.getGameTime(),
                new TeamInfo(
                        game.getHomeTeam().getId(),
                        game.getHomeTeam().getName(),
                        game.getHomeTeam().getShortName(),
                        game.getHomeTeam().getLogoUrl()
                ),
                new TeamInfo(
                        game.getAwayTeam().getId(),
                        game.getAwayTeam().getName(),
                        game.getAwayTeam().getShortName(),
                        game.getAwayTeam().getLogoUrl()
                ),
                game.getStatus(),
                game.getHomeScore(),
                game.getAwayScore(),
                game.getInning(),
                game.getStadium()
        );
    }
}
