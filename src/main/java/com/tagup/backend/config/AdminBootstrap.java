package com.tagup.backend.config;

import com.tagup.backend.user.entity.User;
import com.tagup.backend.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 환경변수에 적힌 이메일을 <b>기동할 때마다</b> 관리자로 올린다.
 *
 * <p>운영 DB에 직접 UPDATE를 치지 않으려는 장치다. 관리자 권한을 잃어버려도 환경변수만 맞으면
 * 재기동으로 복구된다. 반대로 <b>권한을 뺏을 때는 환경변수에서 지워야</b> 한다 — 지우지 않고
 * DB만 고치면 다음 기동에 되돌아온다.
 *
 * <p>설정: {@code tagup.security.admin-emails=a@b.com,c@d.com} (비우면 아무 일도 하지 않는다)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminBootstrap implements ApplicationRunner {

    private final UserRepository userRepository;
    private final AdminEmails adminEmails;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        List<String> emails = adminEmails.normalized();
        if (emails.isEmpty()) return;

        for (String email : emails) {
            userRepository.findByEmailIgnoreCase(email).ifPresentOrElse(user -> {
                if (user.isAdmin()) return;
                user.promoteToAdmin();
                userRepository.save(user);
                log.info("[관리자] {} 를 관리자로 지정했습니다", email);
            }, () -> log.warn("[관리자] {} 는 아직 가입하지 않았습니다 — 가입 후 재기동하면 지정됩니다", email));
        }
    }
}
