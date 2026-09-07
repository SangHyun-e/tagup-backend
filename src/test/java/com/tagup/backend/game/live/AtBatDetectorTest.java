package com.tagup.backend.game.live;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 타석 감지 로직 검증.
 *
 * <p>픽스처는 <b>2026-09-01 LG-두산 실경기</b>를 60초 간격으로 폴링한 234샘플이다
 * (예정 27 / 진행 167 / 종료 40). 조작된 데이터가 아니라 실제 경기 흐름이므로,
 * 이 리플레이를 통과하면 로직이 현실에서 동작한다고 볼 수 있다.
 */
class AtBatDetectorTest {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();
    private final AtBatDetector detector = new AtBatDetector();

    private List<LiveGameSnapshot> loadFixture() {
        try (InputStream in = getClass().getResourceAsStream(
                "/fixtures/live-20260901-LG-두산.json")) {
            JsonNode arr = MAPPER.readTree(in);
            List<LiveGameSnapshot> list = new ArrayList<>();
            for (JsonNode n : arr) {
                list.add(new LiveGameSnapshot(
                        LiveGameState.fromCode(text(n, "state")),
                        intOrNull(n, "inning"),
                        HalfInning.from(text(n, "half")),
                        intOrNull(n, "awayScore"), intOrNull(n, "homeScore"),
                        intOrNull(n, "strike"), intOrNull(n, "ball"), intOrNull(n, "out"),
                        text(n, "awayPlayer"), text(n, "homePlayer"),
                        intOrNull(n, "r1"), intOrNull(n, "r2"), intOrNull(n, "r3")));
            }
            return list;
        } catch (Exception e) {
            throw new IllegalStateException("픽스처 로드 실패", e);
        }
    }

    private static String text(JsonNode n, String f) {
        JsonNode v = n.path(f);
        return v.isNull() ? null : v.asString(null);
    }

