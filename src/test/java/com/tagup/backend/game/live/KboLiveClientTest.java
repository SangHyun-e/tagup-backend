package com.tagup.backend.game.live;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * GetKboGameList 응답 파싱 검증.
 *
 * <p>픽스처는 2026-08-28 / 09-01 실제 관측 응답의 구조를 그대로 옮긴 것이다.
 * 특히 <b>같은 성격의 필드가 문자열과 숫자로 뒤섞여</b> 오고, 본문이 <b>UTF-8 BOM</b>으로
 * 시작하는 점이 실제 응답의 특징이다.
 */
class KboLiveClientTest {

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    // 맨 앞 ﻿ 는 실제 응답의 BOM
    private static final String SAMPLE = "﻿" + """
        { "game": [
          { "AWAY_NM":"LG", "HOME_NM":"두산",
            "GAME_STATE_SC":"2", "GAME_INN_NO":1, "GAME_TB_SC_NM":"초",
            "T_SCORE_CN":"0", "B_SCORE_CN":"0",
            "STRIKE_CN":"1", "BALL_CN":"2", "OUT_CN":"1",
            "B1_BAT_ORDER_NO":3, "B2_BAT_ORDER_NO":null, "B3_BAT_ORDER_NO":null,
            "T_P_NM":"박해민", "B_P_NM":"잭로그", "CANCEL_SC_NM":null },

          { "AWAY_NM":"키움", "HOME_NM":"두산",
            "GAME_STATE_SC":"4", "GAME_INN_NO":0, "GAME_TB_SC_NM":"초",
            "T_SCORE_CN":"0", "B_SCORE_CN":"0",
            "STRIKE_CN":null, "BALL_CN":null, "OUT_CN":null,
            "B1_BAT_ORDER_NO":null, "B2_BAT_ORDER_NO":null, "B3_BAT_ORDER_NO":null,
            "T_P_NM":"", "B_P_NM":"", "CANCEL_SC_NM":"그라운드사정" },

          { "AWAY_NM":"SSG", "HOME_NM":"KIA",
            "GAME_STATE_SC":"1", "GAME_INN_NO":0, "GAME_TB_SC_NM":"",
            "T_SCORE_CN":"", "B_SCORE_CN":"",
            "STRIKE_CN":"", "BALL_CN":"", "OUT_CN":"",
            "T_P_NM":"", "B_P_NM":"", "CANCEL_SC_NM":null }
        ] }
        """;

    private KboLiveClient clientReturning(String body) {
        RestTemplate restTemplate = mock(RestTemplate.class);
        when(restTemplate.exchange(any(String.class), any(), any(), eq(String.class)))
                .thenReturn(ResponseEntity.ok(body));
        return new KboLiveClient(restTemplate, jsonMapper);
    }

    @Test
    void 응답_키가_Game_엔티티의_kboGameId_형식과_일치한다() {
        Map<String, LiveGameSnapshot> snaps =
                clientReturning(SAMPLE).fetchSnapshots(LocalDate.of(2026, 9, 1));

        // 이 형식이 어긋나면 폴러가 우리 DB의 경기와 조인하지 못해 조용히 아무것도 안 한다
        assertThat(snaps.keySet())
                .containsExactlyInAnyOrder("20260901_LG_두산", "20260901_키움_두산", "20260901_SSG_KIA");
    }

    @Test
    void BOM으로_시작해도_파싱된다() {
        assertThat(clientReturning(SAMPLE).fetchSnapshots(LocalDate.of(2026, 9, 1)))
                .as("본문 맨 앞의 BOM을 제거하지 않으면 파싱 자체가 실패한다")
                .isNotEmpty();
    }

    @Test
    void 문자열과_숫자가_섞여_와도_같은_타입으로_읽는다() {
        LiveGameSnapshot live = clientReturning(SAMPLE)
                .fetchSnapshots(LocalDate.of(2026, 9, 1)).get("20260901_LG_두산");

        assertThat(live.inning()).isEqualTo(1);      // 숫자로 옴
        assertThat(live.strike()).isEqualTo(1);      // 문자열 "1" 로 옴
        assertThat(live.ball()).isEqualTo(2);
        assertThat(live.out()).isEqualTo(1);
        assertThat(live.runner1()).isEqualTo(3);
        assertThat(live.runner2()).isNull();
    }

    @Test
    void 빈_문자열과_null은_모두_null로_읽는다() {
        LiveGameSnapshot scheduled = clientReturning(SAMPLE)
                .fetchSnapshots(LocalDate.of(2026, 9, 1)).get("20260901_SSG_KIA");

        assertThat(scheduled.state()).isEqualTo(LiveGameState.SCHEDULED);
        assertThat(scheduled.half()).isNull();
        assertThat(scheduled.awayScore()).isNull();
        assertThat(scheduled.out()).isNull();
    }

    @Test
    void 상태코드가_스냅샷_상태로_변환된다() {
        Map<String, LiveGameSnapshot> snaps =
                clientReturning(SAMPLE).fetchSnapshots(LocalDate.of(2026, 9, 1));

        assertThat(snaps.get("20260901_LG_두산").state()).isEqualTo(LiveGameState.IN_PROGRESS);
        assertThat(snaps.get("20260901_LG_두산").isLive()).isTrue();
        assertThat(snaps.get("20260901_키움_두산").state()).isEqualTo(LiveGameState.CANCELLED);
        assertThat(snaps.get("20260901_SSG_KIA").state()).isEqualTo(LiveGameState.SCHEDULED);
    }

    /**
     * T_P_NM/B_P_NM은 "원정팀/홈팀의 현재 선수"라서 초·말에 따라 타자와 투수가 뒤바뀐다.
     * 이 매핑이 어긋나면 타석 감지가 투수 이름으로 이뤄져 전부 틀어진다.
     */
    @Test
    void 초에는_원정선수가_타자로_읽힌다() {
        LiveGameSnapshot top = clientReturning(SAMPLE)
                .fetchSnapshots(LocalDate.of(2026, 9, 1)).get("20260901_LG_두산");

        assertThat(top.half()).isEqualTo(HalfInning.TOP);
        assertThat(top.batter()).isEqualTo("박해민");   // LG(원정) 타자
        assertThat(top.pitcher()).isEqualTo("잭로그");  // 두산(홈) 투수
    }

    @Test
    void 요청_실패시_예외_대신_빈_맵을_돌려준다() {
        RestTemplate failing = mock(RestTemplate.class);
        when(failing.exchange(any(String.class), any(), any(), eq(String.class)))
                .thenThrow(new RuntimeException("connection reset"));

        assertThat(new KboLiveClient(failing, jsonMapper).fetchSnapshots(LocalDate.now()))
                .as("폴링은 다음 주기에 재시도하면 되므로 예외가 스케줄러를 죽이면 안 된다")
                .isEmpty();
    }
}
