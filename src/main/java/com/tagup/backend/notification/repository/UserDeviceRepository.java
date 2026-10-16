package com.tagup.backend.notification.repository;

import com.tagup.backend.notification.entity.UserDevice;
import com.tagup.backend.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface UserDeviceRepository extends JpaRepository<UserDevice, Long> {

    /** 탈퇴 시 기기 토큰까지 지운다 — 남겨두면 탈퇴한 계정으로 푸시가 간다 */
    void deleteByUser(User user);

    Optional<UserDevice> findByPushToken(String pushToken);

    List<UserDevice> findAllByUserIn(Collection<User> users);

    void deleteByPushToken(String pushToken);
}
