package com.tagup.backend.user.service;

import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;
import com.tagup.backend.bet.entity.Bet;
import com.tagup.backend.bet.entity.BetStatus;
import com.tagup.backend.bet.repository.BetRepository;
import com.tagup.backend.notification.repository.UserDeviceRepository;
import com.tagup.backend.room.repository.RoomMemberRepository;
import com.tagup.backend.user.entity.User;
import com.tagup.backend.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 회원 탈퇴.
 *
 * <p><b>앱 안에서 계정을 지울 수 있어야 한다.</b> 애플 심사 지침 5.1.1(v)는 계정을 만드는 앱에
 * 앱 내 삭제 수단을 요구한다. 없으면 반려된다.
 *
 * <p><b>행을 지우지 않고 익명화한다.</b> 내기는 둘이 걸고 둘이 정산한다. 한쪽 행을 지우면
 * 남은 사람의 기록까지 깨지고, 이미 끝난 내기의 결과도 설명할 수 없게 된다. 그래서
 * 개인정보(이메일·닉네임·Firebase 연결)만 끊고 기록에는 "탈퇴한 사용자"로 남긴다.
 *
 * <p>Firebase 계정은 <b>실제로 지운다</b>. 우리 쪽 연결만 끊고 두면 같은 이메일로 다시 가입할 때
 * 예전 uid가 살아 있어 꼬인다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WithdrawService {

    private final UserRepository userRepository;
    private final RoomMemberRepository roomMemberRepository;
    private final BetRepository betRepository;
    private final UserDeviceRepository userDeviceRepository;

    @Transactional
    public void withdraw(User user) {
        String firebaseUid = user.getFirebaseUid();

        // 아직 아무도 받지 않은 내기는 취소한다 — 탈퇴한 사람의 제안을 남겨둘 이유가 없다.
        // 이미 성립된 내기는 건드리지 않는다. 상대방에게는 결과가 나와야 한다.
        List<Bet> pending = betRepository.findByProposerAndStatus(user, BetStatus.PENDING);
        pending.forEach(Bet::cancel);

        roomMemberRepository.deleteByUser(user);
        userDeviceRepository.deleteByUser(user);

        user.withdraw();
        user.anonymize();
        userRepository.save(user);

        deleteFirebaseAccount(firebaseUid);
        log.info("[탈퇴] 사용자 {} 탈퇴 처리 — 미수락 내기 {}건 취소", user.getId(), pending.size());
    }

    /** 실패해도 탈퇴 자체는 되돌리지 않는다. 우리 쪽에서 이미 로그인할 수 없는 상태다 */
    private void deleteFirebaseAccount(String firebaseUid) {
        if (firebaseUid == null || FirebaseApp.getApps().isEmpty()) return;
        try {
            FirebaseAuth.getInstance().deleteUser(firebaseUid);
        } catch (Exception e) {
            log.warn("[탈퇴] Firebase 계정 삭제 실패 uid={}: {}", firebaseUid, e.toString());
        }
    }
}
