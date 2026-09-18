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

    /**
     * {@code other}보다 <b>과거 상태</b>인지. 경기는 뒤로 가지 않으므로 true면 KBO가 옛 스냅샷을 준 것이다.
     *
     * <p>회·초말이 앞서거나 어느 팀 점수든 줄었으면 과거로 본다. <b>아웃 카운트는 보지 않는다</b> —
     * 공수 교대 순간 필드가 따로 갱신될 수 있어(타자 이름이 늦게 바뀌는 것처럼) 오판 위험이 크다.
     * 진행 중이 아닌 스냅샷끼리는 비교하지 않는다.
     */
    public boolean isBehind(LiveGameSnapshot other) {
        if (other == null || !isLive() || !other.isLive()) return false;
        if (zero(awayScore) < zero(other.awayScore) || zero(homeScore) < zero(other.homeScore)) {
            return true;
        }
        if (inning == null || other.inning == null || half == null || other.half == null) {
            return false;
        }
        if (!inning.equals(other.inning)) return inning < other.inning;
        return half == HalfInning.TOP && other.half == HalfInning.BOTTOM;
    }

    private static int zero(Integer v) {
        return v == null ? 0 : v;
    }
}
