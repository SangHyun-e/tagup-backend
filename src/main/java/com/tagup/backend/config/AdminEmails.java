package com.tagup.backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

/** {@code tagup.security.admin-emails} 설정값. 공백·대소문자를 정리해 돌려준다 */
@Component
public class AdminEmails {

    private final String raw;

    public AdminEmails(@Value("${tagup.security.admin-emails:}") String raw) {
        this.raw = raw;
    }

    public List<String> normalized() {
        if (raw == null || raw.isBlank()) return List.of();
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .map(s -> s.toLowerCase())
                .distinct()
                .toList();
    }
}
