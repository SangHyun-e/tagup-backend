package com.tagup.backend.game.crawler;

import tools.jackson.databind.json.JsonMapper;
import com.tagup.backend.game.entity.GameStatus;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * KBO 일정 파싱의 경기 상태 판정 검증.
 * 실제 KBO GetScheduleList 응답의 셀 구조를 픽스처로 사용한다.
 * (핵심: 취소 사유는 relay 칸이 아니라 마지막 비고 칸에 들어온다)
 */
class KboGameCrawlerTest {

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    // 실제 KBO 응답 구조를 그대로 옮긴 픽스처 (2026-07)
    // day 셀이 있는 행(9칸) / 없는 행(8칸) 혼재, 비고 칸은 항상 마지막
    private static final String SAMPLE_JSON = """
        { "rows": [
          { "row": [
            {"Class":"day","Text":"07.22(수)"}, {"Class":"time","Text":"18:30"},
            {"Class":"play","Text":"NCvsLG"}, {"Class":"relay","Text":""},
            {"Text":""}, {"Text":"SS-T"}, {"Text":""}, {"Text":"잠실"}, {"Text":"우천취소"} ] },
          { "row": [
            {"Class":"time","Text":"18:30"},
            {"Class":"play","Text":"SSG7vs3롯데"}, {"Class":"relay","Text":"리뷰"},
            {"Text":"하이라이트"}, {"Text":"SPO-2T"}, {"Text":""}, {"Text":"사직"}, {"Text":"-"} ] },
          { "row": [
            {"Class":"time","Text":"18:30"},
            {"Class":"play","Text":"두산vsKT"}, {"Class":"relay","Text":""},
            {"Text":""}, {"Text":"SPO-T"}, {"Text":""}, {"Text":"수원"}, {"Text":"그라운드사정"} ] },
          { "row": [
            {"Class":"day","Text":"07.23(목)"}, {"Class":"time","Text":"18:30"},
            {"Class":"play","Text":"NCvsLG"}, {"Class":"relay","Text":"프리뷰"},
            {"Text":""}, {"Text":"SS-T"}, {"Text":""}, {"Text":"잠실"}, {"Text":"-"} ] },
          { "row": [
            {"Class":"time","Text":"18:30"},
            {"Class":"play","Text":"한화vsKIA"}, {"Class":"relay","Text":"5회"},
            {"Text":""}, {"Text":"KBSN"}, {"Text":""}, {"Text":"광주"}, {"Text":"-"} ] },
          { "row": [
            {"Class":"day","Text":"08.26(수)"}, {"Class":"time","Text":"18:30"},
            {"Class":"play","Text":"NC0vs0LG"}, {"Class":"relay","Text":""},
            {"Text":""}, {"Text":"SS-T"}, {"Text":""}, {"Text":"잠실"}, {"Text":"-"} ] },
          { "row": [
            {"Class":"time","Text":"18:30"},
            {"Class":"play","Text":"삼성12vs2키움"}, {"Class":"relay","Text":"리뷰"},
            {"Text":"하이라이트"}, {"Text":"SPO"}, {"Text":""}, {"Text":"고척"}, {"Text":"-"} ] }
        ] }
        """;

    private KboGameCrawler crawlerReturning(String json) {
        RestTemplate restTemplate = mock(RestTemplate.class);
        when(restTemplate.exchange(any(String.class), any(), any(), eq(String.class)))
                .thenReturn(ResponseEntity.ok(json));
        return new KboGameCrawler(restTemplate, jsonMapper);
    }

    @Test
    void 우천취소_그라운드사정_경기는_CANCELLED로_판정된다() {
        List<CrawledGame> games = crawlerReturning(SAMPLE_JSON).crawlByMonth(2026, 7);
        Map<String, CrawledGame> byId = games.stream()
                .collect(Collectors.toMap(CrawledGame::kboGameId, Function.identity()));

        // 우천취소 (비고 칸) → CANCELLED, 점수 없음
        CrawledGame rainOut = byId.get("20260722_NC_LG");
        assertThat(rainOut.status()).isEqualTo(GameStatus.CANCELLED);
        assertThat(rainOut.awayScore()).isNull();

        // 그라운드사정 (비고 칸, '취소' 글자 없음) → CANCELLED
        assertThat(byId.get("20260722_두산_KT").status()).isEqualTo(GameStatus.CANCELLED);
    }

