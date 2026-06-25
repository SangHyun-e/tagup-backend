package com.tagup.backend.team.entity;

import jakarta.persistence.*;
import lombok.Getter;

@Entity
@Table(name = "teams")
@Getter
public class Team {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, length = 10)
    private String shortName;

    private String logoUrl;
}
