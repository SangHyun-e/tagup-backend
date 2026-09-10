package com.tagup.backend.game.live;

import com.tagup.backend.game.entity.Game;
import com.tagup.backend.game.entity.GameStatus;
import com.tagup.backend.game.repository.GameRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 진행 중인 경기를 짧은 주기로 폴링해 <b>타석 종료를 실시간 감지</b>한다.
 *
 * <p>{@link AtBatDetector}는 연속한 두 스냅샷의 차이로 결과를 역산하므로, 폴링 간격이
 * 길면 타석을 통째로 건너뛴다. 2026-09-01 60초 간격 리플레이에서는 70~80타석 중 62개만
 * 잡혔다(약 80%). 그래서 기본 간격을 15초로 잡았다.
 *
 * <p><b>기본 비활성이다.</b> {@code tagup.live.enabled=true}일 때만 빈이 만들어진다.
 * 외부 사이트를 짧은 주기로 호출하는 동작이라 (I-001 법적 검토 미결) 명시적으로 켜야 한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "tagup.live.enabled", havingValue = "true")
public class LiveGamePoller {

    private final KboLiveClient liveClient;
    private final GameRepository gameRepository;
    private final AtBatDetector detector = new AtBatDetector();

    /** kboGameId → 직전 스냅샷 */
    private final Map<String, LiveGameSnapshot> lastSnapshots = new ConcurrentHashMap<>();

    /** 그날 첫 수집에만 요약을 남긴다 — 폴러가 살아 있고 응답이 파싱된다는 증거 */
    private LocalDate loggedFor;

    @Value("${tagup.live.poll-interval-ms:15000}")
    private long pollIntervalMs;

    @Scheduled(fixedDelayString = "${tagup.live.poll-interval-ms:15000}")
    public void poll() {
        LocalDate today = LocalDate.now();

        // 오늘 볼 경기가 없으면 KBO를 호출하지 않는다 (불필요한 트래픽 차단)
        List<Game> watchable = gameRepository.findByGameDateAndStatusIn(
                today, List.of(GameStatus.SCHEDULED, GameStatus.IN_PROGRESS));
        if (watchable.isEmpty()) {
            lastSnapshots.clear();
            return;
        }

        Map<String, LiveGameSnapshot> snapshots = liveClient.fetchSnapshots(today);
        if (snapshots.isEmpty()) return;

        if (!today.equals(loggedFor)) {
            loggedFor = today;
            logFirstPoll(watchable, snapshots);
        }

        for (Game game : watchable) {
            LiveGameSnapshot cur = snapshots.get(game.getKboGameId());
            if (cur == null) continue;

            LiveGameSnapshot prev = lastSnapshots.put(game.getKboGameId(), cur);
            if (prev == null) {
                if (cur.isLive()) logStart(game.getKboGameId(), cur);
                continue;
            }

            logStateTransition(game.getKboGameId(), prev, cur);

            detector.detect(prev, cur)
                    .ifPresent(event -> logAtBat(game.getKboGameId(), event, cur));
        }
    }

    /** 현재 감지 중인 경기 수 (운영 확인용) */
    public int trackedGameCount() {
        return lastSnapshots.size();
    }

    /** 특정 경기의 최신 스냅샷 */
    public Optional<LiveGameSnapshot> latest(String kboGameId) {
        return Optional.ofNullable(lastSnapshots.get(kboGameId));
    }

    /**
     * 그날 첫 수집 요약.
     *
     * <p>단순히 "몇 건 받았다"로는 부족하다. 우리 {@code kboGameId}와 KBO 응답 키가
     * 어긋나면 폴러는 <b>예외도 로그도 없이 아무 일도 하지 않는다.</b> 그 상태로 경기를
     * 통째로 흘려보내지 않도록, 매칭되지 않은 경기를 이름까지 찍어 남긴다.
     */
    private void logFirstPoll(List<Game> watchable, Map<String, LiveGameSnapshot> snapshots) {
        List<String> unmatched = watchable.stream()
                .map(Game::getKboGameId)
                .filter(id -> !snapshots.containsKey(id))
                .toList();

        log.info("[라이브] 폴링 시작 — 감시 {}경기 중 {}경기 매칭, KBO 응답 {}건, 주기 {}초",
                watchable.size(), watchable.size() - unmatched.size(),
                snapshots.size(), pollIntervalMs / 1000);

        if (!unmatched.isEmpty()) {
            log.warn("[라이브] 매칭 실패 {}건 — 우리 ID {} / KBO 키 {}",
                    unmatched.size(), unmatched, snapshots.keySet());
        }
    }

    private void logStart(String gameId, LiveGameSnapshot cur) {
        log.info("[라이브] {} 추적 시작 — {}회{} {}:{}",
                gameId, cur.inning(), half(cur), cur.awayScore(), cur.homeScore());
    }

    private void logStateTransition(String gameId, LiveGameSnapshot prev, LiveGameSnapshot cur) {
        if (prev.state() != cur.state()) {
            log.info("[라이브] {} 상태 전이 {} → {}", gameId, prev.state(), cur.state());
        }
    }

    private void logAtBat(String gameId, AtBatEvent e, LiveGameSnapshot cur) {
        log.info("[타석] {} {}회{} {} (투수 {}) → {}{}{} | 현재 {}:{} {}아웃",
                gameId, e.inning(), e.half() == null ? "" : e.half().korean(),
                e.batter(), e.pitcher(), e.result(),
                e.runsScored() > 0 ? " +" + e.runsScored() + "점" : "",
                e.endedInning() ? " (이닝 종료)" : "",
                cur.awayScore(), cur.homeScore(), cur.out());
    }

    private String half(LiveGameSnapshot s) {
        return s.half() == null ? "" : s.half().korean();
    }
}
