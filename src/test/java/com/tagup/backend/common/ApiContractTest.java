package com.tagup.backend.common;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import com.tagup.backend.bet.dto.BetResponse;
import com.tagup.backend.bet.entity.BetResult;
import com.tagup.backend.bet.entity.BetStatus;
import com.tagup.backend.common.response.ApiResponse;
import com.tagup.backend.game.dto.GameResponse;
import com.tagup.backend.game.entity.GameStatus;
import com.tagup.backend.room.dto.RoomMemberResponse;
import com.tagup.backend.room.dto.RoomResponse;
import com.tagup.backend.team.dto.TeamResponse;
import com.tagup.backend.user.dto.UserProfileResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * API 응답 계약(스키마) 테스트.
 *
 * <p>이 프로젝트는 FE-BE 계약 불일치로 3번 사고가 났다:
 * <ol>
 *   <li>인증 방식 미스매치 (자체 JWT ↔ Firebase ID Token)</li>
 *   <li>내기 응답 구조 (FE {@code proposerId} 플랫 ↔ BE {@code proposer} 중첩),
 *       상태값 (FE {@code COMPLETED} ↔ BE {@code FINISHED})</li>
 *   <li>경기 상태값 (FE {@code LIVE} ↔ BE {@code IN_PROGRESS}), {@code inning} 필드 누락</li>
 * </ol>
 *
 * <p><b>필드를 추가/삭제/개명하면 이 테스트가 깨진다. 그게 의도다.</b>
 * 깨지면 (1) FE에 변경을 알리고 (2) CHANGELOG에 응답 JSON 예시를 기재한 뒤(I-012)
 * 이 테스트의 기대값을 갱신할 것.
 */
class ApiContractTest {

    /** Spring Boot가 실제 HTTP 응답 직렬화에 쓰는 매퍼를 그대로 사용 (Boot 4 = Jackson 3 JsonMapper) */
    private static final JsonMapper MAPPER = resolveSpringJsonMapper();

