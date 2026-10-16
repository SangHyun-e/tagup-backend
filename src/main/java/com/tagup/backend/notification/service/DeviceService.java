package com.tagup.backend.notification.service;

import com.tagup.backend.notification.dto.RegisterDeviceRequest;
import com.tagup.backend.notification.entity.UserDevice;
import com.tagup.backend.notification.repository.UserDeviceRepository;
import com.tagup.backend.user.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceService {

    private final UserDeviceRepository userDeviceRepository;

    /** 등록(upsert) — 같은 토큰이 이미 있으면 현재 유저로 재배정 (기기 공유/재로그인 대응) */
    @Transactional
    public void register(User user, RegisterDeviceRequest request) {
        userDeviceRepository.findByPushToken(request.pushToken())
                .ifPresentOrElse(
                        device -> device.reassignTo(user, request.platform()),
                        () -> userDeviceRepository.save(UserDevice.builder()
                                .user(user)
                                .pushToken(request.pushToken())
                                .platform(request.platform())
                                .build()));
    }

    /** 해제 — 로그아웃 시 호출 (다른 계정으로 알림이 가는 것 방지) */
    @Transactional
    public void unregister(String pushToken) {
        userDeviceRepository.deleteByPushToken(pushToken);
    }
}
