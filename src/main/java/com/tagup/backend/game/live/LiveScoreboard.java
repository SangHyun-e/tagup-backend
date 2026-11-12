package com.tagup.backend.game.live;

import java.time.Duration;
import java.time.Instant;

/**
 * 진행 중인 경기의 <b>지금 상태</b> 한 장. 폴러가 받은 스냅샷을 앱이 읽을 모양으로 정리한 것이다.
 *
 * <p><b>왜 따로 두는가</b> — KBO 일정 API는 경기 중에 점수 칸을 0:0으로 고정해 내려준다.
 * 그 값을 믿었다가 경기 시작 3분 만에 "0:0 무승부"로 내기가 정산된 사고가 있어서,
 * 크롤러는 진행 중 경기의 점수를 아예 버린다. 진짜 점수는 15초마다 도는 폴러만 알고 있고,
 * 지금까지 그 값은 채팅 중계에만 쓰이고 사라졌다.
 *
 * <p>{@code updatedAt}을 함께 들고 다니는 이유는 <b>절전</b> 때문이다. 노트북이 자면 폴링이
 * 멈추는데, 그 사이의 점수를 "지금 점수"라고 보여주면 거짓말이 된다. 신선하지 않으면 없는 셈 친다.
 */
public record LiveScoreboard(
        Integer inning,
        HalfInning half,
        Integer awayScore,
        Integer homeScore,
        Integer out,
        Integer ball,
        Integer strike,
        boolean firstBase,
        boolean secondBase,
        boolean thirdBase,
        String batter,
        String pitcher,
        Instant updatedAt
) {

    public static LiveScoreboard of(LiveGameSnapshot snapshot, Instant at) {
        return new LiveScoreboard(
                snapshot.inning(), snapshot.half(),
                snapshot.awayScore(), snapshot.homeScore(),
                snapshot.out(), snapshot.ball(), snapshot.strike(),
                occupied(snapshot.runner1()), occupied(snapshot.runner2()), occupied(snapshot.runner3()),
                snapshot.batter(), snapshot.pitcher(), at);
    }

    /** 주자 칸에는 주자의 타순 번호가 들어온다. 0이나 빈 값이면 아무도 없다는 뜻이다 */
    private static boolean occupied(Integer runner) {
        return runner != null && runner != 0;
    }

    public boolean isFresh(Instant now, Duration ttl) {
        return updatedAt != null && !updatedAt.plus(ttl).isBefore(now);
    }
}
