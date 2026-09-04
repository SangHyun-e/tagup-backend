package com.tagup.backend.game.crawler;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
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
    private static final Pattern INNING_PATTERN = Pattern.compile("(\\d+)\\s*회");
    private static final DateTimeFormatter TIME_PARSER = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DATE_PARSER = DateTimeFormatter.ofPattern("MM.dd");

    private final RestTemplate restTemplate;
    private final JsonMapper jsonMapper;

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
        JsonNode rows = jsonMapper.readTree(json).get("rows");

        List<CrawledGame> result = new ArrayList<>();
        LocalDate currentDate = null;

        for (JsonNode row : rows) {
            JsonNode cells = row.get("row");
            if (cells == null || cells.isEmpty()) continue;

            int offset = 0;
            if ("day".equals(cells.get(0).path("Class").asString(""))) {
                String dateText = stripTags(cells.get(0).path("Text").asString(""));
                currentDate = parseDate(dateText, year);
                offset = 1;
            }

            if (currentDate == null) continue;

            String timeText    = stripTags(cells.path(offset).path("Text").asString(""));
            String playText    = stripTags(cells.path(offset + 1).path("Text").asString(""));
            String relayText   = stripTags(cells.path(offset + 2).path("Text").asString(""));
            String stadiumText = stripTags(cells.path(offset + 6).path("Text").asString(""));
            // 비고 칸(마지막 셀): 정상 '-', 취소 시 '우천취소'/'그라운드사정' 등 사유 표시
            String noteText    = stripTags(cells.path(cells.size() - 1).path("Text").asString(""));

            CrawledGame game = buildGame(currentDate, timeText, playText, relayText, noteText, stadiumText);
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
                                  String relayText, String noteText, String stadium) {
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

        GameStatus status = resolveStatus(relayText, noteText, awayScore);

        // 진행 중 경기의 점수 칸은 0vs0으로 고정돼 있어 실제 스코어가 아니다.
        // 잘못된 0:0을 노출/정산에 쓰지 않도록 버린다. (실시간 스코어는 스코어보드 API 필요 — Sprint 4)
        if (status == GameStatus.IN_PROGRESS) {
            awayScore = null;
            homeScore = null;
        }

        String kboGameId = date.format(DateTimeFormatter.ofPattern("yyyyMMdd"))
                + "_" + awayShort + "_" + homeShort;

        return CrawledGame.builder()
                .kboGameId(kboGameId)
                .gameDate(date)
                .gameTime(parseTime(timeText))
                .awayTeamShortName(awayShort)
                .homeTeamShortName(homeShort)
                .status(status)
                .inning(parseInning(relayText, noteText, awayScore))
                .awayScore(awayScore)
                .homeScore(homeScore)
                .stadium(stadium.isBlank() ? null : stadium)
                .build();
    }

    /**
     * 경기 상태 판정.
     *
     * <p>2026-08-26 야간 경기를 5회 스냅샷해 확인한 실제 응답:
     * <pre>
     *   예정   : play="NCvsLG"    relay="프리뷰"  note="-"
     *   진행 중 : play="NC0vs0LG"  relay=""        note="-"   ← 점수는 0vs0로 고정(실시간 아님)
     *   종료   : play="NC0vs8LG"  relay="리뷰"    note="-"
     *   취소   : play="NCvsLG"    relay=""        note="우천취소"
     * </pre>
     *
     * <p><b>진행 중에도 점수 칸이 0vs0으로 채워진다.</b> 과거 로직은 "점수가 있으면 종료"로
     * 판정해 경기 시작 직후 FINISHED가 되었고, 정산 스케줄러가 이를 0:0 무승부로 정산해버렸다.
     * 따라서 relay 칸을 먼저 보고 종료 여부를 확정한다.
     */
    private GameStatus resolveStatus(String relayText, String noteText, Integer score) {
        // 1) 취소/순연 (우천취소, 그라운드사정 등) — 비고 칸이 정상값이 아님
        if (isCancelledNote(noteText)) return GameStatus.CANCELLED;
        // 2) 종료 확정 — 리뷰 링크가 붙는다
        if ("리뷰".equals(relayText)) return GameStatus.FINISHED;
        // 3) 이닝 표기가 있는 경우 (일부 응답에서 관측될 수 있음)
        if (relayText.contains("회")) return GameStatus.IN_PROGRESS;
        // 4) 점수 칸이 채워졌는데 relay가 비어 있으면 진행 중
        //    (종료 직후 리뷰 링크가 아직 안 붙은 순간도 여기로 온다 → 조기 정산보다 안전)
        if (score != null && relayText.isBlank()) return GameStatus.IN_PROGRESS;
        // 5) 그 외 점수가 있으면 종료
        if (score != null) return GameStatus.FINISHED;
        return GameStatus.SCHEDULED;
    }

    /** 진행 중 경기의 현재 이닝. relay 칸의 "5회" 형태에서 숫자만 추출 (그 외 null) */
    private Integer parseInning(String relayText, String noteText, Integer score) {
        if (resolveStatus(relayText, noteText, score) != GameStatus.IN_PROGRESS) return null;
        Matcher m = INNING_PATTERN.matcher(relayText);
        return m.find() ? Integer.parseInt(m.group(1)) : null;
    }

    /** 비고 칸이 정상값('-'/빈칸)이 아니면 취소 사유로 간주 ('우천취소', '그라운드사정' 등) */
    private boolean isCancelledNote(String note) {
        return note != null && !note.isBlank() && !"-".equals(note);
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
