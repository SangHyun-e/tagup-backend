package com.tagup.backend.bet.scheduler;

import com.tagup.backend.bet.entity.BetStatus;
import com.tagup.backend.bet.service.BetService;
import com.tagup.backend.game.entity.Game;
import com.tagup.backend.game.entity.GameStatus;
import com.tagup.backend.game.repository.GameRepository;
import com.tagup.backend.team.entity.Team;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

/**
 * 정산 스케줄러가 <b>팀까지 함께 읽는 쿼리</b>를 쓰는지 고정한다.
 *
 * <p>2026-09-09 실경기 회귀. fetch join 없는 쿼리로 읽은 경기를 정산에 넘기면 팀 이름에
 * 접근하는 순간 {@code LazyInitializationException}이 나고, 그게 트랜잭션 밖으로 나가면서
 * 이미 확정된 정산까지 롤백된다. 그날 배팅은 하나도 정산되지 않았다.
 *
 * <p>어느 쿼리를 쓰는지는 반환값이 아니라 <b>호출 자체</b>로만 드러나므로 이렇게 검증한다.
 */
class BetSettlementSchedulerTest {

    private final GameRepository gameRepository = mock(GameRepository.class);
    private final BetService betService = mock(BetService.class);
    private final BetSettlementScheduler scheduler =
            new BetSettlementScheduler(gameRepository, betService);

    @Test
    void 미정산_배팅이_남은_종료_경기를_팀까지_함께_읽는다() {
        when(gameRepository.findFinishedGamesWithOpenBets(anyList()))
                .thenReturn(List.of(finishedGame()));

        scheduler.settleBets();

        // 날짜 기준으로 찾으면 날이 바뀐 뒤 정산이 영영 안 된다
        verify(gameRepository, never()).findByGameDateAndStatusIn(any(), anyList());
        verify(gameRepository).findFinishedGamesWithOpenBets(
                List.of(BetStatus.PENDING, BetStatus.ACCEPTED));
    }

    @Test
    void 종료_경기마다_정산을_호출한다() {
        Game game = finishedGame();
        when(gameRepository.findFinishedGamesWithOpenBets(anyList())).thenReturn(List.of(game));

        scheduler.settleBets();

        verify(betService).settleByGame(game);
    }

    @Test
    void 종료된_경기가_없으면_정산을_호출하지_않는다() {
        when(gameRepository.findFinishedGamesWithOpenBets(anyList())).thenReturn(List.of());

        scheduler.settleBets();

        verifyNoInteractions(betService);
    }

    private Game finishedGame() {
        Team away = withId(Team.builder().name("NC 다이노스").shortName("NC").build(), 9L);
        Team home = withId(Team.builder().name("KIA 타이거즈").shortName("KIA").build(), 1L);
        return withId(Game.builder()
                .kboGameId("20260909_NC_KIA")
                .gameDate(LocalDate.now()).gameTime(LocalTime.of(18, 30))
                .awayTeam(away).homeTeam(home)
                .status(GameStatus.FINISHED)
                .awayScore(5).homeScore(4)
                .build(), 683L);
    }

    private <T> T withId(T entity, Long id) {
        ReflectionTestUtils.setField(entity, "id", id);
        return entity;
    }
}
