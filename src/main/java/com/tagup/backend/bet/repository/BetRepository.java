package com.tagup.backend.bet.repository;

import com.tagup.backend.bet.entity.Bet;
import com.tagup.backend.bet.entity.BetStatus;
import com.tagup.backend.game.entity.Game;
import com.tagup.backend.room.entity.Room;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface BetRepository extends JpaRepository<Bet, Long> {

    @Query("SELECT b FROM Bet b JOIN FETCH b.proposer JOIN FETCH b.receiver JOIN FETCH b.game g JOIN FETCH g.homeTeam JOIN FETCH g.awayTeam WHERE b.room = :room ORDER BY b.createdAt DESC")
    List<Bet> findAllByRoomWithDetails(@Param("room") Room room);

    List<Bet> findByGameAndStatusIn(Game game, List<BetStatus> statuses);
}
