package com.tagup.backend.user.repository;

import com.tagup.backend.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);

    Optional<User> findByFirebaseUid(String firebaseUid);

    @Query("SELECT u FROM User u LEFT JOIN FETCH u.favoriteTeam WHERE u.id = :id")
    Optional<User> findByIdWithTeam(@Param("id") Long id);
}
