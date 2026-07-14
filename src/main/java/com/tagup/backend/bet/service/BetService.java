package com.tagup.backend.bet.service;

import com.tagup.backend.bet.dto.BetResponse;
import com.tagup.backend.bet.dto.CreateBetRequest;
import com.tagup.backend.bet.entity.Bet;
import com.tagup.backend.bet.entity.BetResult;
import com.tagup.backend.bet.entity.BetStatus;
import com.tagup.backend.bet.repository.BetRepository;
import com.tagup.backend.common.exception.CustomException;
import com.tagup.backend.common.exception.ErrorCode;
import com.tagup.backend.game.entity.Game;
import com.tagup.backend.game.entity.GameStatus;
import com.tagup.backend.game.repository.GameRepository;
import com.tagup.backend.room.entity.Room;
import com.tagup.backend.room.repository.RoomMemberRepository;
import com.tagup.backend.room.repository.RoomRepository;
import com.tagup.backend.user.entity.User;
import com.tagup.backend.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class BetService {

    private final BetRepository betRepository;
    private final BetChatAnnouncer betChatAnnouncer;
    private final RoomRepository roomRepository;
    private final RoomMemberRepository roomMemberRepository;
    private final GameRepository gameRepository;
    private final UserRepository userRepository;

    @Transactional
    public BetResponse createBet(Long roomId, CreateBetRequest request, User proposer) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new CustomException(ErrorCode.ROOM_NOT_FOUND));

        validateRoomMember(room, proposer);

        Game game = gameRepository.findById(request.gameId())
                .orElseThrow(() -> new CustomException(ErrorCode.GAME_NOT_FOUND));

        if (game.getStatus() != GameStatus.SCHEDULED) {
            throw new CustomException(ErrorCode.GAME_ALREADY_STARTED);
        }

        Long homeTeamId = game.getHomeTeam().getId();
        Long awayTeamId = game.getAwayTeam().getId();
        if (!request.betOnTeamId().equals(homeTeamId) && !request.betOnTeamId().equals(awayTeamId)) {
            throw new CustomException(ErrorCode.INVALID_BET_TEAM);
        }

        if (request.receiverId().equals(proposer.getId())) {
            throw new CustomException(ErrorCode.CANNOT_BET_SELF);
        }

        User receiver = userRepository.findById(request.receiverId())
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        validateRoomMember(room, receiver);

        Bet bet = Bet.builder()
                .proposer(proposer)
                .receiver(receiver)
                .room(room)
                .game(game)
                .content(request.content())
                .betOnTeamId(request.betOnTeamId())
                .build();

        return BetResponse.from(betRepository.save(bet));
    }

    @Transactional
    public BetResponse acceptBet(Long betId, User user) {
        Bet bet = getBetOrThrow(betId);

        if (!bet.getReceiver().getId().equals(user.getId())) {
            throw new CustomException(ErrorCode.NOT_BET_PARTICIPANT);
        }
        if (bet.getStatus() != BetStatus.PENDING) {
            throw new CustomException(ErrorCode.BET_NOT_PENDING);
        }

        bet.accept();
        return BetResponse.from(bet);
    }

    @Transactional
    public BetResponse cancelBet(Long betId, User user) {
        Bet bet = getBetOrThrow(betId);

        boolean isParticipant = bet.getProposer().getId().equals(user.getId())
                || bet.getReceiver().getId().equals(user.getId());
        if (!isParticipant) {
            throw new CustomException(ErrorCode.NOT_BET_PARTICIPANT);
        }
        if (bet.getStatus() != BetStatus.PENDING) {
            throw new CustomException(ErrorCode.BET_NOT_PENDING);
        }

        bet.cancel();
        return BetResponse.from(bet);
    }

    @Transactional(readOnly = true)
    public List<BetResponse> getRoomBets(Long roomId, User user) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new CustomException(ErrorCode.ROOM_NOT_FOUND));

        validateRoomMember(room, user);

        return betRepository.findAllByRoomWithDetails(room).stream()
                .map(BetResponse::from)
                .toList();
    }

    /** 경기 종료 시 정산: 수락 안 된(PENDING) 내기는 만료 취소, ACCEPTED만 승패 기록 */
    @Transactional
    public void settleByGame(Game game) {
        if (game.getStatus() != GameStatus.FINISHED) return;

        List<Bet> bets = betRepository.findByGameAndStatusIn(
                game, List.of(BetStatus.PENDING, BetStatus.ACCEPTED));

        if (bets.isEmpty()) return;

        Integer homeScore = game.getHomeScore();
        Integer awayScore = game.getAwayScore();
        Long homeTeamId = game.getHomeTeam().getId();

        int cancelled = 0;
        int settled = 0;
        for (Bet bet : bets) {
            if (bet.getStatus() == BetStatus.PENDING) {
                bet.cancel();
                cancelled++;
                continue;
            }
            // 크롤링 결손으로 스코어가 없으면 결과 확정 불가 → 다음 정산 주기로 미룸
            if (homeScore == null || awayScore == null) {
                log.warn("[정산] 경기 {} 스코어 미수집으로 내기 {} 정산 보류", game.getKboGameId(), bet.getId());
                continue;
            }
            bet.settle(calcResult(bet.getBetOnTeamId(), homeTeamId, homeScore, awayScore));
            betChatAnnouncer.announceSettlement(bet, game);
            settled++;
        }

        log.info("[정산] 경기 {} 내기 정산 {}건, 미수락 만료 {}건", game.getKboGameId(), settled, cancelled);
    }

    private BetResult calcResult(Long betOnTeamId, Long homeTeamId,
                                  int homeScore, int awayScore) {
        boolean betOnHome = betOnTeamId.equals(homeTeamId);
        if (homeScore > awayScore) return betOnHome ? BetResult.WIN : BetResult.LOSE;
        if (awayScore > homeScore) return betOnHome ? BetResult.LOSE : BetResult.WIN;
        return BetResult.DRAW;
    }

    private void validateRoomMember(Room room, User user) {
        if (!roomMemberRepository.existsByRoomAndUser(room, user)) {
            throw new CustomException(ErrorCode.NOT_ROOM_MEMBER);
        }
    }

    private Bet getBetOrThrow(Long betId) {
        return betRepository.findById(betId)
                .orElseThrow(() -> new CustomException(ErrorCode.BET_NOT_FOUND));
    }
}
