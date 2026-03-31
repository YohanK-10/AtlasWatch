package com.atlasmind.ai_travel_recommendation.service;

import com.atlasmind.ai_travel_recommendation.dto.request.RecommendationRequestDto;
import com.atlasmind.ai_travel_recommendation.dto.request.SoloRecommendationRequestDto;
import com.atlasmind.ai_travel_recommendation.dto.response.RecommendationResponseDto;
import com.atlasmind.ai_travel_recommendation.dto.response.SoloRecommendationResponseDto;
import com.atlasmind.ai_travel_recommendation.models.Genre;
import com.atlasmind.ai_travel_recommendation.models.Movie;
import com.atlasmind.ai_travel_recommendation.models.MovieGenre;
import com.atlasmind.ai_travel_recommendation.models.User;
import com.atlasmind.ai_travel_recommendation.models.WatchList;
import com.atlasmind.ai_travel_recommendation.models.WatchListStatus;
import com.atlasmind.ai_travel_recommendation.repository.MovieGenreRepository;
import com.atlasmind.ai_travel_recommendation.repository.MovieRepository;
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
import org.springframework.data.domain.Pageable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecommendationServiceTest {

    @Mock
    private WatchlistRepository watchlistRepository;
    @Mock
    private ReviewRepository reviewRepository;
    @Mock
    private MovieGenreRepository movieGenreRepository;
    @Mock
    private MovieRepository movieRepository;
    @Mock
    private MovieService movieService;

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

        List<SoloRecommendationResponseDto> results = recommendationService.getSoloRecommendations(user, request);

        assertEquals(2, results.size());
        assertEquals(111, results.get(0).getTmdbId());
        assertTrue(results.get(0).getScore() > results.get(1).getScore());
        assertTrue(results.get(0).getReasons().stream()
                .map(String::toLowerCase)
                .anyMatch(reason -> reason.contains("tense") && reason.contains("vibe")));
        assertTrue(results.get(0).getReasons().stream().anyMatch(reason -> reason.contains("runtime fits")));
    }

    @Test
    void recommendationsUseCatalogSignalsAndFlagWatchlistMovies() {
        User user = TestFixtures.user(1L, "alice", "alice@example.com");

        Movie watchlistMovie = TestFixtures.movie(10L, 110, "Shadow District");
        watchlistMovie.setRuntime(100);
        watchlistMovie.setMovieRating(8.2);
        watchlistMovie.setPopularity(90.0);

        Movie genreMatchMovie = TestFixtures.movie(11L, 111, "Fractured Case");
        genreMatchMovie.setRuntime(103);
        genreMatchMovie.setMovieRating(8.4);
        genreMatchMovie.setPopularity(135.0);

        Movie watchedMovie = TestFixtures.movie(12L, 112, "Already Seen");
        watchedMovie.setRuntime(102);

        Movie lovedMovie = TestFixtures.movie(13L, 113, "Loved Crime Story");
        Movie popularMovie = TestFixtures.movie(14L, 114, "Blockbuster Rush");
        popularMovie.setPopularity(250.0);
        popularMovie.setMovieRating(7.6);

        Movie topRatedMovie = TestFixtures.movie(15L, 115, "Awards Magnet");
        topRatedMovie.setPopularity(55.0);
        topRatedMovie.setMovieRating(9.2);

        WatchList watchlistEntry = TestFixtures.watchList(201L, user, watchlistMovie, WatchListStatus.PLAN_TO_WATCH);
        WatchList watchedEntry = TestFixtures.watchList(202L, user, watchedMovie, WatchListStatus.WATCHED);

        var strongReview = TestFixtures.review(301L, user, lovedMovie);
        strongReview.setRating(9);
        strongReview.setReviewText("Excellent crime thriller.");

        Genre thriller = TestFixtures.genre(1L, 53, "Thriller");
        Genre crime = TestFixtures.genre(2L, 80, "Crime");
        Genre mystery = TestFixtures.genre(3L, 9648, "Mystery");
        Genre action = TestFixtures.genre(4L, 28, "Action");
        Genre drama = TestFixtures.genre(5L, 18, "Drama");

        when(movieRepository.count()).thenReturn(120L);
        when(watchlistRepository.findByUserIdWithDetails(1L)).thenReturn(List.of(watchlistEntry, watchedEntry));
        when(reviewRepository.findByUserIdWithDetails(1L)).thenReturn(List.of(strongReview));
        when(movieGenreRepository.findDistinctMoviesByGenreNames(anyCollection(), any(Pageable.class)))
                .thenReturn(List.of(genreMatchMovie));
        when(movieRepository.findTopPopularMovies(any(Pageable.class)))
                .thenReturn(List.of(popularMovie, genreMatchMovie));
        when(movieRepository.findTopRatedMovies(any(Pageable.class)))
                .thenReturn(List.of(topRatedMovie, genreMatchMovie));
        when(movieGenreRepository.findByMovieIdInWithGenre(anyCollection()))
                .thenReturn(List.of(
                        new MovieGenre(watchlistMovie, thriller),
                        new MovieGenre(watchlistMovie, crime),
                        new MovieGenre(genreMatchMovie, thriller),
                        new MovieGenre(genreMatchMovie, mystery),
                        new MovieGenre(watchedMovie, thriller),
                        new MovieGenre(lovedMovie, thriller),
                        new MovieGenre(lovedMovie, crime),
                        new MovieGenre(popularMovie, action),
                        new MovieGenre(topRatedMovie, drama)
                ));

        RecommendationRequestDto request = new RecommendationRequestDto(List.of("tense"), "short", 4);

        List<RecommendationResponseDto> results = recommendationService.getRecommendations(user, request);

        assertEquals(4, results.size());
        assertTrue(results.stream().noneMatch(result -> result.getTmdbId() == 112));
        assertTrue(results.stream().anyMatch(result -> result.isOnWatchlist()
                && "PLAN_TO_WATCH".equals(result.getWatchlistStatus())));
        assertTrue(results.stream().flatMap(result -> result.getReasons().stream())
                .anyMatch(reason -> reason.toLowerCase().contains("watchlist")));
        assertTrue(results.stream().flatMap(result -> result.getReasons().stream())
                .anyMatch(reason -> reason.toLowerCase().contains("rate highly")));
    }

    @Test
    void recommendationsExcludeWatchedMoviesFromFinalResults() {
        User user = TestFixtures.user(1L, "alice", "alice@example.com");
        Movie watchedMovie = TestFixtures.movie(20L, 220, "Watched Already");
        Movie otherMovie = TestFixtures.movie(21L, 221, "Still Eligible");
        WatchList watchedEntry = TestFixtures.watchList(401L, user, watchedMovie, WatchListStatus.WATCHED);

        Genre thriller = TestFixtures.genre(7L, 53, "Thriller");

        when(movieRepository.count()).thenReturn(120L);
        when(watchlistRepository.findByUserIdWithDetails(1L)).thenReturn(List.of(watchedEntry));
        when(reviewRepository.findByUserIdWithDetails(1L)).thenReturn(List.of());
        when(movieRepository.findTopPopularMovies(any(Pageable.class))).thenReturn(List.of(watchedMovie, otherMovie));
        when(movieRepository.findTopRatedMovies(any(Pageable.class))).thenReturn(List.of(otherMovie));
        when(movieGenreRepository.findByMovieIdInWithGenre(anyCollection()))
                .thenReturn(List.of(
                        new MovieGenre(watchedMovie, thriller),
                        new MovieGenre(otherMovie, thriller)
                ));

        RecommendationRequestDto request = new RecommendationRequestDto(List.of("tense"), "any", 3);

        List<RecommendationResponseDto> results = recommendationService.getRecommendations(user, request);

        assertEquals(1, results.size());
        assertEquals(221, results.get(0).getTmdbId());
    }

    @Test
    void coldStartRecommendationsFallbackToPopularAndHighlyRatedCatalogMovies() {
        Movie popularMovie = TestFixtures.movie(30L, 330, "Crowd Favorite");
        popularMovie.setPopularity(320.0);
        popularMovie.setMovieRating(7.8);

        Movie highlyRatedMovie = TestFixtures.movie(31L, 331, "Critics Darling");
        highlyRatedMovie.setPopularity(65.0);
        highlyRatedMovie.setMovieRating(9.3);

        Genre adventure = TestFixtures.genre(9L, 12, "Adventure");
        Genre drama = TestFixtures.genre(10L, 18, "Drama");

        when(movieRepository.count()).thenReturn(120L);
        when(movieRepository.findTopPopularMovies(any(Pageable.class))).thenReturn(List.of(popularMovie));
        when(movieRepository.findTopRatedMovies(any(Pageable.class))).thenReturn(List.of(highlyRatedMovie));
        when(movieGenreRepository.findByMovieIdInWithGenre(anyCollection()))
                .thenReturn(List.of(
                        new MovieGenre(popularMovie, adventure),
                        new MovieGenre(highlyRatedMovie, drama)
                ));

        RecommendationRequestDto request = new RecommendationRequestDto(List.of("any"), "any", 5);

        List<RecommendationResponseDto> results = recommendationService.getColdStartRecommendations(request);

        assertEquals(2, results.size());
        assertTrue(results.stream().noneMatch(RecommendationResponseDto::isOnWatchlist));
        assertTrue(results.stream().flatMap(result -> result.getReasons().stream())
                .anyMatch(reason -> reason.toLowerCase().contains("wider catalog")
                        || reason.toLowerCase().contains("popular catalog")
                        || reason.toLowerCase().contains("audience ratings")));
    }
}
