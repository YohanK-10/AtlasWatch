package com.atlasmind.ai_travel_recommendation.service;

import com.atlasmind.ai_travel_recommendation.dto.request.SoloRecommendationRequestDto;
import com.atlasmind.ai_travel_recommendation.dto.response.SoloRecommendationResponseDto;
import com.atlasmind.ai_travel_recommendation.models.Genre;
import com.atlasmind.ai_travel_recommendation.models.Movie;
import com.atlasmind.ai_travel_recommendation.models.MovieGenre;
import com.atlasmind.ai_travel_recommendation.models.User;
import com.atlasmind.ai_travel_recommendation.models.WatchList;
import com.atlasmind.ai_travel_recommendation.models.WatchListStatus;
import com.atlasmind.ai_travel_recommendation.repository.MovieGenreRepository;
import com.atlasmind.ai_travel_recommendation.repository.ReviewRepository;
import com.atlasmind.ai_travel_recommendation.repository.WatchlistRepository;
import com.atlasmind.ai_travel_recommendation.support.TestFixtures;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecommendationServiceTest {

    @Mock
    private WatchlistRepository watchlistRepository;
    @Mock
    private ReviewRepository reviewRepository;
    @Mock
    private MovieGenreRepository movieGenreRepository;

    @InjectMocks
    private RecommendationService recommendationService;

    @Test
    void soloRecommendationsPreferMoodRuntimeAndOlderWatchlistMovies() {
        User user = TestFixtures.user(1L, "alice", "alice@example.com");

        Movie thriller = TestFixtures.movie(11L, 111, "Short Thriller");
        thriller.setRuntime(98);
        thriller.setMovieRating(8.4);

        Movie comfort = TestFixtures.movie(12L, 222, "Long Comfort");
        comfort.setRuntime(148);
        comfort.setMovieRating(7.2);

        WatchList oldThriller = TestFixtures.watchList(101L, user, thriller, WatchListStatus.PLAN_TO_WATCH);
        oldThriller.setAddedAt(LocalDateTime.now().minusDays(220));

        WatchList newerComfort = TestFixtures.watchList(102L, user, comfort, WatchListStatus.PLAN_TO_WATCH);
        newerComfort.setAddedAt(LocalDateTime.now().minusDays(7));

        Genre thrillerGenre = TestFixtures.genre(1L, 53, "Thriller");
        Genre dramaGenre = TestFixtures.genre(2L, 18, "Drama");
        Genre comedyGenre = TestFixtures.genre(3L, 35, "Comedy");

        when(watchlistRepository.findByUserIdWithDetails(1L)).thenReturn(List.of(oldThriller, newerComfort));
        when(reviewRepository.findByUserIdWithDetails(1L)).thenReturn(List.of());
        when(movieGenreRepository.findByMovieIdInWithGenre(anyCollection()))
                .thenReturn(List.of(
                        new MovieGenre(thriller, thrillerGenre),
                        new MovieGenre(thriller, dramaGenre),
                        new MovieGenre(comfort, comedyGenre)
                ));

        SoloRecommendationRequestDto request = new SoloRecommendationRequestDto();
        request.setMoods(List.of("tense"));
        request.setRuntimePreference("short");
        request.setLimit(5);

        List<SoloRecommendationResponseDto> results = recommendationService.getSoloRecommendations(
                user,
                request
        );

        assertEquals(2, results.size());
        assertEquals(111, results.get(0).getTmdbId());
        assertTrue(results.get(0).getScore() > results.get(1).getScore());
        assertTrue(results.get(0).getReasons().stream()
                .map(String::toLowerCase)
                .anyMatch(reason -> reason.contains("tense") && reason.contains("mood")));
        assertTrue(results.get(0).getReasons().stream().anyMatch(reason -> reason.contains("runtime fits")));
    }

    @Test
    void soloRecommendationsBoostGenresUserRatesHighly() {
        User user = TestFixtures.user(1L, "alice", "alice@example.com");

        Movie candidate = TestFixtures.movie(30L, 300, "Crime Night");
        candidate.setRuntime(118);

        Movie previouslyLoved = TestFixtures.movie(31L, 301, "Loved Crime Drama");

        WatchList candidateEntry = TestFixtures.watchList(200L, user, candidate, WatchListStatus.PLAN_TO_WATCH);
        candidateEntry.setAddedAt(LocalDateTime.now().minusDays(40));

        var strongReview = TestFixtures.review(401L, user, previouslyLoved);
        strongReview.setRating(9);

        Genre crimeGenre = TestFixtures.genre(4L, 80, "Crime");
        Genre dramaGenre = TestFixtures.genre(5L, 18, "Drama");

        when(watchlistRepository.findByUserIdWithDetails(1L)).thenReturn(List.of(candidateEntry));
        when(reviewRepository.findByUserIdWithDetails(1L)).thenReturn(List.of(strongReview));
        when(movieGenreRepository.findByMovieIdInWithGenre(anyCollection()))
                .thenReturn(List.of(
                        new MovieGenre(candidate, crimeGenre),
                        new MovieGenre(previouslyLoved, crimeGenre),
                        new MovieGenre(previouslyLoved, dramaGenre)
                ));

        SoloRecommendationRequestDto request = new SoloRecommendationRequestDto();
        request.setMoods(List.of("any"));
        request.setRuntimePreference("any");
        request.setLimit(5);

        List<SoloRecommendationResponseDto> results = recommendationService.getSoloRecommendations(
                user,
                request
        );

        assertEquals(1, results.size());
        assertFalse(results.get(0).getReasons().isEmpty());
        assertTrue(results.get(0).getReasons().stream().anyMatch(reason -> reason.contains("rate highly")));
    }

    @Test
    void soloRecommendationsIgnoreAlreadyWatchedMovies() {
        User user = TestFixtures.user(1L, "alice", "alice@example.com");
        Movie watchedMovie = TestFixtures.movie(40L, 400, "Already Watched");
        WatchList watchedEntry = TestFixtures.watchList(300L, user, watchedMovie, WatchListStatus.WATCHED);

        when(watchlistRepository.findByUserIdWithDetails(1L)).thenReturn(List.of(watchedEntry));
        SoloRecommendationRequestDto request = new SoloRecommendationRequestDto();
        request.setMoods(List.of("comforting"));
        request.setRuntimePreference("medium");
        request.setLimit(5);

        List<SoloRecommendationResponseDto> results = recommendationService.getSoloRecommendations(
                user,
                request
        );

        assertTrue(results.isEmpty());
    }
}
