package com.atlasmind.ai_travel_recommendation.service;

import com.atlasmind.ai_travel_recommendation.config.RecommendationScoringProperties;
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
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyDouble;
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
    private CatalogIngestionService catalogIngestionService;
    @Spy
    private UserTasteProfileService userTasteProfileService = new UserTasteProfileService();
    @Spy
    private RecommendationScoringProperties recommendationScoringProperties = new RecommendationScoringProperties();

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

        when(watchlistRepository.findByUserIdWithDetails(1L)).thenReturn(List.of(watchlistEntry, watchedEntry));
        when(watchlistRepository.findMovieIdsByUserIdAndStatus(1L, WatchListStatus.WATCHED)).thenReturn(List.of(12L));
        when(reviewRepository.findByUserIdWithDetails(1L)).thenReturn(List.of(strongReview));
        when(movieGenreRepository.findDistinctRecommendationReadyMoviesByGenreNames(anyCollection(), anyDouble(), any(Pageable.class)))
                .thenReturn(List.of(genreMatchMovie));
        when(movieRepository.findRecommendationReadyPopularMovies(anyDouble(), any(Pageable.class)))
                .thenReturn(List.of(popularMovie, genreMatchMovie));
        when(movieRepository.findRecommendationReadyTopRatedMovies(anyDouble(), any(Pageable.class)))
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
    void recommendationsPenalizeGenresTheUserRatesPoorly() {
        User user = TestFixtures.user(1L, "alice", "alice@example.com");

        Movie likedReviewMovie = TestFixtures.movie(60L, 660, "Loved Mystery");
        Movie dislikedReviewMovie = TestFixtures.movie(61L, 661, "Disliked Romance");

        Movie thrillerCandidate = TestFixtures.movie(62L, 662, "Tense Pick");
        thrillerCandidate.setMovieRating(7.8);
        thrillerCandidate.setPopularity(105.0);

        Movie romanceCandidate = TestFixtures.movie(63L, 663, "Soft Focus");
        romanceCandidate.setMovieRating(8.0);
        romanceCandidate.setPopularity(105.0);

        var positiveReview = TestFixtures.review(701L, user, likedReviewMovie);
        positiveReview.setRating(9);
        positiveReview.setReviewText("Great mystery payoff.");

        var negativeReview = TestFixtures.review(702L, user, dislikedReviewMovie);
        negativeReview.setRating(3);
        negativeReview.setReviewText("Not for me.");

        Genre thriller = TestFixtures.genre(21L, 53, "Thriller");
        Genre mystery = TestFixtures.genre(22L, 9648, "Mystery");
        Genre romance = TestFixtures.genre(23L, 10749, "Romance");

        when(watchlistRepository.findByUserIdWithDetails(1L)).thenReturn(List.of());
        when(watchlistRepository.findMovieIdsByUserIdAndStatus(1L, WatchListStatus.WATCHED)).thenReturn(List.of());
        when(reviewRepository.findByUserIdWithDetails(1L)).thenReturn(List.of(positiveReview, negativeReview));
        when(movieGenreRepository.findDistinctRecommendationReadyMoviesByGenreNames(anyCollection(), anyDouble(), any(Pageable.class)))
                .thenReturn(List.of(thrillerCandidate));
        when(movieRepository.findRecommendationReadyPopularMovies(anyDouble(), any(Pageable.class)))
                .thenReturn(List.of(thrillerCandidate, romanceCandidate));
        when(movieRepository.findRecommendationReadyTopRatedMovies(anyDouble(), any(Pageable.class)))
                .thenReturn(List.of(romanceCandidate, thrillerCandidate));
        when(movieGenreRepository.findByMovieIdInWithGenre(anyCollection()))
                .thenReturn(List.of(
                        new MovieGenre(likedReviewMovie, thriller),
                        new MovieGenre(likedReviewMovie, mystery),
                        new MovieGenre(dislikedReviewMovie, romance),
                        new MovieGenre(thrillerCandidate, thriller),
                        new MovieGenre(thrillerCandidate, mystery),
                        new MovieGenre(romanceCandidate, romance)
                ));

        RecommendationRequestDto request = new RecommendationRequestDto(List.of("any"), "any", 5);

        List<RecommendationResponseDto> results = recommendationService.getRecommendations(user, request);

        assertEquals(2, results.size());
        assertEquals(662, results.get(0).getTmdbId());
        assertEquals(663, results.get(1).getTmdbId());
    }

    @Test
    void recommendationsApplyRuntimeHardFiltersBeforeRanking() {
        Movie shortMovie = TestFixtures.movie(70L, 770, "Short Match");
        shortMovie.setRuntime(101);
        shortMovie.setMovieRating(8.1);

        Movie longMovie = TestFixtures.movie(71L, 771, "Long Epic");
        longMovie.setRuntime(164);
        longMovie.setMovieRating(8.9);

        Genre thriller = TestFixtures.genre(24L, 53, "Thriller");

        when(movieRepository.findRecommendationReadyPopularMovies(anyDouble(), any(Pageable.class)))
                .thenReturn(List.of(shortMovie, longMovie));
        when(movieRepository.findRecommendationReadyTopRatedMovies(anyDouble(), any(Pageable.class)))
                .thenReturn(List.of(longMovie, shortMovie));
        when(movieGenreRepository.findByMovieIdInWithGenre(anyCollection()))
                .thenReturn(List.of(
                        new MovieGenre(shortMovie, thriller),
                        new MovieGenre(longMovie, thriller)
                ));

        RecommendationRequestDto request = new RecommendationRequestDto(List.of("any"), "short", 5);

        List<RecommendationResponseDto> results = recommendationService.getColdStartRecommendations(request);

        assertEquals(1, results.size());
        assertEquals(770, results.get(0).getTmdbId());
    }

    @Test
    void recommendationsRespectConfigurableFeatureWeights() {
        recommendationScoringProperties.setGenreAffinityWeight(0.0);
        recommendationScoringProperties.setMoodMatchWeight(0.0);
        recommendationScoringProperties.setRuntimeMatchWeight(0.0);
        recommendationScoringProperties.setQualityWeight(0.0);
        recommendationScoringProperties.setPopularityWeight(0.0);
        recommendationScoringProperties.setWatchlistBoostWeight(0.0);
        recommendationScoringProperties.setWatchlistAgeWeight(0.0);
        recommendationScoringProperties.setDislikedGenrePenaltyWeight(0.0);
        recommendationScoringProperties.setFreshnessWeight(1.0);

        Movie recentMovie = TestFixtures.movie(80L, 880, "Fresh Arrival");
        recentMovie.setReleaseDate(java.time.LocalDate.now().minusMonths(8));
        recentMovie.setMovieRating(7.1);
        recentMovie.setPopularity(70.0);

        Movie olderMovie = TestFixtures.movie(81L, 881, "Classic Favorite");
        olderMovie.setReleaseDate(java.time.LocalDate.of(1995, 6, 1));
        olderMovie.setMovieRating(9.4);
        olderMovie.setPopularity(70.0);

        Genre drama = TestFixtures.genre(25L, 18, "Drama");

        when(movieRepository.findRecommendationReadyPopularMovies(anyDouble(), any(Pageable.class)))
                .thenReturn(List.of(recentMovie, olderMovie));
        when(movieRepository.findRecommendationReadyTopRatedMovies(anyDouble(), any(Pageable.class)))
                .thenReturn(List.of(olderMovie, recentMovie));
        when(movieGenreRepository.findByMovieIdInWithGenre(anyCollection()))
                .thenReturn(List.of(
                        new MovieGenre(recentMovie, drama),
                        new MovieGenre(olderMovie, drama)
                ));

        RecommendationRequestDto request = new RecommendationRequestDto(List.of("any"), "any", 5);

        List<RecommendationResponseDto> results = recommendationService.getColdStartRecommendations(request);

        assertEquals(2, results.size());
        assertEquals(880, results.get(0).getTmdbId());
        assertEquals(881, results.get(1).getTmdbId());
    }

    @Test
    void catalogRecommendationsUseDiversityRerankingToAvoidNearDuplicateGenreClusters() {
        recommendationScoringProperties.setDiversityPenaltyWeight(0.80);

        Movie thrillerLead = TestFixtures.movie(90L, 990, "Night Signal");
        thrillerLead.setMovieRating(8.6);
        thrillerLead.setPopularity(120.0);

        Movie thrillerFollowUp = TestFixtures.movie(91L, 991, "Shadow Wire");
        thrillerFollowUp.setMovieRating(8.4);
        thrillerFollowUp.setPopularity(118.0);

        Movie dramaAlternative = TestFixtures.movie(92L, 992, "Quiet Return");
        dramaAlternative.setMovieRating(8.2);
        dramaAlternative.setPopularity(115.0);

        Genre thriller = TestFixtures.genre(31L, 53, "Thriller");
        Genre mystery = TestFixtures.genre(32L, 9648, "Mystery");
        Genre drama = TestFixtures.genre(33L, 18, "Drama");

        when(movieRepository.findRecommendationReadyPopularMovies(anyDouble(), any(Pageable.class)))
                .thenReturn(List.of(thrillerLead, thrillerFollowUp, dramaAlternative));
        when(movieRepository.findRecommendationReadyTopRatedMovies(anyDouble(), any(Pageable.class)))
                .thenReturn(List.of(thrillerLead, thrillerFollowUp, dramaAlternative));
        when(movieGenreRepository.findByMovieIdInWithGenre(anyCollection()))
                .thenReturn(List.of(
                        new MovieGenre(thrillerLead, thriller),
                        new MovieGenre(thrillerLead, mystery),
                        new MovieGenre(thrillerFollowUp, thriller),
                        new MovieGenre(thrillerFollowUp, mystery),
                        new MovieGenre(dramaAlternative, drama)
                ));

        RecommendationRequestDto request = new RecommendationRequestDto(List.of("any"), "any", 3);

        List<RecommendationResponseDto> results = recommendationService.getColdStartRecommendations(request);

        assertEquals(3, results.size());
        assertEquals(990, results.get(0).getTmdbId());
        assertEquals(992, results.get(1).getTmdbId());
        assertEquals(991, results.get(2).getTmdbId());
    }

    @Test
    void soloRecommendationsUseDiversityRerankingToBreakUpRepeatedGenres() {
        recommendationScoringProperties.setDiversityPenaltyWeight(0.80);

        User user = TestFixtures.user(1L, "alice", "alice@example.com");

        Movie thrillerLead = TestFixtures.movie(100L, 1000, "Lead Thriller");
        thrillerLead.setMovieRating(8.5);

        Movie thrillerFollowUp = TestFixtures.movie(101L, 1001, "Second Thriller");
        thrillerFollowUp.setMovieRating(8.3);

        Movie comedyAlternative = TestFixtures.movie(102L, 1002, "Comic Break");
        comedyAlternative.setMovieRating(8.1);

        WatchList olderThriller = TestFixtures.watchList(801L, user, thrillerLead, WatchListStatus.PLAN_TO_WATCH);
        olderThriller.setAddedAt(LocalDateTime.now().minusDays(220));

        WatchList newerThriller = TestFixtures.watchList(802L, user, thrillerFollowUp, WatchListStatus.PLAN_TO_WATCH);
        newerThriller.setAddedAt(LocalDateTime.now().minusDays(60));

        WatchList comedyEntry = TestFixtures.watchList(803L, user, comedyAlternative, WatchListStatus.PLAN_TO_WATCH);
        comedyEntry.setAddedAt(LocalDateTime.now().minusDays(45));

        Genre thriller = TestFixtures.genre(34L, 53, "Thriller");
        Genre mystery = TestFixtures.genre(35L, 9648, "Mystery");
        Genre comedy = TestFixtures.genre(36L, 35, "Comedy");

        when(watchlistRepository.findByUserIdWithDetails(1L)).thenReturn(List.of(olderThriller, newerThriller, comedyEntry));
        when(reviewRepository.findByUserIdWithDetails(1L)).thenReturn(List.of());
        when(movieGenreRepository.findByMovieIdInWithGenre(anyCollection()))
                .thenReturn(List.of(
                        new MovieGenre(thrillerLead, thriller),
                        new MovieGenre(thrillerLead, mystery),
                        new MovieGenre(thrillerFollowUp, thriller),
                        new MovieGenre(thrillerFollowUp, mystery),
                        new MovieGenre(comedyAlternative, comedy)
                ));

        SoloRecommendationRequestDto request = new SoloRecommendationRequestDto();
        request.setMoods(List.of("any"));
        request.setRuntimePreference("any");
        request.setLimit(3);

        List<SoloRecommendationResponseDto> results = recommendationService.getSoloRecommendations(user, request);

        assertEquals(3, results.size());
        assertEquals(1000, results.get(0).getTmdbId());
        assertEquals(1002, results.get(1).getTmdbId());
        assertEquals(1001, results.get(2).getTmdbId());
    }

    @Test
    void recommendationsExcludeWatchedMoviesFromFinalResults() {
        User user = TestFixtures.user(1L, "alice", "alice@example.com");
        Movie watchedMovie = TestFixtures.movie(20L, 220, "Watched Already");
        Movie otherMovie = TestFixtures.movie(21L, 221, "Still Eligible");
        WatchList watchedEntry = TestFixtures.watchList(401L, user, watchedMovie, WatchListStatus.WATCHED);

        Genre thriller = TestFixtures.genre(7L, 53, "Thriller");

        when(watchlistRepository.findByUserIdWithDetails(1L)).thenReturn(List.of(watchedEntry));
        when(watchlistRepository.findMovieIdsByUserIdAndStatus(1L, WatchListStatus.WATCHED)).thenReturn(List.of(20L));
        when(reviewRepository.findByUserIdWithDetails(1L)).thenReturn(List.of());
        when(movieRepository.findRecommendationReadyPopularMovies(anyDouble(), any(Pageable.class)))
                .thenReturn(List.of(watchedMovie, otherMovie));
        when(movieRepository.findRecommendationReadyTopRatedMovies(anyDouble(), any(Pageable.class)))
                .thenReturn(List.of(otherMovie));
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
    void recommendationsSkipIncompleteWatchlistMoviesFromCandidatePool() {
        User user = TestFixtures.user(1L, "alice", "alice@example.com");
        Movie incompleteWatchlistMovie = TestFixtures.movie(40L, 440, "Thin Cache Entry");
        incompleteWatchlistMovie.setRuntime(null);

        Movie readyMovie = TestFixtures.movie(41L, 441, "Catalog Ready");
        readyMovie.setRuntime(118);
        readyMovie.setMovieRating(8.0);
        readyMovie.setPopularity(140.0);

        WatchList watchlistEntry = TestFixtures.watchList(501L, user, incompleteWatchlistMovie, WatchListStatus.PLAN_TO_WATCH);
        Genre drama = TestFixtures.genre(11L, 18, "Drama");

        when(watchlistRepository.findByUserIdWithDetails(1L)).thenReturn(List.of(watchlistEntry));
        when(watchlistRepository.findMovieIdsByUserIdAndStatus(1L, WatchListStatus.WATCHED)).thenReturn(List.of());
        when(reviewRepository.findByUserIdWithDetails(1L)).thenReturn(List.of());
        when(movieRepository.findRecommendationReadyPopularMovies(anyDouble(), any(Pageable.class)))
                .thenReturn(List.of(readyMovie));
        when(movieRepository.findRecommendationReadyTopRatedMovies(anyDouble(), any(Pageable.class)))
                .thenReturn(List.of(readyMovie));
        when(movieGenreRepository.findByMovieIdInWithGenre(anyCollection()))
                .thenReturn(List.of(
                        new MovieGenre(incompleteWatchlistMovie, drama),
                        new MovieGenre(readyMovie, drama)
                ));

        RecommendationRequestDto request = new RecommendationRequestDto(List.of("any"), "any", 5);

        List<RecommendationResponseDto> results = recommendationService.getRecommendations(user, request);

        assertEquals(1, results.size());
        assertEquals(441, results.get(0).getTmdbId());
    }

    @Test
    void coldStartRecommendationsUseMoodAlignedCandidatesForAnonymousUsers() {
        Movie moodMovie = TestFixtures.movie(50L, 550, "Night Tension");
        moodMovie.setRuntime(101);
        moodMovie.setMovieRating(7.9);
        moodMovie.setPopularity(120.0);

        Genre thriller = TestFixtures.genre(12L, 53, "Thriller");

        when(movieGenreRepository.findDistinctRecommendationReadyMoviesByGenreNames(anyCollection(), anyDouble(), any(Pageable.class)))
                .thenReturn(List.of(moodMovie));
        when(movieRepository.findRecommendationReadyPopularMovies(anyDouble(), any(Pageable.class)))
                .thenReturn(List.of());
        when(movieRepository.findRecommendationReadyTopRatedMovies(anyDouble(), any(Pageable.class)))
                .thenReturn(List.of());
        when(movieGenreRepository.findByMovieIdInWithGenre(anyCollection()))
                .thenReturn(List.of(new MovieGenre(moodMovie, thriller)));

        RecommendationRequestDto request = new RecommendationRequestDto(List.of("tense"), "any", 5);

        List<RecommendationResponseDto> results = recommendationService.getColdStartRecommendations(request);

        assertEquals(1, results.size());
        assertEquals(550, results.get(0).getTmdbId());
        assertTrue(results.get(0).getReasons().stream()
                .map(String::toLowerCase)
                .anyMatch(reason -> reason.contains("tense") || reason.contains("vibe")));
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

        when(movieRepository.findRecommendationReadyPopularMovies(anyDouble(), any(Pageable.class)))
                .thenReturn(List.of(popularMovie));
        when(movieRepository.findRecommendationReadyTopRatedMovies(anyDouble(), any(Pageable.class)))
                .thenReturn(List.of(highlyRatedMovie));
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
