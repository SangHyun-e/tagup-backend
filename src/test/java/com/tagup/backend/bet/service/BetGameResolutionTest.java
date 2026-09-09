package com.tagup.backend.bet.service;

import com.tagup.backend.bet.dto.CreateBetRequest;
import com.tagup.backend.bet.entity.Bet;
import com.tagup.backend.bet.repository.BetRepository;
import com.tagup.backend.common.exception.CustomException;
import com.tagup.backend.common.exception.ErrorCode;
import com.tagup.backend.game.entity.Game;
import com.tagup.backend.game.entity.GameStatus;
import com.tagup.backend.game.repository.GameRepository;
import com.tagup.backend.notification.service.PushSender;
import com.tagup.backend.room.entity.Room;
import com.tagup.backend.room.repository.RoomMemberRepository;
import com.tagup.backend.room.repository.RoomRepository;
import com.tagup.backend.team.entity.Team;
import com.tagup.backend.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 배팅을 만들 때 어느 경기에 거는지 정하는 규칙.
 *
 * <p>더그아웃마다 오늘 보는 경기가 정해져 있으므로 매번 경기를 고르지 않아도 된다.
 * 다만 아무 근거 없이 임의의 경기로 떨어지면 사용자가 엉뚱한 경기에 걸게 되므로,
 * 지정도 없고 방의 관전 경기도 없으면 거절한다.
 */
class BetGameResolutionTest {

    private BetRepository betRepository;
    private RoomRepository roomRepository;
    private RoomMemberRepository roomMemberRepository;
    private GameRepository gameRepository;
    private BetService betService;

    private Team lg, doosan, kia, ssg;
    private User proposer;
    private Room room;
    private Game watching;   // 방이 보고 있는 경기 (LG vs 두산)
    private Game other;      // 다른 경기 (SSG vs KIA)

    @BeforeEach
    void setUp() {
        betRepository = mock(BetRepository.class);
        roomRepository = mock(RoomRepository.class);
        roomMemberRepository = mock(RoomMemberRepository.class);
        gameRepository = mock(GameRepository.class);

        betService = new BetService(betRepository, mock(BetChatAnnouncer.class),
                mock(PushSender.class), roomRepository, roomMemberRepository, gameRepository);

        lg = team(3L, "LG");
        doosan = team(4L, "두산");
        ssg = team(6L, "SSG");
        kia = team(1L, "KIA");

        watching = game(101L, lg, doosan);
        other = game(102L, ssg, kia);

        proposer = user(1L, "제안자");
        room = withId(Room.builder().tagCode("ABC123").name("더그아웃").createdBy(proposer).build(), 10L);

        when(roomRepository.findById(10L)).thenReturn(Optional.of(room));
        when(roomMemberRepository.existsByRoomAndUser(room, proposer)).thenReturn(true);
        when(roomMemberRepository.findAllByRoomWithUser(room)).thenReturn(List.of());
        when(betRepository.save(any(Bet.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void 경기를_지정하지_않으면_더그아웃이_보고_있는_경기에_건다() {
        room.setWatchingGame(watching);

        betService.createBet(10L, new CreateBetRequest(null, "커피 한 잔", lg.getId()), proposer);

        assertThat(savedBet().getGame()).isEqualTo(watching);
        verify(gameRepository, never()).findById(any());
    }

    @Test
    void 경기를_지정하면_방의_관전_경기보다_우선한다() {
        room.setWatchingGame(watching);
        when(gameRepository.findById(102L)).thenReturn(Optional.of(other));

        betService.createBet(10L, new CreateBetRequest(102L, "점심", ssg.getId()), proposer);

        assertThat(savedBet().getGame())
                .as("명시적으로 고른 경기가 방의 기본값을 덮어쓴다")
                .isEqualTo(other);
    }

    @Test
    void 지정도_없고_관전_경기도_없으면_거절한다() {
        room.setWatchingGame(null);

        assertThatThrownBy(() ->
                betService.createBet(10L, new CreateBetRequest(null, "커피", lg.getId()), proposer))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.NO_WATCHING_GAME);

        verify(betRepository, never()).save(any());
    }

    @Test
    void 관전_경기에_없는_팀에는_걸_수_없다() {
        room.setWatchingGame(watching);   // LG vs 두산

        assertThatThrownBy(() ->
                betService.createBet(10L, new CreateBetRequest(null, "커피", kia.getId()), proposer))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_BET_TEAM);
    }

    @Test
    void 이미_시작된_관전_경기에는_걸_수_없다() {
        watching.updateResult(GameStatus.IN_PROGRESS, null, null, 3);
        room.setWatchingGame(watching);

        assertThatThrownBy(() ->
                betService.createBet(10L, new CreateBetRequest(null, "커피", lg.getId()), proposer))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.GAME_ALREADY_STARTED);
    }

    // ------------------------------------------------------------------ 헬퍼

    private Bet savedBet() {
        var captor = org.mockito.ArgumentCaptor.forClass(Bet.class);
        verify(betRepository).save(captor.capture());
        return captor.getValue();
    }

    private Team team(Long id, String shortName) {
        return withId(Team.builder().name(shortName).shortName(shortName).build(), id);
    }

    private User user(Long id, String nickname) {
        return withId(User.builder().firebaseUid("uid" + id).email(id + "@t").nickname(nickname).build(), id);
    }

    private Game game(Long id, Team away, Team home) {
        return withId(Game.builder()
                .kboGameId("20260909_" + away.getShortName() + "_" + home.getShortName())
                .gameDate(LocalDate.of(2026, 9, 9)).gameTime(LocalTime.of(18, 30))
                .awayTeam(away).homeTeam(home)
                .status(GameStatus.SCHEDULED)
                .build(), id);
    }

    private <T> T withId(T entity, Long id) {
        ReflectionTestUtils.setField(entity, "id", id);
        return entity;
    }
}
