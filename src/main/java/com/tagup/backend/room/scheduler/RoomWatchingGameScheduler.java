package com.tagup.backend.room.scheduler;

import com.tagup.backend.game.entity.Game;
import com.tagup.backend.game.repository.GameRepository;
import com.tagup.backend.room.entity.Room;
import com.tagup.backend.room.entity.RoomMember;
import com.tagup.backend.room.repository.RoomMemberRepository;
import com.tagup.backend.room.repository.RoomRepository;
import com.tagup.backend.room.service.WatchingGameSuggester;
import com.tagup.backend.user.entity.User;
import com.tagup.backend.team.entity.Team;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * 매일 아침, 각 더그아웃이 그날 볼 경기를 멤버들의 응원팀에서 유추해 지정한다.
 *
 * <p>더그아웃은 영구 그룹인데 경기는 매일 바뀐다. 방을 만들 때 팀을 고정하면 매일 방을
 * 새로 파야 하므로, 방은 그대로 두고 <b>관전 경기만 날마다 갈아끼운다.</b>
 *
 * <p>어제 경기가 남아 있으면 덮어쓴다. 근거가 없으면(멤버 응원팀이 오늘 경기에 없음)
 * 비워두고 사용자가 직접 고르게 한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RoomWatchingGameScheduler {

    private final RoomRepository roomRepository;
    private final RoomMemberRepository roomMemberRepository;
    private final GameRepository gameRepository;
    private final WatchingGameSuggester suggester = new WatchingGameSuggester();

    /** 채팅 활성화(7:30)와 같은 시각대. 경기 일정 수집(7:00) 이후여야 한다 */
    @Scheduled(cron = "0 35 7 * * *", zone = "Asia/Seoul")
    @Transactional
    public void assignTodayGames() {
        LocalDate today = LocalDate.now();
        List<Game> todayGames = gameRepository.findByGameDateWithTeams(today);

        List<Room> rooms = roomRepository.findAll();
        if (rooms.isEmpty()) return;

        if (todayGames.isEmpty()) {
            rooms.forEach(r -> r.setWatchingGame(null));
            log.info("[관전경기] {} 경기 없음 — 더그아웃 {}개 해제", today, rooms.size());
            return;
        }

        int assigned = 0;
        for (Room room : rooms) {
            List<Team> favorites = roomMemberRepository.findAllByRoomWithUser(room).stream()
                    .map(RoomMember::getUser)
                    .map(User::getFavoriteTeam)
                    .toList();

            Game picked = suggester.suggest(favorites, todayGames).orElse(null);
            room.setWatchingGame(picked);
            if (picked != null) assigned++;
        }

        log.info("[관전경기] {} 더그아웃 {}개 중 {}개 자동 지정 (오늘 {}경기)",
                today, rooms.size(), assigned, todayGames.size());
    }
}
