package com.tagup.backend.game.live;

/**
 * 타석 하나가 끝났음을 알리는 도메인 이벤트.
 *
 * <p>{@code game.live}가 배팅 도메인을 직접 알지 않도록 이벤트로 분리했다.
 * 감지는 중계를 위한 것이기도 해서, 배팅이 없어도 성립하는 사실이다.
 *
 * <p><b>엔티티가 아니라 id를 담는다.</b> 리스너는 별도 트랜잭션에서 돌기 때문에,
 * 엔티티를 그대로 넘기면 lazy 프록시를 초기화하지 못한다
 * (2026-09-09 정산 롤백 사고와 같은 원인).
 */
public record AtBatDetectedEvent(Long gameId, String kboGameId, AtBatEvent atBat) {}
