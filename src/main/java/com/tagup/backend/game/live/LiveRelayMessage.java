package com.tagup.backend.game.live;

/**
 * 중계 문구 생성. <b>순수 함수라 문구를 테스트로 고정할 수 있다.</b>
 *
 * <p>KBO가 주는 정보의 한계를 그대로 반영한다 — 뜬공·땅볼·안타·볼넷은 구분되지 않으므로
 * <b>아웃 / 세이프</b>로만 말한다. 없는 정보를 있는 것처럼 쓰면 사용자가 오해한다.
 */
public final class LiveRelayMessage {

    private LiveRelayMessage() {}

    /** 타석 시작 — 배팅 창이 열렸다는 신호이기도 하다 */
    public static String atBatStarted(AtBatStartedEvent e) {
        StringBuilder sb = new StringBuilder();
        if (e.pitcherChanged() && notBlank(e.pitcher())) {
            sb.append("🔄 투수 교체 — ").append(e.pitcher()).append('\n');
        }
        sb.append("⚾ ").append(inningText(e.inning(), e.half()))
          .append(' ').append(e.batter()).append(" 타석");
        if (notBlank(e.pitcher())) {
            sb.append(" (투수 ").append(e.pitcher()).append(')');
        }
        return sb.toString();
    }

    /** 타석 결과 */
    public static String atBatFinished(AtBatEvent e) {
        String head = switch (e.result()) {
            case OUT -> "🔴 " + e.batter() + " 아웃";
            case SAFE -> "🟢 " + e.batter() + " 세이프";
            case UNKNOWN -> "❔ " + e.batter() + " 결과 확인 불가";
        };

        StringBuilder sb = new StringBuilder(head);
        if (e.runsScored() > 0) sb.append(" · ").append(e.runsScored()).append("점!");
        if (e.endedInning()) sb.append(" · 이닝 종료");
        return sb.toString();
    }

    private static String inningText(Integer inning, HalfInning half) {
        if (inning == null) return "";
        return inning + "회" + (half == null ? "" : half.korean());
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
