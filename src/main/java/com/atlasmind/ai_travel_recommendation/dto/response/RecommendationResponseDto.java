package com.atlasmind.ai_travel_recommendation.dto.response;

import java.time.LocalDate;
import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class RecommendationResponseDto {
    private Integer tmdbId;
    private String movieTitle;
    private String movieOverview;
    private String posterPath;
    private String backdropPath;
    private LocalDate releaseDate;
    private Double rating;
    private Integer runtime;
    private Double popularity;
    private List<String> genres;
    private boolean onWatchlist;
    private String watchlistStatus;
    private List<String> reasons;
}
