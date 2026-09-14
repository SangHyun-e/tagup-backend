package com.tagup.backend.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ErrorCode {

    // Auth
    EMAIL_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 사용 중인 이메일입니다."),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 올바르지 않습니다."),
    INVALID_FIREBASE_TOKEN(HttpStatus.UNAUTHORIZED, "유효하지 않은 Firebase 토큰입니다."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "인증이 필요합니다."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "접근 권한이 없습니다."),

    // Room
    ROOM_NOT_FOUND(HttpStatus.NOT_FOUND, "더그아웃을 찾을 수 없습니다."),
    INVALID_TAG_CODE(HttpStatus.NOT_FOUND, "유효하지 않은 태그코드입니다."),
    ALREADY_ROOM_MEMBER(HttpStatus.CONFLICT, "이미 참여 중인 더그아웃입니다."),

    // User
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."),

    // Team
    TEAM_NOT_FOUND(HttpStatus.NOT_FOUND, "팀을 찾을 수 없습니다."),

    // Game
    GAME_NOT_FOUND(HttpStatus.NOT_FOUND, "경기를 찾을 수 없습니다."),

    // Bet
    BET_NOT_FOUND(HttpStatus.NOT_FOUND, "내기를 찾을 수 없습니다."),
    NOT_ROOM_MEMBER(HttpStatus.FORBIDDEN, "더그아웃 멤버만 접근할 수 있습니다."),
    NOT_BET_PARTICIPANT(HttpStatus.FORBIDDEN, "내기 참여자만 접근할 수 있습니다."),
    BET_NOT_PENDING(HttpStatus.BAD_REQUEST, "수락 대기 중인 내기만 처리할 수 있습니다."),
    GAME_ALREADY_STARTED(HttpStatus.BAD_REQUEST, "이미 시작된 경기에는 내기를 제안할 수 없습니다."),
    INVALID_BET_TEAM(HttpStatus.BAD_REQUEST, "해당 경기에 참여하지 않는 팀입니다."),
    CANNOT_BET_SELF(HttpStatus.BAD_REQUEST, "자신이 건 배팅에는 콜할 수 없습니다."),
    NO_WATCHING_GAME(HttpStatus.BAD_REQUEST, "경기를 지정하거나 더그아웃의 관전 경기를 먼저 설정해주세요."),
    GAME_NOT_LIVE(HttpStatus.BAD_REQUEST, "진행 중인 경기에서만 타석 배팅을 걸 수 있습니다."),
    NO_LIVE_AT_BAT(HttpStatus.BAD_REQUEST, "지금 진행 중인 타석이 없습니다."),
    AT_BAT_BETTING_CLOSED(HttpStatus.BAD_REQUEST, "이 타석은 배팅이 마감됐습니다. 다음 타석을 기다려주세요."),
    INVALID_AT_BAT_RESULT(HttpStatus.BAD_REQUEST, "아웃 또는 세이프만 선택할 수 있습니다."),

    // Common
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 오류가 발생했습니다.");

    private final HttpStatus status;
    private final String message;

    ErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }
}
