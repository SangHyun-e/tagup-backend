package com.tagup.backend.game.dto;

import com.tagup.backend.game.live.HalfInning;
import com.tagup.backend.game.live.LiveScoreboard;

import java.time.Instant;

/**
 * 진행 중인 경기의 지금 상태. <b>경기가 진행 중이고 수집이 살아 있을 때만</b> 내려간다.
 *
 * <p>점수를 여기서 따로 주는 이유: KBO 일정 API는 경기 중 점수 칸을 0:0으로 고정해 보내므로
 * {@code GameResponse.homeScore/awayScore}는 경기가 끝나야 채워진다. 진행 중 점수는 이 필드를 쓸 것.
 */
public record LiveStateResponse(
        Integer inning,
        HalfInning half,
        Integer awayScore,
        Integer homeScore,
        Integer out,
        Integer ball,
        Integer strike,
        Bases bases,
        String batter,
        String pitcher,
        Instant updatedAt
) {

    /** 누가 나가 있는지 */
    public record Bases(boolean first, boolean second, boolean third) {}

    public static LiveStateResponse from(LiveScoreboard board) {
        return new LiveStateResponse(
                board.inning(), board.half(),
                board.awayScore(), board.homeScore(),
                board.out(), board.ball(), board.strike(),
                new Bases(board.firstBase(), board.secondBase(), board.thirdBase()),
                board.batter(), board.pitcher(), board.updatedAt());
    }
}
