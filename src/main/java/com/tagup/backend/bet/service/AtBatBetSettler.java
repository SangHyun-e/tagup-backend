package com.tagup.backend.bet.service;

import com.tagup.backend.bet.entity.Bet;
import com.tagup.backend.bet.entity.BetResult;
import com.tagup.backend.bet.entity.BetStatus;
import com.tagup.backend.bet.entity.BetType;
import com.tagup.backend.bet.repository.BetRepository;
import com.tagup.backend.game.live.AtBatDetectedEvent;
import com.tagup.backend.game.live.AtBatEvent;
import com.tagup.backend.game.live.AtBatResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 타석이 끝나는 순간 그 타석에 걸린 배팅을 정산한다.
 *
 * <p>승패 배팅과 달리 경기 종료를 기다리지 않는다. 타석 하나가 끝나면 즉시 승패가 갈린다.
 *
 * <p><b>판정 불가(UNKNOWN)는 무효 처리한다.</b> KBO 응답만으로는 약 6%의 타석에서 결과를
 * 역산할 수 없다(폴링 간격 안에 여러 타석이 지나가는 등). 억지로 한쪽으로 몰면 틀린 정산이
 * 되므로, 승패 배팅의 무승부와 같은 {@link BetResult#DRAW}로 처리한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AtBatBetSettler {

    private final BetRepository betRepository;
    private final BetChatAnnouncer betChatAnnouncer;

    @EventListener
    @Transactional
    public void onAtBatDetected(AtBatDetectedEvent event) {
        try {
            settle(event);
        } catch (Exception e) {
            // 폴러 스레드에서 동기 호출되므로, 여기서 터지면 그 주기의 나머지 경기 감지가 죽는다
            log.error("[타석 정산] 실패 {} {}: {}",
                    event.kboGameId(), event.atBat().batter(), e.toString(), e);
        }
    }

    private void settle(AtBatDetectedEvent event) {
        AtBatEvent atBat = event.atBat();

        List<Bet> candidates = betRepository.findAtBatBetsForSettlement(
                event.gameId(), BetType.AT_BAT,
                List.of(BetStatus.PENDING, BetStatus.ACCEPTED));
        if (candidates.isEmpty()) return;

        int settled = 0, expired = 0;
        for (Bet bet : candidates) {
            if (!bet.matchesAtBat(atBat.inning(), atBat.half(), atBat.batter())) continue;

            if (bet.getStatus() == BetStatus.PENDING) {
                // 타석이 끝날 때까지 아무도 콜하지 않았다 — 성립하지 않았으므로 만료
                bet.cancel();
                expired++;
                continue;
            }

            bet.settle(resultFor(bet, atBat.result()));
            settled++;
            announceQuietly(bet, event);
        }

        if (settled > 0 || expired > 0) {
            log.info("[타석 정산] {} {}회{} {} → {} | 정산 {}건, 미수락 만료 {}건",
                    event.kboGameId(), atBat.inning(),
                    atBat.half() == null ? "" : atBat.half().korean(),
                    atBat.batter(), atBat.result(), settled, expired);
        }
    }

    /**
     * 제안자 관점의 승패.
     *
     * <p>콜한 사람은 자동으로 반대편에 서므로, 제안자가 맞으면 WIN·틀리면 LOSE다.
     * 판정 불가는 양쪽 누구의 잘못도 아니므로 무효(DRAW).
     */
    private BetResult resultFor(Bet bet, AtBatResult actual) {
        if (actual == null || actual == AtBatResult.UNKNOWN) return BetResult.DRAW;
        return bet.getBetOnAtBatResult() == actual ? BetResult.WIN : BetResult.LOSE;
    }

    /** 알림 실패가 정산을 되돌리면 안 된다 (2026-09-09 사고의 교훈) */
    private void announceQuietly(Bet bet, AtBatDetectedEvent event) {
        try {
            betChatAnnouncer.announceSettlement(bet, bet.getGame());
        } catch (Exception e) {
            log.warn("[타석 정산] 채팅 공지 실패 betId={} (정산은 유지): {}", bet.getId(), e.toString());
        }
    }
}
