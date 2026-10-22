package com.tagup.backend.game.live;

/**
 * 새 타석이 시작됐음을 알리는 도메인 이벤트.
 *
 * <p>중계 문구와 <b>타석 배팅 창</b>의 기준점이 된다. 실제 타석 시작보다 최대 폴링 간격만큼 늦다.
 *
 * @param pitcherChanged 직전 타석과 투수가 달라졌는지 (교체 안내용)
 */
public record AtBatStartedEvent(
        Long gameId,
        String kboGameId,
        Integer inning,
        HalfInning half,
        String batter,
        String pitcher,
        boolean pitcherChanged
) {}
