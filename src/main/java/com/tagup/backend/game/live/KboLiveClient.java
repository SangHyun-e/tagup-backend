package com.tagup.backend.game.live;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * KBO 메인 페이지 경기 위젯 API({@code /ws/Main.asmx/GetKboGameList})에서
 * 라이브 스냅샷을 가져온다.
 *
 * <p>일정 API({@code GetScheduleList})와 달리 이닝·초말·S/B/O·주자·현재 선수를 담고 있어
 * 타석 경계를 감지할 수 있다. 반대로 일정 API에는 이것들이 없다. (I-010, 2026-09-01 확정)
 *
 * <p><b>응답 인코딩 주의</b> — 본문이 UTF-8 BOM으로 시작한다. 그대로 파싱하면 실패하므로 제거한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KboLiveClient {

    private static final String URL =
            "https://www.koreabaseball.com/ws/Main.asmx/GetKboGameList";
    private static final DateTimeFormatter DATE_PARAM = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final char BOM = '﻿';

    private final RestTemplate restTemplate;
    private final JsonMapper jsonMapper;

    /**
     * @return {@code kboGameId → 스냅샷}. {@code kboGameId}는 {@code Game} 엔티티와 같은
     *         {@code yyyyMMdd_원정_홈} 형식이라 그대로 조인할 수 있다.
     *         실패 시 빈 맵 (폴링은 다음 주기에 다시 시도하면 되므로 예외를 밖으로 던지지 않는다)
     */
    public Map<String, LiveGameSnapshot> fetchSnapshots(LocalDate date) {
        try {
            JsonNode games = jsonMapper.readTree(stripBom(fetchRaw(date))).path("game");
            Map<String, LiveGameSnapshot> result = new LinkedHashMap<>();

            for (JsonNode g : games) {
                String away = g.path("AWAY_NM").asString("").trim();
                String home = g.path("HOME_NM").asString("").trim();
                if (away.isEmpty() || home.isEmpty()) continue;

                String kboGameId = date.format(DATE_PARAM) + "_" + away + "_" + home;
                result.put(kboGameId, toSnapshot(g));
            }
            return result;

        } catch (Exception e) {
            log.warn("[라이브] KBO 스냅샷 수집 실패 date={}: {}", date, e.getMessage());
            return Map.of();
        }
    }

    private String fetchRaw(LocalDate date) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
        headers.set("Referer", "https://www.koreabaseball.com/");
        headers.set("X-Requested-With", "XMLHttpRequest");
        headers.set("Accept", "application/json, text/javascript, */*; q=0.01");

        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("leId", "1");
        params.add("srId", "0,9,6");
        params.add("date", date.format(DATE_PARAM));

        return restTemplate.exchange(URL, HttpMethod.POST,
                new HttpEntity<>(params, headers), String.class).getBody();
    }

    private LiveGameSnapshot toSnapshot(JsonNode g) {
        return new LiveGameSnapshot(
                LiveGameState.fromCode(text(g, "GAME_STATE_SC")),
                number(g, "GAME_INN_NO"),
                HalfInning.from(text(g, "GAME_TB_SC_NM")),
                number(g, "T_SCORE_CN"),
                number(g, "B_SCORE_CN"),
                number(g, "STRIKE_CN"),
                number(g, "BALL_CN"),
                number(g, "OUT_CN"),
                text(g, "T_P_NM"),
                text(g, "B_P_NM"),
                number(g, "B1_BAT_ORDER_NO"),
                number(g, "B2_BAT_ORDER_NO"),
                number(g, "B3_BAT_ORDER_NO"));
    }

    /** 같은 필드가 문자열("0")로도 숫자(0)로도 오므로 양쪽을 받는다. 빈 값은 null */
    private Integer number(JsonNode node, String field) {
        JsonNode v = node.path(field);
        if (v.isMissingNode() || v.isNull()) return null;
        if (v.isNumber()) return v.asInt();
        String s = v.asString("").trim();
        if (s.isEmpty()) return null;
        try {
            return Integer.valueOf(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String text(JsonNode node, String field) {
        String s = node.path(field).asString("");
        return s == null ? "" : s.trim();
    }

    private String stripBom(String body) {
        if (body == null) return "";
        return (!body.isEmpty() && body.charAt(0) == BOM) ? body.substring(1) : body;
    }
}
