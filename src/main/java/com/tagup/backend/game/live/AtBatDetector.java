package com.tagup.backend.game.live;

import java.util.Optional;

/**
 * 연속한 두 스냅샷을 비교해 <b>타석이 끝났는지와 그 결과</b>를 판정한다.
 *
 * <p>KBO는 타석 단위 이벤트를 주지 않는다. 대신 타자 이름이 바뀌는 순간을 타석 경계로 보고,
 * 그 사이의 아웃카운트·스코어·주자 변화로 결과를 역산한다.
 * (2026-09-01 실경기 234샘플 관측에 근거 — PM_plan §4-4)
 *
 * <p><b>순수 함수다.</b> 네트워크·DB에 의존하지 않아 실제 경기 데이터를 리플레이해 검증할 수 있다.
 */
public class AtBatDetector {

    /**
     * @return 타석이 끝났으면 그 결과, 아직 진행 중이면 {@link Optional#empty()}
     */
    public Optional<AtBatEvent> detect(LiveGameSnapshot prev, LiveGameSnapshot cur) {
        if (prev == null || cur == null) return Optional.empty();
        if (!prev.isLive() || !cur.isLive()) return Optional.empty();

        String prevBatter = prev.batter();
        if (prevBatter == null || prevBatter.isBlank()) return Optional.empty();

        // 타자가 그대로면 아직 같은 타석 (투구만 진행 중)
        if (prevBatter.equals(cur.batter())) return Optional.empty();

        boolean halfChanged = !prev.sameHalfInning(cur);
        int runs = cur.totalScore() - prev.totalScore();
        int outDelta = zero(cur.out()) - zero(prev.out());
        boolean runnersChanged = !prev.sameRunners(cur);

        AtBatResult result;
        if (halfChanged) {
            // 이닝이 넘어갔다 = 직전 타석이 3번째 아웃을 만들었다.
            // (아웃카운트는 0으로 리셋되므로 outDelta로는 판정할 수 없다)
            result = AtBatResult.OUT;
        } else if (outDelta > 0) {
            // 같은 이닝에서 아웃이 늘었다 → 타자가 아웃
            // 주의: 야수선택처럼 '타자는 살고 주자가 죽는' 경우를 아웃으로 오판할 수 있다 (드묾)
            result = AtBatResult.OUT;
        } else if (runs > 0 || runnersChanged) {
            // 아웃은 안 늘었는데 점수가 나거나 주자가 바뀌었다 → 타자가 살아나감
            result = AtBatResult.SAFE;
        } else {
            // 근거 부족 — 폴링 간격이 길어 여러 타석을 건너뛴 경우 등
            result = AtBatResult.UNKNOWN;
        }

        return Optional.of(new AtBatEvent(
                prevBatter, prev.pitcher(), prev.inning(), prev.half(),
                result, Math.max(runs, 0), halfChanged));
    }

    private static int zero(Integer v) {
        return v == null ? 0 : v;
    }
}
