package com.tagup.backend.game.live;

import java.time.Duration;
import java.time.Instant;

/**
 * 지금 진행 중인 타석. 타석 배팅은 이 정보를 기준으로 만들어진다.
 *
 * @param startedAt 이 타석을 <b>감지한</b> 시각 (실제 타석 시작보다 최대 폴링 간격만큼 늦다)
 */
public record CurrentAtBat(Integer inning, HalfInning half, String batter, Instant startedAt) {

    /**
     * 아직 배팅을 받을 수 있는지.
     *
     * <p>창을 짧게 두는 이유는 시간이 부족해서가 아니라 <b>정보 비대칭</b> 때문이다.
     * 폴링 간격만큼(기본 15초) 결과가 이미 나왔는데 서버가 모르는 구간이 생기고,
     * 중계를 보는 사람은 그 사이에 결과를 알고 걸 수 있다. 타석 평균이 2분이므로
     * 초반 30초로 제한하면 그런 배팅이 원천적으로 불가능해진다.
     */
    public boolean isOpen(Instant now, Duration window) {
        return startedAt != null && !now.isAfter(startedAt.plus(window));
    }

    public boolean identifiable() {
        return inning != null && half != null && batter != null && !batter.isBlank();
    }
}
