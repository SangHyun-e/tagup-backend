package com.tagup.backend.game.service;

import com.tagup.backend.common.exception.CustomException;
import com.tagup.backend.common.exception.ErrorCode;
import com.tagup.backend.game.dto.GameResponse;
import com.tagup.backend.game.entity.Game;
import com.tagup.backend.game.entity.GameStatus;
import com.tagup.backend.game.repository.GameRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class GameService {

    private final GameRepository gameRepository;

    @Transactional(readOnly = true)
    public List<GameResponse> getGamesByDate(LocalDate date) {
        return gameRepository.findByGameDateWithTeams(date).stream()
                .map(GameResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<GameResponse> getTodayGames() {
        return getGamesByDate(LocalDate.now());
    }

    /** 오늘 이후 가장 가까운 예정(SCHEDULED) 경기일의 경기 목록. 올스타 브레이크 등 휴식기 대응 */
    @Transactional(readOnly = true)
    public List<GameResponse> getUpcomingGames() {
        LocalDate today = LocalDate.now();
        List<Game> games = gameRepository.findByStatusBetweenWithTeams(GameStatus.SCHEDULED, today, today.plusDays(21));
        if (games.isEmpty()) return List.of();

        LocalDate firstDate = games.get(0).getGameDate();
        return games.stream()
                .filter(g -> g.getGameDate().equals(firstDate))
                .map(GameResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public GameResponse getGame(Long gameId) {
        Game game = gameRepository.findById(gameId)
                .orElseThrow(() -> new CustomException(ErrorCode.GAME_NOT_FOUND));
        return GameResponse.from(game);
    }
}
