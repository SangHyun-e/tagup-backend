package com.tagup.backend.game.live;

import com.google.cloud.firestore.FieldValue;
import com.google.cloud.firestore.Firestore;
import com.google.firebase.FirebaseApp;
import com.google.firebase.cloud.FirestoreClient;
import com.tagup.backend.room.entity.Room;
import com.tagup.backend.room.repository.RoomRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 라이브 중계를 더그아웃 채팅에 흘려보낸다.
 *
 * <p>그 경기를 <b>같이 보고 있는 더그아웃</b>에만 보낸다({@code rooms.watching_game_id}).
 * 다른 경기를 보는 방에까지 쏘면 남의 경기 중계로 채팅이 덮인다.
 *
 * <p><b>메시지가 적지 않다.</b> 타석당 2건(시작·결과)이고 경기당 타석이 70~100개이므로
 * 한 경기에 140~200건이 흐른다. 실제 대화를 밀어내면 끄거나 줄여야 하므로
 * {@code tagup.live.relay-enabled}로 분리해뒀다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LiveChatAnnouncer {

    private static final String TYPE = "LIVE";

    private final RoomRepository roomRepository;

    @Value("${tagup.live.relay-enabled:true}")
    private boolean relayEnabled;

    @EventListener
    @Transactional(readOnly = true)
    public void onAtBatStarted(AtBatStartedEvent event) {
        send(event.gameId(), event.kboGameId(),
                LiveRelayMessage.atBatStarted(event), "AT_BAT_START");
    }

    @EventListener
    @Transactional(readOnly = true)
    public void onAtBatFinished(AtBatDetectedEvent event) {
        send(event.gameId(), event.kboGameId(),
                LiveRelayMessage.atBatFinished(event.atBat()), "AT_BAT_RESULT");
    }

    /**
     * <b>실패해도 밖으로 던지지 않는다.</b> 리스너는 폴러 스레드에서 동기 호출되므로,
     * 중계 한 건 때문에 그 주기의 경기 감지가 죽으면 손해가 훨씬 크다.
     */
    private void send(Long gameId, String kboGameId, String content, String kind) {
        if (!relayEnabled) return;
        if (FirebaseApp.getApps().isEmpty()) {
            log.debug("[중계] Firebase 미초기화로 발송 생략 {}", kboGameId);
            return;
        }

        try {
            List<Room> rooms = roomRepository.findByWatchingGameId(gameId);
            if (rooms.isEmpty()) return;

            Firestore db = FirestoreClient.getFirestore();
            for (Room room : rooms) {
                Map<String, Object> message = new HashMap<>();
                message.put("roomId", String.valueOf(room.getId()));
                message.put("senderId", "system");
                message.put("senderNickname", "태그업");
                message.put("content", content);
                message.put("type", TYPE);
                message.put("liveKind", kind);
                message.put("createdAt", FieldValue.serverTimestamp());

                db.collection("rooms").document(room.getChatKey())
                        .collection("messages").add(message);
            }
        } catch (Exception e) {
            log.warn("[중계] 발송 실패 {} ({}): {}", kboGameId, kind, e.toString());
        }
    }
}
