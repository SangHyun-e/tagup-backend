package com.tagup.backend.game.live;

/**
 * KBO {@code GAME_STATE_SC} 코드.
 *
 * <p>2026-09-01 진행 중 경기 5개를 60초 간격 234샘플로 관측해 확정:
 * {@code 1 → 2 → 3} 전이(예정→진행→종료), {@code 4}는 취소.
 */
public enum LiveGameState {
    SCHEDULED,
    IN_PROGRESS,
    FINISHED,
    CANCELLED,
    UNKNOWN;

    public static LiveGameState fromCode(String code) {
        if (code == null) return UNKNOWN;
        return switch (code.trim()) {
            case "1" -> SCHEDULED;
            case "2" -> IN_PROGRESS;
            case "3" -> FINISHED;
            case "4" -> CANCELLED;
            default -> UNKNOWN;
        };
    }
}
