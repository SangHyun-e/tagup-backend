package com.tagup.backend.game.live;

/** 이닝의 초/말. KBO 응답의 GAME_TB_SC_NM("초"/"말")에 대응한다. */
public enum HalfInning {
    TOP("초"),
    BOTTOM("말");

    private final String korean;

    HalfInning(String korean) {
        this.korean = korean;
    }

    public String korean() {
        return korean;
    }

    public static HalfInning from(String text) {
        if (text == null) return null;
        return switch (text.trim()) {
            case "초" -> TOP;
            case "말" -> BOTTOM;
            default -> null;
        };
    }
}
