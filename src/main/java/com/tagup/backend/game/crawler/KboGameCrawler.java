package com.tagup.backend.game.crawler;

import com.tagup.backend.game.entity.GameStatus;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class KboGameCrawler {

    // KBO 공식 사이트 단축명과 DB shortName 매핑
    private static final Map<String, String> TEAM_NAME_MAP = Map.ofEntries(
            Map.entry("KIA", "KIA"),
            Map.entry("기아", "KIA"),
            Map.entry("삼성", "삼성"),
            Map.entry("LG", "LG"),
            Map.entry("두산", "두산"),
            Map.entry("KT", "KT"),
            Map.entry("SSG", "SSG"),
            Map.entry("롯데", "롯데"),
            Map.entry("한화", "한화"),
            Map.entry("NC", "NC"),
            Map.entry("키움", "키움")
    );

    private static final String SCHEDULE_URL =
            "https://www.koreabaseball.com/Schedule/Schedule.aspx";

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    public List<CrawledGame> crawlByDate(LocalDate date) {
        String yyyymm = date.format(DateTimeFormatter.ofPattern("yyyyMM"));
        String dateStr = date.format(DateTimeFormatter.ofPattern("yyyyMMdd"));

        try {
            Document doc = Jsoup.connect(SCHEDULE_URL)
                    .userAgent("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36")
                    .header("Accept", "text/html,application/xhtml+xml")
                    .data("yyyymm", yyyymm)
                    .timeout(10_000)
                    .get();

            return parseScheduleTable(doc, date, dateStr);
        } catch (IOException e) {
            log.warn("KBO 크롤링 실패 date={}: {}", date, e.getMessage());
            return List.of();
        }
    }

    private List<CrawledGame> parseScheduleTable(Document doc, LocalDate date, String dateStr) {
        List<CrawledGame> result = new ArrayList<>();

        // KBO 공식 사이트 일정 테이블 파싱
        // 테이블 구조: 날짜 | 시간 | 원정팀 | 스코어 | 홈팀 | 경기장 | 상태
        Elements rows = doc.select("table.tbl > tbody > tr");

        String currentDate = null;

        for (Element row : rows) {
            Elements cells = row.select("td");
            if (cells.isEmpty()) continue;

            // 날짜 셀이 있으면 현재 날짜 업데이트
            Element dateTd = row.selectFirst("td.date");
            if (dateTd != null) {
                currentDate = dateTd.text().replaceAll("[^0-9]", "");
            }

            // 오늘 날짜 행만 파싱
            if (!dateStr.equals(currentDate) && currentDate != null && currentDate.length() == 8) {
                // 날짜가 바뀌었고 오늘이 아니면 스킵
                if (!date.format(DateTimeFormatter.ofPattern("yyyyMMdd")).equals(currentDate)) {
                    continue;
                }
            }

            CrawledGame game = parseGameRow(row, date, cells);
            if (game != null) {
                result.add(game);
            }
        }

        log.info("KBO 크롤링 완료 date={}, 경기 수={}", date, result.size());
        return result;
    }

    private CrawledGame parseGameRow(Element row, LocalDate date, Elements cells) {
        try {
            if (cells.size() < 5) return null;

            // 셀 인덱스는 KBO 실제 HTML 구조에 맞게 조정 필요
            String timeText = cells.get(0).text().trim();
            String awayTeamText = cells.get(1).text().trim();
            String scoreText = cells.get(2).text().trim();
            String homeTeamText = cells.get(3).text().trim();
            String stadiumText = cells.size() > 4 ? cells.get(4).text().trim() : "";
            String statusText = cells.size() > 5 ? cells.get(5).text().trim() : "";

            String awayShort = resolveTeamShortName(awayTeamText);
            String homeShort = resolveTeamShortName(homeTeamText);

            if (awayShort == null || homeShort == null) return null;

            LocalTime gameTime = parseTime(timeText);
            GameStatus status = resolveStatus(statusText, scoreText);
            int[] scores = parseScore(scoreText);

            String kboGameId = date.format(DateTimeFormatter.ofPattern("yyyyMMdd"))
                    + "_" + awayShort + "_" + homeShort;

            return CrawledGame.builder()
                    .kboGameId(kboGameId)
                    .gameDate(date)
                    .gameTime(gameTime)
                    .homeTeamShortName(homeShort)
                    .awayTeamShortName(awayShort)
                    .status(status)
                    .homeScore(scores[0] >= 0 ? scores[0] : null)
                    .awayScore(scores[1] >= 0 ? scores[1] : null)
                    .stadium(stadiumText.isEmpty() ? null : stadiumText)
                    .build();

        } catch (Exception e) {
            log.debug("경기 행 파싱 실패: {}", e.getMessage());
            return null;
        }
    }

    private String resolveTeamShortName(String text) {
        for (Map.Entry<String, String> entry : TEAM_NAME_MAP.entrySet()) {
            if (text.contains(entry.getKey())) {
                return entry.getValue();
            }
        }
        return null;
    }

    private LocalTime parseTime(String text) {
        try {
            return LocalTime.parse(text, TIME_FORMATTER);
        } catch (Exception e) {
            return null;
        }
    }

    private GameStatus resolveStatus(String statusText, String scoreText) {
        if (statusText.contains("취소") || statusText.contains("우천")) return GameStatus.CANCELLED;
        if (statusText.contains("진행") || statusText.contains("회")) return GameStatus.IN_PROGRESS;
        if (scoreText.contains(":") && !scoreText.contains("vs") && !scoreText.isBlank()) {
            return GameStatus.FINISHED;
        }
        return GameStatus.SCHEDULED;
    }

    private int[] parseScore(String scoreText) {
        // "3:5" 형식 파싱 → [홈, 원정] (KBO는 원정:홈 표기)
        try {
            if (scoreText.contains(":")) {
                String[] parts = scoreText.split(":");
                int away = Integer.parseInt(parts[0].trim());
                int home = Integer.parseInt(parts[1].trim());
                return new int[]{home, away};
            }
        } catch (Exception ignored) {}
        return new int[]{-1, -1};
    }
}
