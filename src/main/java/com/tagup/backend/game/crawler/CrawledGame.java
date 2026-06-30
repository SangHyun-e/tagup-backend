package com.tagup.backend.game.crawler;

import com.tagup.backend.game.entity.GameStatus;
import lombok.Builder;

import java.time.LocalDate;
import java.time.LocalTime;

@Builder
public record CrawledGame(
        String kboGameId,
        LocalDate gameDate,
        LocalTime gameTime,
        String homeTeamShortName,
        String awayTeamShortName,
        GameStatus status,
        Integer homeScore,
        Integer awayScore,
        String stadium
) {}
