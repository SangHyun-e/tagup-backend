package com.tagup.backend.notification.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterDeviceRequest(
        @NotBlank(message = "푸시 토큰을 입력해주세요.")
        @Size(max = 255)
        String pushToken,

        @Size(max = 20)
        String platform
) {}
