package com.tagup.backend.bet.repository;

import com.tagup.backend.bet.entity.Bet;
import com.tagup.backend.bet.entity.BetStatus;
import com.tagup.backend.bet.entity.BetType;
import com.tagup.backend.game.entity.Game;
import com.tagup.backend.room.entity.Room;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface BetRepository extends JpaRepository<Bet, Long> {

    @Query("SELECT b FROM Bet b JOIN FETCH b.proposer LEFT JOIN FETCH b.receiver JOIN FETCH b.game g JOIN FETCH g.homeTeam JOIN FETCH g.awayTeam WHERE b.room = :room ORDER BY b.createdAt DESC")
    List<Bet> findAllByRoomWithDetails(@Param("room") Room room);

    List<Bet> findByGameAndStatusIn(Game game, List<BetStatus> statuses);

    /**
     * 특정 경기의 미정산 타석 배팅. 정산에 필요한 연관을 함께 읽는다.
     *
     * <p>{@code type}을 JPQL 문자열 리터럴로 비교하면 빈 결과가 나오므로 반드시 파라미터로 바인딩한다.
     * (2026-07 같은 원인으로 배팅 목록이 비어 보이는 버그가 있었다)
     */
    @Query("SELECT b FROM Bet b "
            + "JOIN FETCH b.proposer LEFT JOIN FETCH b.receiver "
            + "JOIN FETCH b.room "
            + "JOIN FETCH b.game g JOIN FETCH g.homeTeam JOIN FETCH g.awayTeam "
            + "WHERE g.id = :gameId AND b.type = :type AND b.status IN :statuses")
    List<Bet> findAtBatBetsForSettlement(@Param("gameId") Long gameId,
                                          @Param("type") BetType type,
                                          @Param("statuses") List<BetStatus> statuses);
}