    @Test
    void 점수있는_경기는_FINISHED_예정경기는_SCHEDULED로_판정된다() {
        Map<String, CrawledGame> byId = crawlerReturning(SAMPLE_JSON).crawlByMonth(2026, 7).stream()
                .collect(Collectors.toMap(CrawledGame::kboGameId, Function.identity()));

        CrawledGame finished = byId.get("20260722_SSG_롯데");
        assertThat(finished.status()).isEqualTo(GameStatus.FINISHED);
        assertThat(finished.awayScore()).isEqualTo(7);
        assertThat(finished.homeScore()).isEqualTo(3);

        // 비고 '-', relay '프리뷰' → SCHEDULED
        CrawledGame scheduled = byId.get("20260723_NC_LG");
        assertThat(scheduled.status()).isEqualTo(GameStatus.SCHEDULED);
        assertThat(scheduled.inning()).isNull();
    }

    /**
     * 2026-08-26 야간 경기 5회 스냅샷으로 확인한 실제 동작에 대한 회귀 테스트.
     *
     * <p>진행 중에도 점수 칸이 "0vs0"으로 채워진다. 과거 로직("점수가 있으면 종료")은
     * 경기 시작 직후 FINISHED로 판정했고, 정산 스케줄러가 이를 0:0 무승부로 정산해
     * <b>모든 배팅이 경기 시작 30분 안에 잘못 정산되는</b> 치명적 버그가 있었다.
     */
    @Test
    void 진행중_경기는_점수칸이_0vs0이어도_FINISHED가_아니다() {
        Map<String, CrawledGame> byId = crawlerReturning(SAMPLE_JSON).crawlByMonth(2026, 8).stream()
                .collect(Collectors.toMap(CrawledGame::kboGameId, Function.identity()));

        CrawledGame live = byId.get("20260826_NC_LG");
        assertThat(live.status())
                .as("relay가 비어 있고 점수 칸이 0vs0이면 진행 중이다 (종료 아님)")
                .isEqualTo(GameStatus.IN_PROGRESS);

        // 0:0은 실제 스코어가 아니므로 저장하지 않는다 — 정산에 쓰이면 무승부로 오판된다
        assertThat(live.awayScore()).isNull();
        assertThat(live.homeScore()).isNull();
    }

    @Test
    void 종료_경기는_리뷰_링크와_실제_스코어로_판정한다() {
        Map<String, CrawledGame> byId = crawlerReturning(SAMPLE_JSON).crawlByMonth(2026, 8).stream()
                .collect(Collectors.toMap(CrawledGame::kboGameId, Function.identity()));

        CrawledGame finished = byId.get("20260826_삼성_키움");
        assertThat(finished.status()).isEqualTo(GameStatus.FINISHED);
        assertThat(finished.awayScore()).isEqualTo(12);
        assertThat(finished.homeScore()).isEqualTo(2);
    }

    @Test
    void 진행중_경기는_IN_PROGRESS이고_relay에서_이닝을_추출한다() {
        Map<String, CrawledGame> byId = crawlerReturning(SAMPLE_JSON).crawlByMonth(2026, 7).stream()
                .collect(Collectors.toMap(CrawledGame::kboGameId, Function.identity()));

        // relay "5회" → IN_PROGRESS + inning 5
        CrawledGame live = byId.get("20260723_한화_KIA");
        assertThat(live.status()).isEqualTo(GameStatus.IN_PROGRESS);
        assertThat(live.inning()).isEqualTo(5);

        // 종료/취소 경기는 이닝 없음
        assertThat(byId.get("20260722_SSG_롯데").inning()).isNull();
        assertThat(byId.get("20260722_NC_LG").inning()).isNull();
    }
}
