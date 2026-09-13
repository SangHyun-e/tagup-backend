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
 *
 * <p><b>기준 스냅샷이 중요하다.</b> 2026-09-01 실경기 349타석을 두 방식으로 리플레이한 결과:
 * <pre>
 *   직전 스냅샷 기준   OUT 227 / SAFE 108 / UNKNOWN 14   정산가능 96.0%
 *   타석 시작 기준     OUT 232 / SAFE 111 / UNKNOWN  6   정산가능 98.3%
 * </pre>
 * 폴링이 촘촘할수록 직전 스냅샷이 이미 갱신돼 있을 확률이 높아 이 차이는 더 벌어진다.
 * 실서비스 15초 폴링에서는 정산가능률이 88%대까지 떨어졌다.
 */
public class AtBatDetector {

    /**
     * 기준 스냅샷 없이 판정한다. {@code prev}를 기준으로 삼으므로 정확도가 낮다 —
     * 가능하면 {@link #detect(LiveGameSnapshot, LiveGameSnapshot, LiveGameSnapshot)}를 쓸 것.
     */
    public Optional<AtBatEvent> detect(LiveGameSnapshot prev, LiveGameSnapshot cur) {
        return detect(prev, prev, cur);
    }

    /**
     * @param atBatStart 이 타석이 <b>시작된</b> 시점의 스냅샷 (결과 역산의 기준)
     * @param prev       직전 스냅샷 (타석 경계·이닝 전환 판정용)
     * @param cur        현재 스냅샷
     * @return 타석이 끝났으면 그 결과, 아직 진행 중이면 {@link Optional#empty()}
     */
    public Optional<AtBatEvent> detect(LiveGameSnapshot atBatStart,
                                       LiveGameSnapshot prev,
                                       LiveGameSnapshot cur) {
        if (prev == null || cur == null) return Optional.empty();
        if (!prev.isLive() || !cur.isLive()) return Optional.empty();
        if (atBatStart == null || !atBatStart.isLive()) atBatStart = prev;

        String prevBatter = prev.batter();
        if (prevBatter == null || prevBatter.isBlank()) return Optional.empty();

        // 타자가 그대로면 아직 같은 타석 (투구만 진행 중)
        if (prevBatter.equals(cur.batter())) return Optional.empty();

        boolean halfChanged = !prev.sameHalfInning(cur);
        // 변화량은 '타석이 시작된 시점'과 비교한다. 직전 스냅샷과 비교하면 안 된다 —
        // KBO는 타격 결과(점수·아웃·주자)를 먼저 갱신하고 타자 이름은 다음 타자가 들어설 때
        // 바뀐다. 그래서 타자가 바뀌는 순간에는 변화가 이미 몇 폴링 전에 끝나 있어 0으로 보인다.
        int runs = cur.totalScore() - atBatStart.totalScore();
        int outDelta = zero(cur.out()) - zero(atBatStart.out());
        boolean runnersChanged = !atBatStart.sameRunners(cur);

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
