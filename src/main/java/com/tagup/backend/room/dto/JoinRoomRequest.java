package com.tagup.backend.room.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record JoinRoomRequest(
        @NotBlank(message = "태그코드를 입력해주세요.")
        @Size(min = 6, max = 6, message = "태그코드는 6자리입니다.")
        String tagCode
) {
}
