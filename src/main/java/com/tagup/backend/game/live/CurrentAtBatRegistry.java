package com.tagup.backend.game.live;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 경기별 "지금 타석"을 들고 있는 공유 상태.
 *
 * <p>{@link LiveGamePoller}가 쓰고 배팅 도메인이 읽는다. 폴러를 직접 주입하지 않는 이유는
 * 폴러가 설정에 따라 아예 만들어지지 않는 빈이기 때문이다({@code tagup.live.enabled}).
 * 이 레지스트리는 항상 존재하고, 폴러가 없으면 그냥 비어 있다 —
 * 그 경우 타석 배팅은 "진행 중인 타석 없음"으로 거절된다.
 */
@Component
public class CurrentAtBatRegistry {

    private final Map<String, CurrentAtBat> byGame = new ConcurrentHashMap<>();

    public void update(String kboGameId, LiveGameSnapshot snapshot, Instant detectedAt) {
        if (snapshot == null || !snapshot.isLive()) {
            byGame.remove(kboGameId);
            return;
        }
        CurrentAtBat atBat = new CurrentAtBat(
                snapshot.inning(), snapshot.half(), snapshot.batter(), detectedAt);
        if (!atBat.identifiable()) {
            byGame.remove(kboGameId);
            return;
        }
        byGame.put(kboGameId, atBat);
    }

    public void remove(String kboGameId) {
        byGame.remove(kboGameId);
    }

    public void clear() {
        byGame.clear();
    }

    public Optional<CurrentAtBat> find(String kboGameId) {
        return Optional.ofNullable(byGame.get(kboGameId));
    }
}
