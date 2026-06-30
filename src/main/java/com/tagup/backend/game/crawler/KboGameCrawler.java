package com.tagup.backend.game.crawler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tagup.backend.game.entity.GameStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
@RequiredArgsConstructor
public class KboGameCrawler {

    // KBO 공식 사이트 schedule JSON API (form-urlencoded POST)
    private static final String SCHEDULE_URL =
            "https://www.koreabaseball.com/ws/Schedule.asmx/GetScheduleList";

    // "한화3vs5두산" → away=한화, awayScore=3, homeScore=5, home=두산
    private static final Pattern SCORE_PATTERN = Pattern.compile("(.+?)(\\d+)vs(\\d+)(.+)");
    // "키움vs롯데" → away=키움, home=롯데 (미경기)
    private static final Pattern VS_PATTERN = Pattern.compile("(.+?)vs(.+)");

    private static final DateTimeFormatter DATE_PARSER = DateTimeFormatter.ofPattern("MM.dd");
    private static final DateTimeFormatter TIME_PARSER = DateTimeFormatter.ofPattern("HH:mm");

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public List<CrawledGame> crawlByDate(LocalDate date) {
        try {
            String json = fetchScheduleJson(date.getYear(), date.getMonthValue());
            return parseGames(json, date);
        } catch (Exception e) {
            log.warn("KBO 크롤링 실패 date={}: {}", date, e.getMessage());
            return List.of();
        }
    }

    private String fetchScheduleJson(int year, int month) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
        headers.set("Referer", "https://www.koreabaseball.com/Schedule/Schedule.aspx");
        headers.set("X-Requested-With", "XMLHttpRequest");
        headers.set("Accept", "application/json, text/javascript, */*; q=0.01");

        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("leId", "1");
        params.add("srIdList", "0,9,6");   // 정규시즌
        params.add("seasonId", String.valueOf(year));
        params.add("gameMonth", String.valueOf(month));
        params.add("teamId", "");

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(params, headers);
        ResponseEntity<String> response = restTemplate.exchange(
                SCHEDULE_URL, HttpMethod.POST, request, String.class);

        return response.getBody();
    }

    private List<CrawledGame> parseGames(String json, LocalDate targetDate) throws Exception {
        JsonNode root = objectMapper.readTree(json);
        JsonNode rows = root.get("rows");

        List<CrawledGame> result = new ArrayList<>();
        String currentDateStr = null;

        for (JsonNode row : rows) {
            JsonNode cells = row.get("row");
            if (cells == null || cells.isEmpty()) continue;

            // 첫 셀이 "day"면 날짜 업데이트
            String firstClass = cells.get(0).path("Class").asText("");
            int offset = 0;
            if ("day".equals(firstClass)) {
                currentDateStr = stripTags(cells.get(0).path("Text").asText(""));
                offset = 1;
            }

            if (currentDateStr == null) continue;

            // 날짜 필터링: "06.30(월)" 형태에서 월.일 추출
            String datePart = currentDateStr.length() >= 5 ? currentDateStr.substring(0, 5) : currentDateStr;
            String targetMonthDay = targetDate.format(DateTimeFormatter.ofPattern("MM.dd"));
            if (!datePart.equals(targetMonthDay)) continue;

            // 셀 파싱
            String timeText = stripTags(cells.path(offset).path("Text").asText(""));
            String playText = stripTags(cells.path(offset + 1).path("Text").asText(""));
            String relayText = stripTags(cells.path(offset + 2).path("Text").asText(""));
            String stadiumText = stripTags(cells.path(offset + 6).path("Text").asText(""));

            CrawledGame game = buildGame(targetDate, timeText, playText, relayText, stadiumText);
            if (game != null) {
                result.add(game);
            }
        }

        log.info("KBO 크롤링 완료 date={}, 경기 수={}", targetDate, result.size());
        return result;
    }

    private CrawledGame buildGame(LocalDate date, String timeText, String playText,
                                  String relayText, String stadium) {
        if (playText.isBlank()) return null;

        String awayShort, homeShort;
        Integer awayScore = null, homeScore = null;

        Matcher scoreMatcher = SCORE_PATTERN.matcher(playText);
        Matcher vsMatcher = VS_PATTERN.matcher(playText);

        if (scoreMatcher.matches()) {
            awayShort = scoreMatcher.group(1).trim();
            awayScore = Integer.parseInt(scoreMatcher.group(2));
            homeScore = Integer.parseInt(scoreMatcher.group(3));
            homeShort = scoreMatcher.group(4).trim();
        } else if (vsMatcher.matches()) {
            awayShort = vsMatcher.group(1).trim();
            homeShort = vsMatcher.group(2).trim();
        } else {
            log.debug("경기 텍스트 파싱 실패: {}", playText);
            return null;
        }

        LocalTime gameTime = parseTime(timeText);
        GameStatus status = resolveStatus(relayText, awayScore);

        String kboGameId = date.format(DateTimeFormatter.ofPattern("yyyyMMdd"))
                + "_" + awayShort + "_" + homeShort;

        return CrawledGame.builder()
                .kboGameId(kboGameId)
                .gameDate(date)
                .gameTime(gameTime)
                .awayTeamShortName(awayShort)
                .homeTeamShortName(homeShort)
                .status(status)
                .awayScore(awayScore)
                .homeScore(homeScore)
                .stadium(stadium.isBlank() ? null : stadium)
                .build();
    }

    private GameStatus resolveStatus(String relayText, Integer score) {
        if ("취소".equals(relayText) || "우천".equals(relayText)) return GameStatus.CANCELLED;
        if ("리뷰".equals(relayText) || score != null) return GameStatus.FINISHED;
        if (relayText.contains("회")) return GameStatus.IN_PROGRESS;
        return GameStatus.SCHEDULED;
    }

    private LocalTime parseTime(String text) {
        try {
            return LocalTime.parse(text.trim(), TIME_PARSER);
        } catch (Exception e) {
            return null;
        }
    }

    private String stripTags(String html) {
        return html.replaceAll("<[^>]+>", "").trim();
    }
}
