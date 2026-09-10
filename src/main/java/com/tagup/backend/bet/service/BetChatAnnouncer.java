package com.tagup.backend.bet.service;

import com.google.cloud.firestore.FieldValue;
import com.google.cloud.firestore.Firestore;
import com.google.firebase.FirebaseApp;
import com.google.firebase.cloud.FirestoreClient;
import com.tagup.backend.bet.entity.Bet;
import com.tagup.backend.bet.entity.BetResult;
import com.tagup.backend.game.entity.Game;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 내기 정산 결과를 더그아웃 채팅(Firestore)에 시스템 메시지로 발송.
 * 제안/콜/취소 안내는 행동한 유저의 앱이 직접 작성하고,
 * 스케줄러가 처리하는 정산만 서버가 발송한다.
 */
@Slf4j
@Component
public class BetChatAnnouncer {

    public void announceSettlement(Bet bet, Game game) {
        // 로컬 개발 모드 등 Firebase Admin 미초기화 환경에서는 발송 생략
        if (FirebaseApp.getApps().isEmpty()) {
            log.debug("[정산 안내] Firebase 미초기화로 발송 생략 betId={}", bet.getId());
            return;
        }

        try {
            Firestore db = FirestoreClient.getFirestore();

            Map<String, Object> message = new HashMap<>();
            message.put("roomId", String.valueOf(bet.getRoom().getId()));
            message.put("senderId", "system");
            message.put("senderNickname", "태그업");
            message.put("content", buildContent(bet, game));
            message.put("type", "BET");
            message.put("betId", bet.getId());
            message.put("createdAt", FieldValue.serverTimestamp());

            db.collection("rooms")
                    .document(bet.getRoom().getChatKey())
                    .collection("messages")
                    .add(message);
        } catch (Exception e) {
            log.warn("[정산 안내] 발송 실패 betId={}: {}", bet.getId(), e.getMessage());
        }
    }

    private String buildContent(Bet bet, Game game) {
        String score = String.format("%s %d : %d %s",
                game.getAwayTeam().getShortName(), game.getAwayScore(),
                game.getHomeScore(), game.getHomeTeam().getShortName());

        if (bet.getProposerResult() == BetResult.DRAW) {
            return String.format("🤝 무승부! \"%s\" 내기는 무효예요 (%s)", bet.getContent(), score);
        }

        String winner = bet.getProposerResult() == BetResult.WIN
                ? bet.getProposer().getNickname()
                : bet.getReceiver().getNickname();
        return String.format("🏆 %s님 승리! \"%s\" 내기 정산 완료 (%s)", winner, bet.getContent(), score);
    }
}
