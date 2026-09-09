package com.tagup.backend.room.service;

import com.tagup.backend.game.entity.Game;
import com.tagup.backend.game.entity.GameStatus;
import com.tagup.backend.team.entity.Team;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 더그아웃의 오늘 관전 경기 자동 선정.
 *
 * <p>엉뚱한 경기를 자동 지정해두면 사용자가 잘못된 경기에 배팅하게 되므로,
 * <b>근거가 없으면 고르지 않는다</b>는 게 핵심 규칙이다.
 */
class WatchingGameSuggesterTest {

    private final WatchingGameSuggester suggester = new WatchingGameSuggester();

    private final Team lg    = team(3L, "LG");
    private final Team doosan = team(4L, "두산");
    private final Team kia   = team(1L, "KIA");
    private final Team ssg   = team(6L, "SSG");
    private final Team nc    = team(9L, "NC");

    private final Game lgVsDoosan = game(101L, lg, doosan, LocalTime.of(18, 30));
    private final Game ssgVsKia   = game(102L, ssg, kia,   LocalTime.of(18, 30));
    private final Game ncVsLg     = game(103L, nc, lg,     LocalTime.of(17, 0));

    @Test
    void 두_멤버의_응원팀이_맞붙는_경기를_고른다() {
        // LG팬 + 두산팬 → LG vs 두산이 명백한 답 (2팀 매칭)
        Optional<Game> picked = suggester.suggest(
                List.of(lg, doosan), List.of(ssgVsKia, lgVsDoosan, ncVsLg));

        assertThat(picked).contains(lgVsDoosan);
    }

    @Test
    void 한_팀만_걸려도_그_경기를_고른다() {
        Optional<Game> picked = suggester.suggest(
                List.of(kia), List.of(lgVsDoosan, ssgVsKia));

        assertThat(picked).contains(ssgVsKia);
    }

    @Test
    void 응원팀이_오늘_경기에_없으면_고르지_않는다() {
        Team samsung = team(2L, "삼성");

        assertThat(suggester.suggest(List.of(samsung), List.of(lgVsDoosan, ssgVsKia)))
                .as("근거 없이 아무 경기나 지정하면 사용자가 엉뚱한 경기에 배팅하게 된다")
                .isEmpty();
    }

    @Test
    void 응원팀_미설정_멤버는_무시한다() {
        // 팀을 안 고른 멤버가 섞여 있어도 나머지로 판단한다
        Optional<Game> picked = suggester.suggest(
                Arrays.asList(null, kia, null), List.of(lgVsDoosan, ssgVsKia));

        assertThat(picked).contains(ssgVsKia);
    }

    @Test
    void 아무도_응원팀을_고르지_않았으면_비워둔다() {
        assertThat(suggester.suggest(Arrays.asList(null, null), List.of(lgVsDoosan))).isEmpty();
        assertThat(suggester.suggest(List.of(), List.of(lgVsDoosan))).isEmpty();
    }

    @Test
    void 오늘_경기가_없으면_비워둔다() {
        assertThat(suggester.suggest(List.of(lg, doosan), List.of())).isEmpty();
    }

    /** 같은 팀 수가 걸리면 순서가 흔들리지 않아야 한다 — 매일 다른 경기가 잡히면 혼란스럽다 */
    @Test
    void 매칭_수가_같으면_같은_경기를_일관되게_고른다() {
        // LG팬만 있으면 lgVsDoosan(18:30)과 ncVsLg(17:00) 둘 다 1팀 매칭
        Optional<Game> a = suggester.suggest(List.of(lg), List.of(lgVsDoosan, ncVsLg));
        Optional<Game> b = suggester.suggest(List.of(lg), List.of(ncVsLg, lgVsDoosan));

        assertThat(a).isPresent();
        assertThat(a.map(Game::getId)).isEqualTo(b.map(Game::getId));
        assertThat(a).as("먼저 시작하는 경기를 고른다").contains(ncVsLg);
    }

    @Test
    void null_입력에도_터지지_않는다() {
        assertThat(suggester.suggest(null, List.of(lgVsDoosan))).isEmpty();
        assertThat(suggester.suggest(List.of(lg), null)).isEmpty();
    }

    // ------------------------------------------------------------------ 헬퍼

    private Team team(Long id, String shortName) {
        Team t = Team.builder().name(shortName).shortName(shortName).build();
        ReflectionTestUtils.setField(t, "id", id);
        return t;
    }

    private Game game(Long id, Team away, Team home, LocalTime time) {
        Game g = Game.builder()
                .kboGameId("20260909_" + away.getShortName() + "_" + home.getShortName())
                .gameDate(LocalDate.of(2026, 9, 9)).gameTime(time)
                .awayTeam(away).homeTeam(home)
                .status(GameStatus.SCHEDULED)
                .build();
        ReflectionTestUtils.setField(g, "id", id);
        return g;
    }
}
