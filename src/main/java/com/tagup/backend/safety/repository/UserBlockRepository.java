package com.tagup.backend.safety.repository;

import com.tagup.backend.safety.entity.UserBlock;
import com.tagup.backend.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserBlockRepository extends JpaRepository<UserBlock, Long> {

    boolean existsByBlockerAndBlocked(User blocker, User blocked);

    Optional<UserBlock> findByBlockerAndBlocked(User blocker, User blocked);

    @Query("SELECT b FROM UserBlock b JOIN FETCH b.blocked WHERE b.blocker = :blocker ORDER BY b.createdAt DESC")
    List<UserBlock> findAllByBlockerWithUser(@Param("blocker") User blocker);

    void deleteByBlockerOrBlocked(User blocker, User blocked);
}
