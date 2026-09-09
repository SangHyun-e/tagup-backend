package com.tagup.backend.bet.service;

import com.tagup.backend.bet.dto.BetResponse;
import com.tagup.backend.bet.dto.CreateBetRequest;
import com.tagup.backend.bet.entity.Bet;
import com.tagup.backend.bet.entity.BetResult;
import com.tagup.backend.bet.entity.BetStatus;
import com.tagup.backend.bet.repository.BetRepository;
import com.tagup.backend.common.exception.CustomException;
import com.tagup.backend.notification.service.PushSender;
import com.tagup.backend.common.exception.ErrorCode;
import com.tagup.backend.game.entity.Game;
import com.tagup.backend.game.entity.GameStatus;
import com.tagup.backend.game.repository.GameRepository;
import com.tagup.backend.room.entity.Room;
import com.tagup.backend.room.repository.RoomMemberRepository;
import com.tagup.backend.room.repository.RoomRepository;
import com.tagup.backend.user.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class BetService {

    private final BetRepository betRepository;
    private final BetChatAnnouncer betChatAnnouncer;
    private final PushSender pushSender;
    private final RoomRepository roomRepository;
    private final RoomMemberRepository roomMemberRepository;
    private final GameRepository gameRepository;

    @Transactional
    public BetResponse createBet(Long roomId, CreateBetRequest request, User proposer) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new CustomException(ErrorCode.ROOM_NOT_FOUND));

        validateRoomMember(room, proposer);

        Game game = resolveGame(request, room);

        if (game.getStatus() != GameStatus.SCHEDULED) {
            throw new CustomException(ErrorCode.GAME_ALREADY_STARTED);
        }

        Long homeTeamId = game.getHomeTeam().getId();
        Long awayTeamId = game.getAwayTeam().getId();
        if (!request.betOnTeamId().equals(homeTeamId) && !request.betOnTeamId().equals(awayTeamId)) {
            throw new CustomException(ErrorCode.INVALID_BET_TEAM);
        }

        Bet bet = Bet.builder()
                .proposer(proposer)
                .room(room)
                .game(game)
                .content(request.content())
                .betOnTeamId(request.betOnTeamId())
                .build();

        Bet saved = betRepository.save(bet);

        // 제안자를 뺀 방 멤버에게 "받을 사람 콜!" 알림
        List<User> others = roomMemberRepository.findAllByRoomWithUser(room).stream()
                .map(rm -> rm.getUser())
                .filter(u -> !u.getId().equals(proposer.getId()))
                .toList();
        pushSender.sendToUsers(others, "⚾ 새 배팅이 올라왔어요",
                String.format("%s님: \"%s\" · %s 승리에 배팅 — 받을 사람 콜!",
                        proposer.getNickname(), saved.getContent(),
                        BetResponse.from(saved).betOnTeam().shortName()),
                Map.of("type", "BET_CREATED", "roomId", String.valueOf(room.getId()),
                        "betId", String.valueOf(saved.getId())));

        return BetResponse.from(saved);
    }

    /** 콜! — 방 멤버 누구나 가능 (선착 1명), 제안자의 반대편에 배팅 */
    @Transactional
    public BetResponse acceptBet(Long betId, User user) {
        Bet bet = getBetOrThrow(betId);

        validateRoomMember(bet.getRoom(), user);

        if (bet.getProposer().getId().equals(user.getId())) {
            throw new CustomException(ErrorCode.CANNOT_BET_SELF);
        }
        if (bet.getStatus() != BetStatus.PENDING) {
            throw new CustomException(ErrorCode.BET_NOT_PENDING);
        }

        bet.accept(user);

        pushSender.sendToUsers(List.of(bet.getProposer()), "📣 배팅 성립!",
                String.format("%s님이 콜했어요 — \"%s\"", user.getNickname(), bet.getContent()),
                Map.of("type", "BET_ACCEPTED", "roomId", String.valueOf(bet.getRoom().getId()),
                        "betId", String.valueOf(bet.getId())));

        return BetResponse.from(bet);
    }

    @Transactional
    public BetResponse cancelBet(Long betId, User user) {
        Bet bet = getBetOrThrow(betId);

        // PENDING 상태에는 receiver가 없으므로 제안자만 취소 가능
        if (!bet.getProposer().getId().equals(user.getId())) {
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
            notifySettlement(bet, game, awayScore, homeScore);
            settled++;
        }

        log.info("[정산] 경기 {} 내기 정산 {}건, 미수락 만료 {}건", game.getKboGameId(), settled, cancelled);
    }

    /**
     * 배팅 대상 경기를 정한다.
     *
     * <p>{@code gameId}를 주면 그 경기, 생략하면 <b>더그아웃이 오늘 보고 있는 경기</b>를 쓴다.
     * 방마다 관전 경기가 정해져 있으므로 매번 경기를 고를 필요가 없다.
     * 둘 다 없으면 무엇에 거는지 알 수 없으므로 거절한다.
     */
    private Game resolveGame(CreateBetRequest request, Room room) {
        if (request.gameId() != null) {
            return gameRepository.findById(request.gameId())
                    .orElseThrow(() -> new CustomException(ErrorCode.GAME_NOT_FOUND));
        }
        if (room.getWatchingGame() == null) {
            throw new CustomException(ErrorCode.NO_WATCHING_GAME);
        }
        return room.getWatchingGame();
    }

    /** 정산 결과를 양측에 푸시 (승자 관점 문구) */
    private void notifySettlement(Bet bet, Game game, int awayScore, int homeScore) {
        String score = String.format("%s %d : %d %s",
                game.getAwayTeam().getShortName(), awayScore,
                homeScore, game.getHomeTeam().getShortName());
        String body = bet.getProposerResult() == BetResult.DRAW
                ? String.format("🤝 무승부! \"%s\" 내기는 무효예요 (%s)", bet.getContent(), score)
                : String.format("🏆 %s님 승리! \"%s\" (%s)",
                        bet.getProposerResult() == BetResult.WIN
                                ? bet.getProposer().getNickname()
                                : bet.getReceiver().getNickname(),
                        bet.getContent(), score);

        pushSender.sendToUsers(List.of(bet.getProposer(), bet.getReceiver()),
                "배팅 정산 완료", body,
                Map.of("type", "BET_SETTLED", "roomId", String.valueOf(bet.getRoom().getId()),
                        "betId", String.valueOf(bet.getId())));
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
