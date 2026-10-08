package com.tagup.backend.user.repository;

import com.tagup.backend.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);

    /** 관리자 지정용 — 설정 파일의 이메일 대소문자가 가입 시와 다를 수 있다 */
    Optional<User> findByEmailIgnoreCase(String email);
    boolean existsByEmail(String email);

    Optional<User> findByFirebaseUid(String firebaseUid);

    @Query("SELECT u FROM User u LEFT JOIN FETCH u.favoriteTeam WHERE u.id = :id")
    Optional<User> findByIdWithTeam(@Param("id") Long id);
}
