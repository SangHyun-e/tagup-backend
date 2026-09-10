package com.tagup.backend.game.live;

/** 종료된 타석 하나. {@link AtBatDetector}가 스냅샷 전이에서 만들어낸다. */
public record AtBatEvent(
        String batter,
        String pitcher,
        Integer inning,
        HalfInning half,
        AtBatResult result,
        /** 이 타석에서 난 득점 */
        int runsScored,
        /** 이 타석으로 이닝이 끝났는지 (3아웃) */
        boolean endedInning
) {
    public boolean settleable() {
        return result != AtBatResult.UNKNOWN;
    }
}
