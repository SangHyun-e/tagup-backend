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

    /** 권한. null인 과거 행은 일반 유저로 본다 */
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private UserRole role;

    /** 상태. null인 과거 행은 정상으로 본다 */
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private UserStatus status;

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
        this.role = UserRole.USER;
        this.status = UserStatus.ACTIVE;
    }

    /**
     * 권한. <b>null을 일반 유저로 돌려준다</b> — 컬럼이 추가되기 전에 만들어진 행이 있다.
     * (로컬 H2는 {@code ddl-auto: update}로 컬럼만 붙고 기존 행은 비어 있다)
     */
    public UserRole getRole() {
        return role == null ? UserRole.USER : role;
    }

    /** 상태. null은 정상으로 본다 — 위와 같은 이유 */
    public UserStatus getStatus() {
        return status == null ? UserStatus.ACTIVE : status;
    }

    public boolean isAdmin() {
        return getRole() == UserRole.ADMIN;
    }

    public boolean canUseService() {
        return getStatus().canUseService();
    }

    public void promoteToAdmin() {
        this.role = UserRole.ADMIN;
    }

    public void block() {
        this.status = UserStatus.BLOCKED;
    }

    public void unblock() {
        this.status = UserStatus.ACTIVE;
    }

    public void withdraw() {
        this.status = UserStatus.WITHDRAWN;
    }

    /**
     * 탈퇴 시 개인정보를 지운다.
     *
     * <p>행 자체를 지우지 않는 이유는 <b>상대방의 내기 기록</b> 때문이다. 둘이 건 내기에서 한쪽이
     * 사라지면 남은 사람의 기록도 깨진다. 그래서 이메일·닉네임·Firebase 연결만 끊고,
     * 기록에는 "탈퇴한 사용자"로 남는다.
     *
     * <p>이메일은 유니크 제약이 있어 비울 수 없다. 다시 쓸 수 없는 값으로 바꾼다.
     */
    public void anonymize() {
        this.email = "withdrawn-" + id + "@tagup.invalid";
        this.nickname = "탈퇴한 사용자";
        this.firebaseUid = null;
        this.password = null;
        this.favoriteTeam = null;
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
