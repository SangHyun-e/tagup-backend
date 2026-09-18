package com.tagup.backend.bet.service;

import com.tagup.backend.bet.dto.BetResponse;
import com.tagup.backend.bet.dto.CreateAtBatBetRequest;
import com.tagup.backend.bet.dto.CreateBetRequest;
import com.tagup.backend.bet.entity.Bet;
import com.tagup.backend.bet.entity.BetResult;
import com.tagup.backend.bet.entity.BetStatus;
import com.tagup.backend.bet.entity.BetType;
import com.tagup.backend.bet.repository.BetRepository;
import com.tagup.backend.common.exception.CustomException;
import com.tagup.backend.notification.service.PushSender;
import com.tagup.backend.common.exception.ErrorCode;
import com.tagup.backend.game.entity.Game;
import com.tagup.backend.game.live.AtBatResult;
import com.tagup.backend.game.live.CurrentAtBat;
import com.tagup.backend.game.live.CurrentAtBatRegistry;
import com.tagup.backend.game.entity.GameStatus;
import com.tagup.backend.game.repository.GameRepository;
import com.tagup.backend.room.entity.RoomMember;
import com.tagup.backend.room.entity.Room;
import com.tagup.backend.room.repository.RoomMemberRepository;
import com.tagup.backend.room.repository.RoomRepository;
import com.tagup.backend.user.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
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
    private final CurrentAtBatRegistry currentAtBats;

    /**
     * 타석 배팅을 받는 시간. 짧게 두는 이유는 시간이 부족해서가 아니라 <b>정보 비대칭</b> 때문이다 —
     * 폴링 간격만큼 결과가 이미 나왔는데 서버가 모르는 구간이 생긴다. 타석 평균은 약 2분이다.
     */
    @Value("${tagup.bet.at-bat-window-seconds:30}")
    private long atBatWindowSeconds;

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

    /**
     * 타석 배팅 — 지금 진행 중인 타석의 결과(아웃/세이프)에 건다.
     *
     * <p>경기가 끝나야 정산되는 승패 배팅과 달리, 그 타석이 끝나는 순간
     * {@code AtBatBetSettler}가 즉시 정산한다.
     */
    @Transactional
    public BetResponse createAtBatBet(Long roomId, CreateAtBatBetRequest request, User proposer) {
        if (request.betOnResult() != AtBatResult.OUT && request.betOnResult() != AtBatResult.SAFE) {
            throw new CustomException(ErrorCode.INVALID_AT_BAT_RESULT);
        }

        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new CustomException(ErrorCode.ROOM_NOT_FOUND));
        validateRoomMember(room, proposer);

        Game game = room.getWatchingGame();
        if (game == null) throw new CustomException(ErrorCode.NO_WATCHING_GAME);
        if (game.getStatus() != GameStatus.IN_PROGRESS) {
            throw new CustomException(ErrorCode.GAME_NOT_LIVE);
        }

        CurrentAtBat atBat = currentAtBats.find(game.getKboGameId())
                .orElseThrow(() -> new CustomException(ErrorCode.NO_LIVE_AT_BAT));
        if (!atBat.isOpen(Instant.now(), Duration.ofSeconds(atBatWindowSeconds))) {
            throw new CustomException(ErrorCode.AT_BAT_BETTING_CLOSED);
        }

        Bet saved = betRepository.save(Bet.forAtBat(
                proposer, room, game, request.content(), request.betOnResult(),
                atBat.inning(), atBat.half(), atBat.batter()));

        notifyAtBatBetCreated(room, proposer, saved, atBat);
        return BetResponse.from(saved);
    }

    /** 제안자를 뺀 방 멤버에게 "지금 이 타석, 받을 사람 콜!" */
    private void notifyAtBatBetCreated(Room room, User proposer, Bet bet, CurrentAtBat atBat) {
        List<User> others = roomMemberRepository.findAllByRoomWithUser(room).stream()
                .map(RoomMember::getUser)
                .filter(u -> !u.getId().equals(proposer.getId()))
                .toList();

        String side = bet.getBetOnAtBatResult() == AtBatResult.OUT ? "아웃" : "세이프";
        pushSender.sendToUsers(others, "⚾ 타석 배팅!",
                String.format("%s님: %s회%s %s 타석 — \"%s\" · %s에 배팅",
                        proposer.getNickname(), atBat.inning(),
                        atBat.half() == null ? "" : atBat.half().korean(),
                        atBat.batter(), bet.getContent(), side),
                Map.of("type", "AT_BAT_BET_CREATED", "roomId", String.valueOf(room.getId()),
                        "betId", String.valueOf(bet.getId())));
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
        int unresolvedAtBats = 0;
        for (Bet bet : bets) {
            if (bet.getStatus() == BetStatus.PENDING) {
                bet.cancel();
                cancelled++;
                continue;
            }
            // 타석 배팅은 그 타석이 끝나는 순간 AtBatBetSettler 가 정산한다.
            // 여기까지 살아 있다는 건 그 타석을 끝내 감지하지 못했다는 뜻이므로
            // (감지 실패·폴러 정지·경기 중단 등) 경기 스코어로 판정하지 않고 무효 처리한다.
            if (bet.getType() == BetType.AT_BAT) {
                bet.settle(BetResult.DRAW);
                unresolvedAtBats++;
                continue;
            }
            // 크롤링 결손으로 스코어가 없으면 결과 확정 불가 → 다음 정산 주기로 미룸
            if (homeScore == null || awayScore == null) {
                log.warn("[정산] 경기 {} 스코어 미수집으로 내기 {} 정산 보류", game.getKboGameId(), bet.getId());
                continue;
            }
            bet.settle(calcResult(bet.getBetOnTeamId(), homeTeamId, homeScore, awayScore));
            settled++;
            announceQuietly(bet, game, awayScore, homeScore);
        }

        log.info("[정산] 경기 {} 내기 정산 {}건, 미수락 만료 {}건{}",
                game.getKboGameId(), settled, cancelled,
                unresolvedAtBats > 0 ? ", 타석 미감지 무효 " + unresolvedAtBats + "건" : "");
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

    /**
     * 정산 결과를 알린다. <b>실패해도 정산은 되돌리지 않는다.</b>
     *
     * <p>2026-09-09 실경기에서 푸시 문구를 만들다 {@code LazyInitializationException}이 터졌고,
     * 그게 트랜잭션 밖으로 나가면서 <b>이미 확정된 정산이 통째로 롤백됐다.</b> 그날 배팅은
     * 하나도 정산되지 않았다.
     *
     * <p>승패는 경기 결과로 이미 결정된 사실이다. 알림이 안 갔다고 그 사실을 취소할 이유가 없다.
     * 알림 실패는 로그로 남기고 정산은 확정한다.
     */
    private void announceQuietly(Bet bet, Game game, int awayScore, int homeScore) {
        try {
            betChatAnnouncer.announceSettlement(bet, game);
        } catch (Exception e) {
            log.warn("[정산] 채팅 공지 실패 betId={} (정산은 유지): {}", bet.getId(), e.toString());
        }
        try {
            notifySettlement(bet, game, awayScore, homeScore);
        } catch (Exception e) {
            log.warn("[정산] 푸시 발송 실패 betId={} (정산은 유지): {}", bet.getId(), e.toString());
        }
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
