package com.tagup.backend.game.repository;

import com.tagup.backend.bet.entity.BetStatus;
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

    @Query("SELECT g FROM Game g JOIN FETCH g.homeTeam JOIN FETCH g.awayTeam WHERE g.gameDate = :date ORDER BY g.gameTime ASC")
    List<Game> findByGameDateWithTeams(@Param("date") LocalDate date);

    @Query("SELECT g FROM Game g JOIN FETCH g.homeTeam JOIN FETCH g.awayTeam WHERE g.gameDate BETWEEN :from AND :to ORDER BY g.gameDate ASC, g.gameTime ASC")
    List<Game> findByGameDateBetweenWithTeams(@Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("SELECT g FROM Game g JOIN FETCH g.homeTeam JOIN FETCH g.awayTeam WHERE g.status = :status AND g.gameDate BETWEEN :from AND :to ORDER BY g.gameDate ASC, g.gameTime ASC")
    List<Game> findByStatusBetweenWithTeams(@Param("status") GameStatus status, @Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("SELECT DISTINCT CASE WHEN g.homeTeam = :team THEN g.awayTeam ELSE g.homeTeam END FROM Game g WHERE g.gameDate = :date AND (g.homeTeam = :team OR g.awayTeam = :team) AND g.status != 'CANCELLED'")
    List<Team> findOpponentsByTeamAndDate(@Param("team") Team team, @Param("date") LocalDate date);

    @Query("SELECT g FROM Game g WHERE g.gameDate = :date AND g.status IN :statuses")
    List<Game> findByGameDateAndStatusIn(@Param("date") LocalDate date, @Param("statuses") List<GameStatus> statuses);

    /**
     * <b>정산이 남아 있는 종료 경기</b>를 팀까지 함께 가져온다. 정산 스케줄러 전용.
     *
     * <p>날짜로 찾지 않는 이유 — 2026-09-09에 정산이 예외로 롤백됐는데, 스케줄러가
     * "오늘 종료된 경기"만 보다 보니 날이 바뀐 순간 그 배팅은 영영 정산되지 않는 상태가 됐다.
     * 경기 종료 시각에 서버가 꺼져 있어도 같은 일이 난다. 미정산 배팅이 남아 있는 한
     * 계속 다시 시도하도록 <b>배팅 상태를 기준으로</b> 찾는다.
     *
     * <p>팀을 함께 읽는 이유 — 정산은 채팅 공지·푸시에 팀 이름을 쓴다. lazy 프록시로 넘기면
     * 조회 트랜잭션이 이미 닫혀 있어 {@code no session}으로 터진다.
     */
    @Query("SELECT DISTINCT g FROM Game g JOIN FETCH g.homeTeam JOIN FETCH g.awayTeam "
            + "WHERE g.status = com.tagup.backend.game.entity.GameStatus.FINISHED "
            + "AND EXISTS (SELECT 1 FROM Bet b WHERE b.game = g AND b.status IN :openStatuses)")
    List<Game> findFinishedGamesWithOpenBets(@Param("openStatuses") List<BetStatus> openStatuses);

    @Query("SELECT DISTINCT g.homeTeam FROM Game g WHERE g.gameDate = :date AND g.status != 'CANCELLED'")
    List<Team> findHomeTeamsByDate(@Param("date") LocalDate date);

    @Query("SELECT DISTINCT g.awayTeam FROM Game g WHERE g.gameDate = :date AND g.status != 'CANCELLED'")
    List<Team> findAwayTeamsByDate(@Param("date") LocalDate date);
}
