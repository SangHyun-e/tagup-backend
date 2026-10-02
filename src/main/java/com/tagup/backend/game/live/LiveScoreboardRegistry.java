package com.tagup.backend.game.live;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 경기별 최신 스코어보드를 들고 있는 공유 상태.
 *
 * <p>{@link LiveGamePoller}가 쓰고 API가 읽는다. 폴러는 설정에 따라 아예 만들어지지 않는 빈이라
 * ({@code tagup.live.enabled}) 직접 주입하지 않는다. 이 레지스트리는 항상 존재하고, 폴러가 없으면
 * 비어 있을 뿐이다 — 그 경우 API는 "지금 상태 없음"으로 답한다.
 *
 * <p><b>오래된 값은 없는 것으로 친다.</b> 절전·네트워크 끊김으로 폴링이 멈추면 마지막 스냅샷이
 * 그대로 남는데, 그걸 "지금 점수"로 내보내면 앱이 멈춘 경기를 생중계처럼 보여준다.
 */
@Component
public class LiveScoreboardRegistry {

    private final Map<String, LiveScoreboard> byGame = new ConcurrentHashMap<>();
    private final Clock clock;

    @Value("${tagup.live.state-ttl-ms:60000}")
    private long ttlMs;

    public LiveScoreboardRegistry(Clock clock) {
        this.clock = clock;
    }

    public void update(String kboGameId, LiveGameSnapshot snapshot) {
        if (snapshot == null || !snapshot.isLive()) {
            byGame.remove(kboGameId);
            return;
        }
        byGame.put(kboGameId, LiveScoreboard.of(snapshot, clock.instant()));
    }

    public void remove(String kboGameId) {
        byGame.remove(kboGameId);
    }

    public void clear() {
        byGame.clear();
    }

    /** 신선한 값만 돌려준다. 폴링이 멈춘 뒤의 값은 비어 있는 것으로 취급한다 */
    public Optional<LiveScoreboard> find(String kboGameId) {
        return Optional.ofNullable(byGame.get(kboGameId))
                .filter(board -> board.isFresh(clock.instant(), Duration.ofMillis(ttlMs)));
    }
}
