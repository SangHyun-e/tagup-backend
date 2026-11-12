package com.tagup.backend.bet.dto;

import com.tagup.backend.game.live.AtBatResult;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 타석 배팅 생성.
 *
 * <p><b>어느 타석인지는 클라이언트가 정하지 않는다.</b> 서버가 라이브 상태에서 읽은
 * '지금 타석'에 건다. 클라이언트가 이닝·타자를 보내면 화면이 낡았을 때 엉뚱한 타석에
 * 걸리고, 결과를 본 뒤 과거 타석을 지정하는 것도 가능해진다.
 */
public record CreateAtBatBetRequest(
        /** 아웃/세이프 중 하나. 콜한 사람은 자동으로 반대편에 선다 */
        @NotNull(message = "아웃/세이프 중 하나를 선택해주세요.")
        AtBatResult betOnResult,

        @NotBlank(message = "내기 내용을 입력해주세요.")
        @Size(max = 100, message = "내기 내용은 100자 이하여야 합니다.")
        String content
) {}
