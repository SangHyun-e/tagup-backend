package com.tagup.backend.game.repository;

import com.tagup.backend.game.entity.Game;
import com.tagup.backend.game.entity.GameStatus;
import com.tagup.backend.team.entity.Team;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface GameRepository extends JpaRepository<Game, Long> {

    Optional<Game> findByKboGameId(String kboGameId);

    @Query("SELECT g FROM Game g JOIN FETCH g.homeTeam JOIN FETCH g.awayTeam WHERE g.gameDate = :date ORDER BY g.gameTime ASC NULLS LAST")
    List<Game> findByGameDateWithTeams(@Param("date") LocalDate date);

    @Query("SELECT g FROM Game g JOIN FETCH g.homeTeam JOIN FETCH g.awayTeam WHERE g.gameDate BETWEEN :from AND :to ORDER BY g.gameDate ASC, g.gameTime ASC NULLS LAST")
    List<Game> findByGameDateBetweenWithTeams(@Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("SELECT DISTINCT CASE WHEN g.homeTeam = :team THEN g.awayTeam ELSE g.homeTeam END FROM Game g WHERE g.gameDate = :date AND (g.homeTeam = :team OR g.awayTeam = :team) AND g.status != 'CANCELLED'")
    List<Team> findOpponentsByTeamAndDate(@Param("team") Team team, @Param("date") LocalDate date);

    @Query("SELECT g FROM Game g WHERE g.gameDate = :date AND g.status IN :statuses")
    List<Game> findByGameDateAndStatusIn(@Param("date") LocalDate date, @Param("statuses") List<GameStatus> statuses);

    @Query("SELECT DISTINCT g.homeTeam FROM Game g WHERE g.gameDate = :date AND g.status != 'CANCELLED'")
    List<Team> findHomeTeamsByDate(@Param("date") LocalDate date);

    @Query("SELECT DISTINCT g.awayTeam FROM Game g WHERE g.gameDate = :date AND g.status != 'CANCELLED'")
    List<Team> findAwayTeamsByDate(@Param("date") LocalDate date);
}
