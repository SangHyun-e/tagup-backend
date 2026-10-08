package com.tagup.backend.team.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "teams")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Team {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, length = 10)
    private String shortName;

    private String logoUrl;

    @Builder
    public Team(String name, String shortName, String logoUrl) {
        this.name = name;
        this.shortName = shortName;
        this.logoUrl = logoUrl;
    }
}
