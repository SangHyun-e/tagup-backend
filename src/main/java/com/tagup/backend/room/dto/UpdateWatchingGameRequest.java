package com.tagup.backend.room.dto;

/**
 * 더그아웃이 오늘 같이 볼 경기 지정.
 *
 * @param gameId null이면 관전 경기를 해제한다
 */
public record UpdateWatchingGameRequest(Long gameId) {}
