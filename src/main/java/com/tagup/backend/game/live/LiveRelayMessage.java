package com.tagup.backend.game.live;

import java.util.HashMap;
import java.util.Map;

/**
 * 중계 메시지. <b>순수 함수라 내용을 테스트로 고정할 수 있다.</b>
 *
 * <p>KBO가 주는 정보의 한계를 그대로 반영한다 — 뜬공·땅볼·안타·볼넷은 구분되지 않으므로
 * <b>아웃 / 세이프</b>로만 말한다. 없는 정보를 있는 것처럼 쓰면 사용자가 오해한다.
 *
 * <p><b>2026-10-02: 문장 대신 데이터를 보낸다.</b> 지금까지는 "🔴 박민우 아웃"처럼 완성된 문자열을
 * 저장하고 앱이 그대로 출력했다. 그래서 중계 줄의 모양을 앱에서 바꿀 수 없었고, 기기마다 다르게
 * 그려지는 이모지가 디자인을 좌우했다. 이제 타자·결과·득점을 {@link #startedData}/{@link #finishedData}
 * 로 함께 보내고 앱이 아이콘과 색으로 그린다.
 *
 * <p>{@code text()} 는 남겨둔다 — 푸시 알림과 구버전 앱이 쓴다. 이모지는 뺐다.
 */
public final class LiveRelayMessage {

    private LiveRelayMessage() {}

    /** 타석 시작 — 배팅 창이 열렸다는 신호이기도 하다 */
    public static String atBatStarted(AtBatStartedEvent e) {
        StringBuilder sb = new StringBuilder();
        if (e.pitcherChanged() && notBlank(e.pitcher())) {
            sb.append("투수 교체 — ").append(e.pitcher()).append('\n');
        }
        sb.append(inningText(e.inning(), e.half()))
          .append(' ').append(e.batter()).append(" 타석");
        if (notBlank(e.pitcher())) {
            sb.append(" (투수 ").append(e.pitcher()).append(')');
        }
        return sb.toString().strip();
    }

    /** 타석 결과 */
    public static String atBatFinished(AtBatEvent e) {
        String head = switch (e.result()) {
            case OUT -> e.batter() + " 아웃";
            case SAFE -> e.batter() + " 세이프";
            case UNKNOWN -> e.batter() + " 결과 확인 불가";
        };

        StringBuilder sb = new StringBuilder(head);
        if (e.runsScored() > 0) sb.append(" · ").append(e.runsScored()).append("점");
        if (e.endedInning()) sb.append(" · 이닝 종료");
        return sb.toString();
    }

    /** 앱이 직접 그릴 수 있도록 타석 시작을 데이터로 */
    public static Map<String, Object> startedData(AtBatStartedEvent e) {
        Map<String, Object> data = new HashMap<>();
        put(data, "inning", e.inning());
        put(data, "half", e.half() == null ? null : e.half().name());
        put(data, "batter", e.batter());
        put(data, "pitcher", e.pitcher());
        data.put("pitcherChanged", e.pitcherChanged());
        return data;
    }

    /** 앱이 직접 그릴 수 있도록 타석 결과를 데이터로 */
    public static Map<String, Object> finishedData(AtBatEvent e) {
        Map<String, Object> data = new HashMap<>();
        put(data, "inning", e.inning());
        put(data, "half", e.half() == null ? null : e.half().name());
        put(data, "batter", e.batter());
        put(data, "pitcher", e.pitcher());
        data.put("result", e.result().name());
        data.put("runsScored", e.runsScored());
        data.put("endedInning", e.endedInning());
        return data;
    }

    private static void put(Map<String, Object> data, String key, Object value) {
        if (value instanceof String s && s.isBlank()) return;
        if (value != null) data.put(key, value);
    }

    private static String inningText(Integer inning, HalfInning half) {
        if (inning == null) return "";
        return inning + "회" + (half == null ? "" : half.korean());
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
