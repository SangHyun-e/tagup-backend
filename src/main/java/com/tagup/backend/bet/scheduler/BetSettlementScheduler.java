package com.tagup.backend.bet.scheduler;

import com.tagup.backend.bet.entity.BetStatus;
import com.tagup.backend.bet.service.BetService;
import com.tagup.backend.game.entity.Game;
import com.tagup.backend.game.repository.GameRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class BetSettlementScheduler {

    private final GameRepository gameRepository;
    private final BetService betService;

    /**
     * 경기 결과 업데이트 후 30분마다 정산 (오후 1시~자정).
     *
     * <p>주기를 설정으로 뺀 이유 — 정산은 경기가 끝나야만 도는 경로라 검증이 어렵다.
     * 실제 데이터로 확인할 때 짧은 주기로 덮어쓸 수 있어야 한다.
     */
    @Scheduled(cron = "${tagup.bet.settlement-cron:0 */30 13-23 * * *}", zone = "Asia/Seoul")
    public void settleBets() {
        // 날짜가 아니라 "미정산 배팅이 남은 종료 경기"로 찾는다.
        // 오늘 종료된 경기만 보면, 정산이 한 번 실패한 뒤 날이 바뀌는 순간 그 배팅은
        // 영영 정산되지 않는다 (2026-09-09에 실제로 그렇게 됐다).
        List<Game> finishedGames = gameRepository.findFinishedGamesWithOpenBets(
                List.of(BetStatus.PENDING, BetStatus.ACCEPTED));

        if (finishedGames.isEmpty()) return;

        log.info("[배팅 정산] 종료 경기 {}건 정산 시작", finishedGames.size());
        for (Game game : finishedGames) {
            betService.settleByGame(game);
        }
    }
}
