package com.tagup.backend.game.service;

import com.tagup.backend.common.exception.CustomException;
import com.tagup.backend.common.exception.ErrorCode;
import com.tagup.backend.game.dto.GameResponse;
import com.tagup.backend.game.entity.Game;
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

    @Transactional(readOnly = true)
    public GameResponse getGame(Long gameId) {
        Game game = gameRepository.findById(gameId)
                .orElseThrow(() -> new CustomException(ErrorCode.GAME_NOT_FOUND));
        return GameResponse.from(game);
    }
}
