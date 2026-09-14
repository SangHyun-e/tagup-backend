package com.tagup.backend.user.entity;

import com.tagup.backend.team.entity.Team;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true)
    private String firebaseUid;

    @Column(unique = true, nullable = false)
    private String email;

    // Firebase Auth가 인증을 담당하므로 nullable (소셜 로그인 지원)
    @Column
    private String password;

    @Column(nullable = false, length = 20)
    private String nickname;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_id")
    private Team favoriteTeam;

    @CreatedDate
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;

    @Builder
    public User(String firebaseUid, String email, String password, String nickname) {
        this.firebaseUid = firebaseUid;
        this.email = email;
        this.password = password;
        this.nickname = nickname;
    }

    public void updateFavoriteTeam(Team team) {
        this.favoriteTeam = team;
    }

    public void updateNickname(String nickname) {
        this.nickname = nickname;
    }

    public void linkFirebaseUid(String firebaseUid) {
        this.firebaseUid = firebaseUid;
    }
}
