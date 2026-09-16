package com.tagup.backend.bet.service;

import com.tagup.backend.bet.entity.Bet;
import com.tagup.backend.bet.repository.BetRepository;
import com.tagup.backend.game.entity.Game;
import com.tagup.backend.game.live.AtBatDetectedEvent;
import com.tagup.backend.game.live.AtBatResult;
import com.tagup.backend.game.live.AtBatStartedEvent;
import com.tagup.backend.game.repository.GameRepository;
import com.tagup.backend.room.entity.Room;
import com.tagup.backend.room.repository.RoomRepository;
import com.tagup.backend.user.entity.User;
import com.tagup.backend.user.repository.UserRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * <b>검증 전용.</b> 타석이 시작될 때 자동으로 타석 배팅을 만들고 즉시 성립시킨다.
 *
 * <p>왜 필요한가 — 타석 배팅 정산은 실경기에서만 도는 경로인데, 검증하려면 매일 밤 누군가
 * 앱에서 30초 안에 배팅을 걸고 다른 계정으로 콜까지 해야 한다. 2026-09-13과 09-15에
 * 그걸 못 해서 하루씩 날렸다. 정규시즌은 10/7에 끝나 남은 기회가 유한하다.
 * 사람 손 없이도 검증 데이터가 쌓이게 한다.
 *
 * <p><b>기본 비활성이고 프로덕션에서 켜면 안 된다.</b> 실제 유저의 배팅 목록에
 * 가짜 배팅이 섞인다. 내용에 프리픽스를 붙여 눈으로도 구분되게 했다.
 *
 * <p>30초 창·방 멤버 검사 같은 사용자 경로의 규칙은 일부러 우회한다. 여기서 검증하려는 건
 * <b>타석이 끝났을 때 정산이 제대로 도는가</b>이지 생성 규칙이 아니다(그건 단위 테스트가 본다).
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "tagup.live.auto-test-bet.enabled", havingValue = "true")
public class AutoTestBetCreator {

    private static final String CONTENT_PREFIX = "[자동검증]";

    private final BetRepository betRepository;
    private final RoomRepository roomRepository;
    private final UserRepository userRepository;
    private final GameRepository gameRepository;

    @Value("${tagup.live.auto-test-bet.room-id}")
    private Long roomId;

    @Value("${tagup.live.auto-test-bet.proposer-id}")
    private Long proposerId;

    @Value("${tagup.live.auto-test-bet.receiver-id}")
    private Long receiverId;

    /** 매 타석마다 걸면 경기당 80건이 넘는다. 표본은 그보다 적어도 충분하다 */
    @Value("${tagup.live.auto-test-bet.every-n-at-bats:10}")
    private int everyN;

    private final AtomicInteger seen = new AtomicInteger();

    @PostConstruct
    void warn() {
        log.warn("[자동검증] 타석 배팅 자동 생성이 켜져 있습니다 — 검증 전용입니다. "
                + "방 {}, 제안자 {}, 수락자 {}, {}타석마다 1건", roomId, proposerId, receiverId, everyN);
    }

    @EventListener
    @Transactional
    public void onAtBatStarted(AtBatStartedEvent event) {
        try {
            if (seen.incrementAndGet() % Math.max(everyN, 1) != 0) return;
            create(event);
        } catch (Exception e) {
            // 폴러 스레드에서 동기 호출된다 — 검증용 코드가 실제 감지를 멈추면 본말전도다
            log.warn("[자동검증] 배팅 생성 실패 {} {}: {}",
                    event.kboGameId(), event.batter(), e.toString());
        }
    }

    private void create(AtBatStartedEvent event) {
        Game game = gameRepository.findById(event.gameId()).orElse(null);
        Room room = roomRepository.findById(roomId).orElse(null);
        User proposer = userRepository.findById(proposerId).orElse(null);
        User receiver = userRepository.findById(receiverId).orElse(null);
        if (game == null || room == null || proposer == null || receiver == null) {
            log.warn("[자동검증] 방/계정/경기를 찾지 못해 건너뜁니다 (room={}, proposer={}, receiver={})",
                    roomId, proposerId, receiverId);
            return;
        }

        // 아웃/세이프를 번갈아 걸어 양쪽 결과가 모두 검증되게 한다
        AtBatResult side = seen.get() % (everyN * 2) == 0 ? AtBatResult.SAFE : AtBatResult.OUT;

        Bet bet = Bet.forAtBat(proposer, room, game,
                CONTENT_PREFIX + " " + side + " 배팅", side,
                event.inning(), event.half(), event.batter());
        bet.accept(receiver);          // 즉시 성립 — 콜을 기다릴 사람이 없다
        Bet saved = betRepository.save(bet);

        log.info("[자동검증] 배팅 생성 #{} — {} {}회{} {} 타석에 {} (성립)",
                saved.getId(), event.kboGameId(), event.inning(),
                event.half() == null ? "" : event.half().korean(), event.batter(), side);
    }

    /** 정산이 실제로 붙었는지 아침에 한눈에 보려고 남긴다 */
    @EventListener
    public void onAtBatFinished(AtBatDetectedEvent event) {
        log.debug("[자동검증] 타석 종료 {} {} → {}",
                event.kboGameId(), event.atBat().batter(), event.atBat().result());
    }
}
