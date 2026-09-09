package com.tagup.backend.bet.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateBetRequest(
        /**
         * 배팅할 경기. <b>생략 가능</b> — 비우면 더그아웃이 오늘 보고 있는 경기를 쓴다.
         * 방의 관전 경기도 없으면 거절된다.
         */
        Long gameId,

        @NotBlank(message = "내기 내용을 입력해주세요.")
        @Size(max = 100, message = "내기 내용은 100자 이하여야 합니다.")
        String content,

        @NotNull(message = "응원할 팀 ID를 입력해주세요.")
        Long betOnTeamId
) {}
