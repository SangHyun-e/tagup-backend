package com.tagup.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.time.Clock;

@Configuration
public class AppConfig {

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }

    /** 시간에 의존하는 로직을 테스트에서 조작할 수 있게 주입한다 (예: 폴링 공백 감지) */
    @Bean
    public Clock clock() {
        return Clock.systemDefaultZone();
    }
}
