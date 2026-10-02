package com.tagup.backend.room.repository;

import com.tagup.backend.game.entity.Game;
import com.tagup.backend.game.entity.GameStatus;
import com.tagup.backend.room.entity.Room;
import com.tagup.backend.team.entity.Team;
import com.tagup.backend.user.entity.User;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import java.time.LocalDate;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 라이브 중계 발송 대상 조회.
 *
 * <p>여기가 틀리면 <b>다른 경기를 보는 더그아웃까지 남의 경기 중계로 덮인다.</b>
 * 한 경기에 140~200건이 흐르므로 오발송의 대가가 크다.
 */
@DataJpaTest
class RoomWatchingGameQueryTest {

    @Autowired private RoomRepository roomRepository;
    @Autowired private EntityManager em;

    private Game gameA, gameB;
    private Room watchingA1, watchingA2, watchingB, watchingNone;

    @BeforeEach
    void setUp() {
        Team nc = em.merge(Team.builder().name("NC 다이노스").shortName("NC").build());
        Team doosan = em.merge(Team.builder().name("두산 베어스").shortName("두산").build());
        Team lg = em.merge(Team.builder().name("LG 트윈스").shortName("LG").build());
        Team samsung = em.merge(Team.builder().name("삼성 라이온즈").shortName("삼성").build());

        gameA = persistGame("20260915_NC_두산", nc, doosan);
        gameB = persistGame("20260915_LG_삼성", lg, samsung);

        User owner = User.builder().firebaseUid("u1").email("u1@t").nickname("주인").build();
        em.persist(owner);

        watchingA1 = persistRoom(owner, "AAA111", gameA);
        watchingA2 = persistRoom(owner, "AAA222", gameA);
        watchingB = persistRoom(owner, "BBB111", gameB);
        watchingNone = persistRoom(owner, "CCC111", null);

        em.flush();
        em.clear();
    }

    @Test
    void 그_경기를_보는_더그아웃만_가져온다() {
        assertThat(roomRepository.findByWatchingGameId(gameA.getId()))
                .extracting(Room::getTagCode)
                .containsExactlyInAnyOrder("AAA111", "AAA222");
    }

    @Test
    void 다른_경기를_보는_방과_아무것도_안_보는_방은_빠진다() {
        var rooms = roomRepository.findByWatchingGameId(gameA.getId());

        assertThat(rooms).extracting(Room::getTagCode)
                .as("남의 경기 중계로 채팅이 덮이면 안 된다")
                .doesNotContain("BBB111", "CCC111");
    }

    @Test
    void 보는_방이_없으면_빈_목록이다() {
        Game noWatchers = persistGame("20260915_KT_한화", 
                em.merge(Team.builder().name("KT 위즈").shortName("KT").build()),
                em.merge(Team.builder().name("한화 이글스").shortName("한화").build()));
        em.flush();

        assertThat(roomRepository.findByWatchingGameId(noWatchers.getId())).isEmpty();
    }

    // ------------------------------------------------------------------ 헬퍼

    private Game persistGame(String kboGameId, Team away, Team home) {
        Game g = Game.builder()
                .kboGameId(kboGameId)
                .gameDate(LocalDate.of(2026, 9, 15)).gameTime(LocalTime.of(18, 30))
                .awayTeam(away).homeTeam(home)
                .status(GameStatus.IN_PROGRESS)
                .build();
        em.persist(g);
        return g;
    }

    private Room persistRoom(User owner, String tagCode, Game watching) {
        Room r = Room.builder().tagCode(tagCode).name("방 " + tagCode).createdBy(owner).build();
        r.setWatchingGame(watching);
        em.persist(r);
        return r;
    }
}
