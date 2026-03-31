package com.atlasmind.ai_travel_recommendation.service;

import com.atlasmind.ai_travel_recommendation.dto.tmdb.MovieDetailDto;
import com.atlasmind.ai_travel_recommendation.dto.tmdb.SearchResponseDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriBuilder;

@Slf4j
@Service
@RequiredArgsConstructor
public class TmdbApiService {

    private final RestClient tmdbRestClient;

    /**
     * Search for movies by title.
     * TMDB endpoint: GET /search/movie?query={query}&page={page}
     */
    public SearchResponseDto searchMovies(String query, int page) {
        try {
            return tmdbRestClient.get().uri("/search/movie?query={query}&page={page}", query, page)
                    .retrieve().body(SearchResponseDto.class);
        } catch (RestClientException e) {
            log.error("TMDB search failed for query '{}': {}", query, e.getMessage());
            return null;
        }
    }

    /**
     * Get detailed information about a specific movie.
     * TMDB endpoint: GET /movie/{tmdbId}
     */
    public MovieDetailDto getMovieDetails(Long tmdbId) {
        try {
            return tmdbRestClient.get().uri("/movie/{tmdbId}", tmdbId).retrieve()
                    .body(MovieDetailDto.class);
        } catch (RestClientException e) {
            log.error("TMDB movie details failed for ID {}: {}", tmdbId, e.getMessage());
            return null;
        }
    }

    /**
     * Get currently trending movies (updated daily by TMDB).
     * TMDB endpoint: GET /trending/movie/day
     */
    public SearchResponseDto getTrendingMovies() {
        return getTrendingMovies("day", 1);
    }

    /**
     * Get trending movies for a specific TMDB window and page.
     * TMDB endpoint: GET /trending/movie/{window}?page={page}
     */
    public SearchResponseDto getTrendingMovies(String window, int page) {
        try {
            return tmdbRestClient.get().uri("/trending/movie/{window}?page={page}", window, page).retrieve()
                    .body(SearchResponseDto.class);
        } catch (RestClientException e) {
            log.error("TMDB trending movies failed for window '{}' page {}: {}", window, page, e.getMessage());
            return null;
        }
    }

    /**
     * Get popular movies.
     * TMDB endpoint: GET /movie/popular?page={page}
     */
    public SearchResponseDto getPopularMovies(int page) {
        return getPagedMovieList("/movie/popular", page, "popular");
    }

    /**
     * Get top rated movies.
     * TMDB endpoint: GET /movie/top_rated?page={page}
     */
    public SearchResponseDto getTopRatedMovies(int page) {
        return getPagedMovieList("/movie/top_rated", page, "top rated");
    }

    /**
     * Discover movies for a specific genre.
     * TMDB endpoint: GET /discover/movie?with_genres={genreId}&page={page}
     */
    public SearchResponseDto discoverMoviesByGenre(int genreId, int page) {
        try {
            return tmdbRestClient.get()
                    .uri((UriBuilder builder) -> builder.path("/discover/movie")
                            .queryParam("with_genres", genreId)
                            .queryParam("page", page)
                            .queryParam("include_adult", false)
                            .queryParam("include_video", false)
                            .queryParam("sort_by", "popularity.desc")
                            .build())
                    .retrieve()
                    .body(SearchResponseDto.class);
        } catch (RestClientException e) {
            log.error("TMDB discover failed for genre {} page {}: {}", genreId, page, e.getMessage());
            return null;
        }
    }

    private SearchResponseDto getPagedMovieList(String path, int page, String label) {
        try {
            return tmdbRestClient.get().uri(path + "?page={page}", page)
                    .retrieve().body(SearchResponseDto.class);
        } catch (RestClientException e) {
            log.error("TMDB {} movies failed for page {}: {}", label, page, e.getMessage());
            return null;
        }
    }
}
