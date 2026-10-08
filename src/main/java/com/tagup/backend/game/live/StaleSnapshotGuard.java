package com.tagup.backend.game.live;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * KBO가 섞어 주는 <b>옛 스냅샷</b>을 걸러낸다.
 *
 * <p>2026-09-16 실경기에서 관측: 5회말 진행 중 5회초 2아웃 스냅샷이 한 번 끼어들었다가 돌아왔고,
 * 9회말에는 9회초 스냅샷이 약 1분 반 동안 번갈아 섞였다. 그대로 감지기에 넣으면 이미 끝난
 * 타석이 다시 "종료"되고, 진행 중인 타석이 가짜 결과로 정산된다.
 *
 * <p>과거 스냅샷은 버리되, 같은 상태가 {@code healAfter}번 연속되면 받아들인다 —
 * KBO가 기록을 실제로 정정한 경우 폴러가 영영 멈추면 안 되기 때문이다.
 */
public class StaleSnapshotGuard {

    private final int healAfter;
    private final int outHealAfter;
    private final Map<String, Integer> consecutive = new ConcurrentHashMap<>();

    public StaleSnapshotGuard(int healAfter, int outHealAfter) {
        this.healAfter = healAfter;
        this.outHealAfter = outHealAfter;
    }

    public enum Verdict { ACCEPT, REJECT, HEAL }

    /** {@code cur}를 직전 스냅샷 {@code prev} 다음으로 받아들일지 */
    public Verdict check(String kboGameId, LiveGameSnapshot prev, LiveGameSnapshot cur) {
        int threshold;
        if (prev == null) {
            threshold = 0;
        } else if (cur.isBehind(prev)) {
            // 회·초말 역행이나 점수 감소는 정상일 수 없다 — 오래 버틴다
            threshold = healAfter;
        } else if (cur.hasOutRegression(prev)) {
            // 아웃만 줄어든 건 공수 교대 시차일 수도 있다 — 한 번 더 보고 판단한다
            threshold = outHealAfter;
        } else {
            threshold = 0;
        }

        if (threshold == 0) {
            consecutive.remove(kboGameId);
            return Verdict.ACCEPT;
        }
        int n = consecutive.merge(kboGameId, 1, Integer::sum);
        if (n >= threshold) {
            consecutive.remove(kboGameId);
            return Verdict.HEAL;
        }
        return Verdict.REJECT;
    }

    public void clear() {
        consecutive.clear();
    }
}
