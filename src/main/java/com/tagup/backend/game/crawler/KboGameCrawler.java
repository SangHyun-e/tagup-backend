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
import java.time.MonthDay;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
@RequiredArgsConstructor
public class KboGameCrawler {

    private static final String SCHEDULE_URL =
            "https://www.koreabaseball.com/ws/Schedule.asmx/GetScheduleList";

    private static final Pattern SCORE_PATTERN = Pattern.compile("(.+?)(\\d+)vs(\\d+)(.+)");
    private static final Pattern VS_PATTERN = Pattern.compile("(.+?)vs(.+)");
    private static final DateTimeFormatter TIME_PARSER = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DATE_PARSER = DateTimeFormatter.ofPattern("MM.dd");

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    /** 특정 날짜 경기만 반환 (실시간 결과 업데이트용) */
    public List<CrawledGame> crawlByDate(LocalDate date) {
        try {
            String json = fetchScheduleJson(date.getYear(), date.getMonthValue());
            return parseGames(json, date.getYear()).stream()
                    .filter(g -> g.gameDate().equals(date))
                    .toList();
        } catch (Exception e) {
            log.warn("KBO 크롤링 실패 date={}: {}", date, e.getMessage());
            return List.of();
        }
    }

    /** 해당 월 전체 경기 반환 (월간 일정 수집용) */
    public List<CrawledGame> crawlByMonth(int year, int month) {
        try {
            String json = fetchScheduleJson(year, month);
            List<CrawledGame> games = parseGames(json, year);
            log.info("KBO 월간 크롤링 완료 {}-{:02d}, 경기 수={}", year, month, games.size());
            return games;
        } catch (Exception e) {
            log.warn("KBO 월간 크롤링 실패 {}-{}: {}", year, month, e.getMessage());
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
        params.add("srIdList", "0,9,6");
        params.add("seasonId", String.valueOf(year));
        params.add("gameMonth", String.valueOf(month));
        params.add("teamId", "");

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(params, headers);
        ResponseEntity<String> response = restTemplate.exchange(
                SCHEDULE_URL, HttpMethod.POST, request, String.class);
        return response.getBody();
    }

    private List<CrawledGame> parseGames(String json, int year) throws Exception {
        JsonNode rows = objectMapper.readTree(json).get("rows");

        List<CrawledGame> result = new ArrayList<>();
        LocalDate currentDate = null;

        for (JsonNode row : rows) {
            JsonNode cells = row.get("row");
            if (cells == null || cells.isEmpty()) continue;

            int offset = 0;
            if ("day".equals(cells.get(0).path("Class").asText(""))) {
                String dateText = stripTags(cells.get(0).path("Text").asText(""));
                currentDate = parseDate(dateText, year);
                offset = 1;
            }

            if (currentDate == null) continue;

            String timeText    = stripTags(cells.path(offset).path("Text").asText(""));
            String playText    = stripTags(cells.path(offset + 1).path("Text").asText(""));
            String relayText   = stripTags(cells.path(offset + 2).path("Text").asText(""));
            String stadiumText = stripTags(cells.path(offset + 6).path("Text").asText(""));

            CrawledGame game = buildGame(currentDate, timeText, playText, relayText, stadiumText);
            if (game != null) result.add(game);
        }

        return result;
    }

    private LocalDate parseDate(String dateText, int year) {
        try {
            // "06.30(월)" → "06.30" → MonthDay
            String datePart = dateText.length() >= 5 ? dateText.substring(0, 5) : dateText;
            MonthDay md = MonthDay.parse(datePart, DATE_PARSER);
            return md.atYear(year);
        } catch (Exception e) {
            return null;
        }
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

        String kboGameId = date.format(DateTimeFormatter.ofPattern("yyyyMMdd"))
                + "_" + awayShort + "_" + homeShort;

        return CrawledGame.builder()
                .kboGameId(kboGameId)
                .gameDate(date)
                .gameTime(parseTime(timeText))
                .awayTeamShortName(awayShort)
                .homeTeamShortName(homeShort)
                .status(resolveStatus(relayText, awayScore))
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