    private static Integer intOrNull(JsonNode n, String f) {
        JsonNode v = n.path(f);
        if (v.isNull() || v.isMissingNode()) return null;
        String s = v.asString("");
        if (s.isBlank()) return null;
        try {
            return Integer.valueOf(s.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private List<AtBatEvent> replay(List<LiveGameSnapshot> snaps) {
        List<AtBatEvent> events = new ArrayList<>();
        for (int i = 1; i < snaps.size(); i++) {
            detector.detect(snaps.get(i - 1), snaps.get(i)).ifPresent(events::add);
        }
        return events;
    }

    // ------------------------------------------------------------------
    @Test
    @DisplayName("실경기 리플레이: 타석이 감지되고 대부분 정산 가능한 결과가 나온다")
    void replayRealGame() {
        List<AtBatEvent> events = replay(loadFixture());

        assertThat(events)
                .as("60초 간격 폴링으로도 타석이 다수 감지되어야 한다")
                .hasSizeGreaterThan(40);

        long settleable = events.stream().filter(AtBatEvent::settleable).count();
        double ratio = (double) settleable / events.size();
        assertThat(ratio)
                .as("정산 가능(OUT/SAFE) 비율 — UNKNOWN이 많으면 배팅 정산에 못 쓴다")
                .isGreaterThan(0.9);

        // 타자·이닝 정보가 비어 있으면 배팅과 연결할 수 없다
        assertThat(events).allSatisfy(e -> {
            assertThat(e.batter()).isNotBlank();
            assertThat(e.inning()).isNotNull();
            assertThat(e.half()).isNotNull();
        });
    }

    @Test
    @DisplayName("실경기 리플레이: 아웃/세이프가 모두 나타나고 이닝 종료도 감지된다")
    void replayProducesBothOutcomes() {
        List<AtBatEvent> events = replay(loadFixture());

        assertThat(events).extracting(AtBatEvent::result)
                .contains(AtBatResult.OUT, AtBatResult.SAFE);

        assertThat(events).filteredOn(AtBatEvent::endedInning)
                .as("이닝을 끝낸 타석(3아웃)이 감지되어야 한다")
                .isNotEmpty();

        // 득점이 난 타석은 반드시 SAFE 또는 OUT(희생타)이며 UNKNOWN이면 안 된다
        assertThat(events).filteredOn(e -> e.runsScored() > 0)
                .allSatisfy(e -> assertThat(e.result()).isNotEqualTo(AtBatResult.UNKNOWN));
    }

    @Test
    @DisplayName("진행 중이 아닌 스냅샷은 타석을 만들지 않는다 (예정·종료 구간)")
    void ignoresNonLiveSnapshots() {
        List<LiveGameSnapshot> snaps = loadFixture();

        List<LiveGameSnapshot> scheduled = snaps.stream()
                .filter(s -> s.state() == LiveGameState.SCHEDULED).toList();
        List<LiveGameSnapshot> finished = snaps.stream()
                .filter(s -> s.state() == LiveGameState.FINISHED).toList();

        assertThat(scheduled).isNotEmpty();
        assertThat(finished).isNotEmpty();
        assertThat(replay(scheduled)).isEmpty();
        assertThat(replay(finished)).isEmpty();
    }

    // ------------------------------------------------------------------
    @Test
    @DisplayName("초/말에 따라 타자와 투수가 뒤바뀐다")
    void batterDependsOnHalfInning() {
        LiveGameSnapshot top = snapshot(1, HalfInning.TOP, 0, "박해민", "잭로그");
        assertThat(top.batter()).isEqualTo("박해민");   // 원정(LG) 공격
        assertThat(top.pitcher()).isEqualTo("잭로그");

        LiveGameSnapshot bottom = snapshot(1, HalfInning.BOTTOM, 0, "임찬규", "박찬호");
        assertThat(bottom.batter()).isEqualTo("박찬호"); // 홈(두산) 공격
        assertThat(bottom.pitcher()).isEqualTo("임찬규");
    }

    @Test
    @DisplayName("아웃카운트가 늘면 OUT, 주자만 바뀌면 SAFE")
    void outAndSafeRules() {
        LiveGameSnapshot before = snapshot(3, HalfInning.BOTTOM, 0, "임찬규", "타자A");

        LiveGameSnapshot outNext = snapshot(3, HalfInning.BOTTOM, 1, "임찬규", "타자B");
        assertThat(detector.detect(before, outNext))
                .get().extracting(AtBatEvent::result).isEqualTo(AtBatResult.OUT);

        LiveGameSnapshot safeNext = new LiveGameSnapshot(
                LiveGameState.IN_PROGRESS, 3, HalfInning.BOTTOM, 0, 0, 0, 0, 0,
                "임찬규", "타자B", 5, null, null);   // 1루에 주자 생김
        assertThat(detector.detect(before, safeNext))
                .get().extracting(AtBatEvent::result).isEqualTo(AtBatResult.SAFE);
    }

    @Test
    @DisplayName("이닝이 넘어가면 직전 타석은 3아웃으로 판정한다 (아웃카운트가 0으로 리셋되므로)")
    void inningChangeMeansThirdOut() {
        LiveGameSnapshot before = snapshot(3, HalfInning.BOTTOM, 2, "임찬규", "타자A");
        LiveGameSnapshot after = snapshot(4, HalfInning.TOP, 0, "타자C", "새투수");

        Optional<AtBatEvent> e = detector.detect(before, after);
        assertThat(e).isPresent();
        assertThat(e.get().result()).isEqualTo(AtBatResult.OUT);
        assertThat(e.get().endedInning()).isTrue();
        assertThat(e.get().batter()).isEqualTo("타자A");
    }

    @Test
    @DisplayName("같은 타자면 타석이 끝나지 않은 것 (투구만 진행)")
    void sameBatterMeansAtBatContinues() {
        LiveGameSnapshot s1 = snapshot(2, HalfInning.TOP, 1, "타자A", "투수A");
        LiveGameSnapshot s2 = new LiveGameSnapshot(
                LiveGameState.IN_PROGRESS, 2, HalfInning.TOP, 0, 0, 2, 1, 1,
                "타자A", "투수A", null, null, null);  // 볼카운트만 변함
        assertThat(detector.detect(s1, s2)).isEmpty();
    }

    private static LiveGameSnapshot snapshot(int inning, HalfInning half, int out,
                                             String awayPlayer, String homePlayer) {
        return new LiveGameSnapshot(LiveGameState.IN_PROGRESS, inning, half,
                0, 0, 0, 0, out, awayPlayer, homePlayer, null, null, null);
    }
}
