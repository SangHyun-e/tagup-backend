package com.tagup.backend.game.repository;

import com.tagup.backend.bet.entity.BetStatus;
import com.tagup.backend.bet.entity.Bet;
import com.tagup.backend.room.entity.Room;
import com.tagup.backend.user.entity.User;
import com.tagup.backend.game.entity.Game;
import com.tagup.backend.game.entity.GameStatus;
import com.tagup.backend.team.entity.Team;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 정산 스케줄러가 읽는 경기에 팀이 함께 딸려오는지 검증한다.
 *
 * <p><b>2026-09-09 실경기 회귀.</b> 스케줄러가 fetch join 없는 쿼리로 경기를 읽어
 * {@code settleByGame}에 넘겼고, 그 안에서 팀 이름에 접근하는 순간
 * {@code Could not initialize proxy - no session}이 터졌다. 예외가 트랜잭션 밖으로
 * 나가면서 이미 확정된 정산이 롤백됐고, 그날 배팅은 하나도 정산되지 않았다.
 *
 * <p>단위 테스트는 목(mock)을 쓰므로 lazy 프록시가 없어 이 버그를 절대 잡지 못한다.
 * 실제 영속성 컨텍스트가 필요하다.
 */
@DataJpaTest
class GameRepositoryFetchTest {

    @Autowired private GameRepository gameRepository;
    @Autowired private EntityManager em;

    private static final LocalDate DATE = LocalDate.of(2026, 9, 9);

    @BeforeEach
    void setUp() {
        Team away = em.merge(Team.builder().name("NC 다이노스").shortName("NC").build());
        Team home = em.merge(Team.builder().name("KIA 타이거즈").shortName("KIA").build());

        Game game = Game.builder()
                .kboGameId("20260909_NC_KIA")
                .gameDate(DATE).gameTime(LocalTime.of(18, 30))
                .awayTeam(away).homeTeam(home)
                .status(GameStatus.FINISHED)
                .awayScore(5).homeScore(4)
                .stadium("광주")
                .build();
        em.persist(game);

        // 쿼리가 "미정산 배팅이 남은" 경기만 고르므로, 성립된 배팅이 있어야 한다
        User proposer = User.builder()
                .firebaseUid("uid-p").email("p@test").nickname("제안자").build();
        User receiver = User.builder()
                .firebaseUid("uid-r").email("r@test").nickname("수락자").build();
        em.persist(proposer);
        em.persist(receiver);

        Room room = Room.builder().tagCode("ABC123").name("더그아웃").createdBy(proposer).build();
        em.persist(room);

        Bet bet = Bet.builder()
                .proposer(proposer).room(room).game(game)
                .content("커피 한 잔").betOnTeamId(home.getId())
                .build();
        bet.accept(receiver);
        em.persist(bet);

        em.flush();
        em.clear();
    }

    @Test
    void 미정산_배팅이_없는_경기는_정산_대상에서_빠진다() {
        // 이미 정산된 배팅만 남으면 다시 정산하지 않는다
        em.createQuery("UPDATE Bet b SET b.status = :finished")
                .setParameter("finished", BetStatus.FINISHED)
                .executeUpdate();
        em.flush();
        em.clear();

        assertThat(gameRepository.findFinishedGamesWithOpenBets(
                List.of(BetStatus.PENDING, BetStatus.ACCEPTED))).isEmpty();
    }

    @Test
    void fetch_join_쿼리로_읽으면_영속성_컨텍스트_밖에서도_팀_이름을_읽을_수_있다() {
        Game game = gameRepository
                .findFinishedGamesWithOpenBets(List.of(BetStatus.PENDING, BetStatus.ACCEPTED))
                .get(0);

        em.detach(game);   // 조회 트랜잭션이 끝난 상황을 재현

        assertThat(game.getAwayTeam().getShortName()).isEqualTo("NC");
        assertThat(game.getHomeTeam().getShortName()).isEqualTo("KIA");
    }

    /**
     * 프록시라도 ID는 읽히기 때문에 승패 계산({@code calcResult})까지는 멀쩡히 통과했고,
     * {@code bet.settle()}도 실행된 뒤 <b>알림 단계에서야</b> 터졌다. 그래서 발견이 늦었다.
     */
    @Test
    void 프록시라도_ID는_읽을_수_있다_그래서_승패_계산까지는_통과했었다() {
        Game game = gameRepository
                .findByGameDateAndStatusIn(DATE, List.of(GameStatus.FINISHED))
                .get(0);
        em.detach(game);

        assertThat(game.getHomeTeam().getId()).isNotNull();
        assertThat(game.getHomeScore()).isEqualTo(4);
        assertThat(game.getAwayScore()).isEqualTo(5);
    }
}
