package com.atlasmind.ai_travel_recommendation.models;

import jakarta.persistence.*;
import java.time.LocalDateTime;

public class WatchList {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "watchList_seq")
    @SequenceGenerator(name = "watchList_seq", sequenceName = "db_watchList", allocationSize = 50)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "movie_id", nullable = false)
    private Movie movie;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private WatchListStatus status;

    @Column(nullable = false, updatable = false)
    private LocalDateTime addedAt;
}
