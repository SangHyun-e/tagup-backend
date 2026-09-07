package com.tagup.backend.game.live;

import java.util.Objects;

/**
 * KBO {@code /ws/Main.asmx/GetKboGameList} 응답 1건(경기 하나)의 스냅샷.
 *
 * <p><b>필드 해석 주의</b> — {@code awayPlayer}/{@code homePlayer}는 "원정팀/홈팀의 현재 선수"이며
 * 타자·투수를 직접 가리키지 않는다. 공수는 {@link #half}로 판별한다:
 * <ul>
 *   <li>초(TOP): 원정팀 공격 → {@code awayPlayer}가 타자, {@code homePlayer}가 투수</li>
 *   <li>말(BOTTOM): 홈팀 공격 → {@code homePlayer}가 타자, {@code awayPlayer}가 투수</li>
 * </ul>
 * (2026-09-01 관측으로 검증: 1회초 박해민(LG 타자)/잭로그(두산 투수), 1회말 임찬규(LG 투수)/박찬호(두산 타자))
 */
public record LiveGameSnapshot(
        LiveGameState state,
        Integer inning,
        HalfInning half,
        Integer awayScore,
        Integer homeScore,
        Integer strike,
        Integer ball,
        Integer out,
        String awayPlayer,
        String homePlayer,
        Integer runner1,
        Integer runner2,
        Integer runner3
) {

    public boolean isLive() {
        return state == LiveGameState.IN_PROGRESS;
    }

    /** 현재 타석에 선 타자. 판별 불가 시 null */
    public String batter() {
        if (half == null) return null;
        return half == HalfInning.TOP ? awayPlayer : homePlayer;
    }

    /** 현재 마운드의 투수. 판별 불가 시 null */
    public String pitcher() {
        if (half == null) return null;
        return half == HalfInning.TOP ? homePlayer : awayPlayer;
    }

    public int totalScore() {
        return zero(awayScore) + zero(homeScore);
    }

    /** 같은 공격 이닝(회 + 초/말)인지 */
    public boolean sameHalfInning(LiveGameSnapshot other) {
        return other != null
                && Objects.equals(inning, other.inning)
                && half == other.half;
    }

    /** 주자 상황이 바뀌었는지 (1·2·3루 주자의 타순 번호 조합 비교) */
    public boolean sameRunners(LiveGameSnapshot other) {
        return other != null
                && Objects.equals(runner1, other.runner1)
                && Objects.equals(runner2, other.runner2)
                && Objects.equals(runner3, other.runner3);
    }

    private static int zero(Integer v) {
        return v == null ? 0 : v;
    }
}
