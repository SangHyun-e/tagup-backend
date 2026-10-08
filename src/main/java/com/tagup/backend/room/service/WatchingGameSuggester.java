package com.tagup.backend.room.service;

import com.tagup.backend.game.entity.Game;
import com.tagup.backend.team.entity.Team;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 더그아웃 멤버들의 응원팀을 보고 <b>오늘 같이 볼 경기</b>를 고른다.
 *
 * <p>LG팬과 두산팬이 있는 방이라면 LG-두산 경기가 명백한 답이다. 한 명의 팀만 걸린
 * 경기보다 두 명이 걸린 경기를 우선한다.
 *
 * <p><b>순수 함수다.</b> 애매하면 고르지 않는다 — 엉뚱한 경기를 자동 지정해두면
 * 사용자가 잘못 배팅할 수 있어서, 근거가 없을 때는 비워두고 직접 고르게 하는 편이 낫다.
 */
public class WatchingGameSuggester {

    /**
     * @param favoriteTeams 멤버들의 응원팀 (null 항목·중복 허용)
     * @param todayGames    오늘 경기 목록
     * @return 걸리는 팀이 가장 많은 경기. 하나도 안 걸리면 {@link Optional#empty()}
     */
    public Optional<Game> suggest(List<Team> favoriteTeams, List<Game> todayGames) {
        if (favoriteTeams == null || todayGames == null) return Optional.empty();

        Set<Long> teamIds = favoriteTeams.stream()
                .filter(t -> t != null && t.getId() != null)
                .map(Team::getId)
                .collect(Collectors.toSet());
        if (teamIds.isEmpty()) return Optional.empty();

        return todayGames.stream()
                .filter(g -> matchCount(g, teamIds) > 0)
                // 걸리는 팀이 많은 순 → 같으면 경기 시작이 이른 순 (동점일 때도 결과가 흔들리지 않게)
                .max(Comparator
                        .comparingInt((Game g) -> matchCount(g, teamIds))
                        .thenComparing(Game::getGameTime, Comparator.nullsLast(Comparator.reverseOrder())));
    }

    private int matchCount(Game game, Set<Long> teamIds) {
        int n = 0;
        if (game.getHomeTeam() != null && teamIds.contains(game.getHomeTeam().getId())) n++;
        if (game.getAwayTeam() != null && teamIds.contains(game.getAwayTeam().getId())) n++;
        return n;
    }
}