    private static JsonMapper resolveSpringJsonMapper() {
        final JsonMapper[] holder = new JsonMapper[1];
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration.class))
                .run(ctx -> holder[0] = ctx.getBean(JsonMapper.class));
        return holder[0];
    }

    private static JsonNode toJson(Object o) {
        try {
            return MAPPER.readTree(MAPPER.writeValueAsString(o));
        } catch (Exception e) {
            throw new IllegalStateException("직렬화 실패", e);
        }
    }

    /** 해당 노드의 필드명을 선언 순서대로 반환 */
    private static List<String> keysOf(JsonNode node) {
        List<String> keys = new ArrayList<>();
        keys.addAll(node.propertyNames());
        return keys;
    }

    // ------------------------------------------------------------------
    @Nested
    @DisplayName("공통 래퍼")
    class Wrapper {

        @Test
        @DisplayName("ApiResponse는 success/message/data 구조이며, message가 null이면 생략된다")
        void apiResponseShape() {
            assertThat(keysOf(toJson(ApiResponse.ok("메시지", 1))))
                    .containsExactlyInAnyOrder("success", "message", "data");

            // NON_NULL 정책 — FE는 message 부재를 허용해야 함
            assertThat(keysOf(toJson(ApiResponse.ok(1))))
                    .containsExactlyInAnyOrder("success", "data");

            assertThat(toJson(ApiResponse.fail("에러")).get("success").asBoolean()).isFalse();
        }
    }

    // ------------------------------------------------------------------
    @Nested
    @DisplayName("경기 (Game)")
    class Game {

        private GameResponse sample(GameStatus status, Integer inning) {
            return new GameResponse(
                    1L, "20260826NCLG0", LocalDate.of(2026, 8, 26), LocalTime.of(18, 30),
                    new GameResponse.TeamInfo(3L, "LG 트윈스", "LG", "http://x/lg.png"),
                    new GameResponse.TeamInfo(9L, "NC 다이노스", "NC", "http://x/nc.png"),
                    status, 2, 1, inning, "잠실");
        }

        @Test
        @DisplayName("응답 필드 구성이 고정되어 있다 (inning 포함 — I-013 재발 방지)")
        void gameResponseShape() {
            JsonNode json = toJson(sample(GameStatus.IN_PROGRESS, 5));
            assertThat(keysOf(json)).containsExactlyInAnyOrder(
                    "id", "kboGameId", "gameDate", "gameTime",
                    "homeTeam", "awayTeam", "status",
                    "homeScore", "awayScore", "inning", "stadium");
            assertThat(keysOf(json.get("homeTeam")))
                    .containsExactlyInAnyOrder("id", "name", "shortName", "logoUrl");
        }

        @Test
        @DisplayName("status 문자열은 SCHEDULED/IN_PROGRESS/FINISHED/CANCELLED 4종뿐이다 (LIVE·FINAL 없음)")
        void gameStatusValues() {
            assertThat(Arrays.stream(GameStatus.values()).map(Enum::name))
                    .containsExactlyInAnyOrder("SCHEDULED", "IN_PROGRESS", "FINISHED", "CANCELLED");

            // FE가 한때 기대하던 값들 — 실재하지 않음이 계약이다
            assertThat(Arrays.stream(GameStatus.values()).map(Enum::name))
                    .doesNotContain("LIVE", "FINAL");

            assertThat(toJson(sample(GameStatus.IN_PROGRESS, 5)).get("status").asText())
                    .isEqualTo("IN_PROGRESS");
        }

        @Test
        @DisplayName("진행 중이 아니면 inning은 null이지만 필드 자체는 남는다")
        void inningNullable() {
            JsonNode json = toJson(sample(GameStatus.SCHEDULED, null));
            assertThat(json.has("inning")).isTrue();
            assertThat(json.get("inning").isNull()).isTrue();
        }
    }

    // ------------------------------------------------------------------
    @Nested
    @DisplayName("배팅 (Bet)")
    class Bet {

        private BetResponse sample(BetResponse.ReceiverInfo receiver,
                                   BetStatus status, BetResult result) {
            return new BetResponse(
                    1L,
                    new BetResponse.ProposerInfo(1L, "철수"),
                    receiver,
                    3L,
                    new BetResponse.TeamInfo(3L, "LG"),
                    "커피 한 잔",
                    status, result,
                    new BetResponse.GameSummary(1L, "LG", "NC", "2026-08-26"),
                    LocalDateTime.of(2026, 8, 26, 12, 0));
        }

        @Test
        @DisplayName("proposer/receiver는 중첩 객체다 (플랫 proposerId 아님 — 계약 사고 #2)")
        void betResponseShape() {
            JsonNode json = toJson(sample(new BetResponse.ReceiverInfo(2L, "영희"),
                    BetStatus.ACCEPTED, null));

            assertThat(keysOf(json)).containsExactlyInAnyOrder(
                    "id", "proposer", "receiver", "betOnTeamId", "betOnTeam",
                    "content", "status", "proposerResult", "game", "createdAt");

            assertThat(keysOf(json.get("proposer"))).containsExactlyInAnyOrder("id", "nickname");
            assertThat(keysOf(json.get("receiver"))).containsExactlyInAnyOrder("id", "nickname");
            assertThat(keysOf(json.get("betOnTeam"))).containsExactlyInAnyOrder("id", "shortName");
            assertThat(keysOf(json.get("game")))
                    .containsExactlyInAnyOrder("id", "homeTeam", "awayTeam", "gameDate");

            // 플랫 필드는 존재하지 않는다
            assertThat(json.has("proposerId")).isFalse();
            assertThat(json.has("proposerNickname")).isFalse();
        }

        @Test
        @DisplayName("오픈 배팅: 콜 전에는 receiver가 null이지만 필드는 남는다")
        void receiverNullBeforeCall() {
            JsonNode json = toJson(sample(null, BetStatus.PENDING, null));
            assertThat(json.has("receiver")).isTrue();
            assertThat(json.get("receiver").isNull()).isTrue();
        }

        @Test
        @DisplayName("status는 PENDING/ACCEPTED/FINISHED/CANCELLED (COMPLETED 아님 — 계약 사고 #2)")
        void betStatusValues() {
            assertThat(Arrays.stream(BetStatus.values()).map(Enum::name))
                    .containsExactlyInAnyOrder("PENDING", "ACCEPTED", "FINISHED", "CANCELLED");
            assertThat(Arrays.stream(BetStatus.values()).map(Enum::name))
                    .doesNotContain("COMPLETED");
        }

        @Test
        @DisplayName("정산 결과는 제안자 기준 WIN/LOSE/DRAW (SAFE/OUT 아님)")
        void betResultValues() {
            assertThat(Arrays.stream(BetResult.values()).map(Enum::name))
                    .containsExactlyInAnyOrder("WIN", "LOSE", "DRAW");
            assertThat(Arrays.stream(BetResult.values()).map(Enum::name))
                    .doesNotContain("SAFE", "OUT");

            assertThat(toJson(sample(new BetResponse.ReceiverInfo(2L, "영희"),
                    BetStatus.FINISHED, BetResult.WIN)).get("proposerResult").asText())
                    .isEqualTo("WIN");
        }
    }

    // ------------------------------------------------------------------
    @Nested
    @DisplayName("더그아웃 / 유저")
    class RoomAndUser {

        @Test
        @DisplayName("RoomResponse는 chatKey를 포함한다 (Firestore 채팅 경로 키)")
        void roomResponseShape() {
            JsonNode json = toJson(new RoomResponse(
                    1L, "TAG1A3", "b8905ced-aff3-41e9-88fd-1a614bf7c1da",
                    "우리 더그아웃", "철수", true, null, LocalDateTime.of(2026, 8, 26, 12, 0)));

            assertThat(keysOf(json)).containsExactlyInAnyOrder(
                    "id", "tagCode", "chatKey", "name",
                    "createdByNickname", "chatEnabled", "watchingGame", "createdAt");
        }

        @Test
        @DisplayName("RoomMemberResponse / UserProfileResponse 필드 구성")
        void memberAndProfileShape() {
            assertThat(keysOf(toJson(new RoomMemberResponse(
                    1L, "철수", "LG 트윈스", LocalDateTime.of(2026, 8, 26, 12, 0)))))
                    .containsExactlyInAnyOrder("id", "nickname", "favoriteTeamName", "joinedAt");

            JsonNode profile = toJson(new UserProfileResponse(
                    1L, "a@b.com", "철수",
                    new TeamResponse(3L, "LG 트윈스", "LG", "http://x/lg.png")));
            assertThat(keysOf(profile))
                    .containsExactlyInAnyOrder("id", "email", "nickname", "favoriteTeam");
            assertThat(keysOf(profile.get("favoriteTeam")))
                    .containsExactlyInAnyOrder("id", "name", "shortName", "logoUrl");
        }

        @Test
        @DisplayName("팀 미설정 유저는 favoriteTeam이 null이지만 필드는 남는다")
        void favoriteTeamNullable() {
            JsonNode json = toJson(new UserProfileResponse(1L, "a@b.com", "철수", null));
            assertThat(json.has("favoriteTeam")).isTrue();
            assertThat(json.get("favoriteTeam").isNull()).isTrue();
        }
    }

    // ------------------------------------------------------------------
    @Test
    @DisplayName("날짜/시간은 ISO 문자열로 직렬화된다 (타임스탬프 숫자 아님)")
    void dateTimeSerializedAsIso() {
        JsonNode json = toJson(new GameResponse(
                1L, "G", LocalDate.of(2026, 8, 26), LocalTime.of(18, 30),
                new GameResponse.TeamInfo(1L, "n", "s", "u"),
                new GameResponse.TeamInfo(2L, "n", "s", "u"),
                GameStatus.SCHEDULED, null, null, null, "잠실"));

        assertThat(json.get("gameDate").asText()).isEqualTo("2026-08-26");
        assertThat(json.get("gameTime").asText()).startsWith("18:30");
    }
}
