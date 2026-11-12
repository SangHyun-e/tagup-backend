package com.tagup.backend.room.scheduler;

import com.tagup.backend.game.entity.Game;
import com.tagup.backend.game.entity.GameStatus;
import com.tagup.backend.game.repository.GameRepository;
import com.tagup.backend.room.entity.Room;
import com.tagup.backend.room.repository.RoomRepository;
import com.tagup.backend.team.entity.Team;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChatActivationScheduler {

    private final GameRepository gameRepository;
    private final RoomRepository roomRepository;

    // 매일 오전 7시 30분: 오늘 경기 있는 팀 더그아웃 채팅 활성화
    @Scheduled(cron = "0 30 7 * * *", zone = "Asia/Seoul")
    @Transactional
    public void activateChatForTodayGames() {
        log.info("[스케줄러] 채팅 활성화 시작");

        LocalDate today = LocalDate.now();
        Set<Long> playingTeamIds = getPlayingTeamIds(today);

        if (playingTeamIds.isEmpty()) {
            log.info("[스케줄러] 오늘 경기 없음 - 채팅 활성화 없음");
            return;
        }

        List<Room> roomsToActivate = roomRepository.findRoomsHavingMembersWithFavoriteTeamIn(playingTeamIds);
        roomsToActivate.forEach(room -> room.setChatEnabled(true));

        log.info("[스케줄러] 채팅 활성화 완료: {} 개 더그아웃, 플레이 팀 IDs={}", roomsToActivate.size(), playingTeamIds);
    }

    // 매일 자정: 모든 더그아웃 채팅 비활성화
    @Scheduled(cron = "0 0 0 * * *", zone = "Asia/Seoul")
    @Transactional
    public void deactivateAllChats() {
        log.info("[스케줄러] 자정 채팅 일괄 비활성화");
        List<Room> activeRooms = roomRepository.findByChatEnabled(true);
        activeRooms.forEach(room -> room.setChatEnabled(false));
        log.info("[스케줄러] 채팅 비활성화 완료: {} 개 더그아웃", activeRooms.size());
    }

    // 경기 종료 감지: 30분마다 모든 경기 종료 시 채팅 비활성화 (오후 6시~자정)
    @Scheduled(cron = "0 */30 18-23 * * *", zone = "Asia/Seoul")
    @Transactional
    public void deactivateChatIfAllGamesFinished() {
        LocalDate today = LocalDate.now();
        List<Game> todayGames = gameRepository.findByGameDateAndStatusIn(
                today, List.of(GameStatus.SCHEDULED, GameStatus.IN_PROGRESS));

        // 진행 중이거나 예정된 경기가 없으면 채팅 비활성화
        if (todayGames.isEmpty()) {
            log.info("[스케줄러] 오늘 경기 모두 종료 - 채팅 비활성화");
            List<Room> activeRooms = roomRepository.findByChatEnabled(true);
            activeRooms.forEach(room -> room.setChatEnabled(false));
            log.info("[스케줄러] {} 개 더그아웃 채팅 비활성화", activeRooms.size());
        }
    }

    private Set<Long> getPlayingTeamIds(LocalDate date) {
        Set<Long> ids = new HashSet<>();
        gameRepository.findHomeTeamsByDate(date).stream()
                .map(Team::getId).forEach(ids::add);
        gameRepository.findAwayTeamsByDate(date).stream()
                .map(Team::getId).forEach(ids::add);
        return ids;
    }
}
