package com.tagup.backend.bet.scheduler;

import com.tagup.backend.bet.service.BetService;
import com.tagup.backend.game.entity.Game;
import com.tagup.backend.game.entity.GameStatus;
import com.tagup.backend.game.repository.GameRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class BetSettlementScheduler {

    private final GameRepository gameRepository;
    private final BetService betService;

    // 경기 결과 업데이트 후 30분마다 정산 (오후 1시~자정)
    @Scheduled(cron = "0 */30 13-23 * * *", zone = "Asia/Seoul")
    public void settleBets() {
        List<Game> finishedGames = gameRepository.findByGameDateAndStatusIn(
                LocalDate.now(), List.of(GameStatus.FINISHED));

        if (finishedGames.isEmpty()) return;

        log.info("[배팅 정산] 종료 경기 {}건 정산 시작", finishedGames.size());
        for (Game game : finishedGames) {
            betService.settleByGame(game);
        }
    }
}
