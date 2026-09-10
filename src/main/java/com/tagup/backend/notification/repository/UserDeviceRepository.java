package com.tagup.backend.notification.repository;

import com.tagup.backend.notification.entity.UserDevice;
import com.tagup.backend.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface UserDeviceRepository extends JpaRepository<UserDevice, Long> {

    Optional<UserDevice> findByPushToken(String pushToken);

    List<UserDevice> findAllByUserIn(Collection<User> users);

    void deleteByPushToken(String pushToken);
}
