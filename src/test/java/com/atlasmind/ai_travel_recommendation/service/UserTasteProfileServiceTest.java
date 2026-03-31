package com.atlasmind.ai_travel_recommendation.service;

import com.atlasmind.ai_travel_recommendation.models.MovieGenre;
import com.atlasmind.ai_travel_recommendation.support.TestFixtures;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserTasteProfileServiceTest {

    private final UserTasteProfileService userTasteProfileService = new UserTasteProfileService();

    @Test
    void buildProfileCombinesPositiveNegativeAndWatchlistSignals() {
        var user = TestFixtures.user(1L, "alice", "alice@example.com");

        var lovedMovie = TestFixtures.movie(10L, 110, "Loved Thriller");
        var dislikedMovie = TestFixtures.movie(11L, 111, "Disliked Romance");
        var watchlistMovie = TestFixtures.movie(12L, 112, "Queued Sci-Fi");

        var thriller = TestFixtures.genre(1L, 53, "Thriller");
        var mystery = TestFixtures.genre(2L, 9648, "Mystery");
        var romance = TestFixtures.genre(3L, 10749, "Romance");
        var scienceFiction = TestFixtures.genre(4L, 878, "Science Fiction");

        var positiveReview = TestFixtures.review(20L, user, lovedMovie);
        positiveReview.setRating(9);
        positiveReview.setReviewText("Smart and gripping.");

        var negativeReview = TestFixtures.review(21L, user, dislikedMovie);
        negativeReview.setRating(3);
        negativeReview.setReviewText("Not for me.");

        var watchlistEntry = TestFixtures.watchList(30L, user, watchlistMovie,
                com.atlasmind.ai_travel_recommendation.models.WatchListStatus.PLAN_TO_WATCH);

        Map<Long, List<String>> genresByMovieId = genresByMovieId(
                new MovieGenre(lovedMovie, thriller),
                new MovieGenre(lovedMovie, mystery),
                new MovieGenre(dislikedMovie, romance),
                new MovieGenre(watchlistMovie, scienceFiction)
        );

        UserTasteProfile profile = userTasteProfileService.buildProfile(
                List.of(positiveReview, negativeReview),
                List.of(watchlistEntry),
                genresByMovieId
        );

        assertFalse(profile.isColdStart());
        assertEquals(2, profile.reviewSignalCount());
        assertEquals(1, profile.watchlistSignalCount());
        assertTrue(profile.positiveWeight("thriller") > profile.positiveWeight("science fiction"));
        assertTrue(profile.negativeWeight("romance") > 0.0);
        assertTrue(profile.netWeight("thriller") > 0.0);
        assertTrue(profile.netWeight("romance") < 0.0);
        assertTrue(profile.topPositiveGenres(2).contains("thriller"));
        assertTrue(profile.positiveGenreWeights().values().stream().allMatch(value -> value >= 0.0 && value <= 1.0));
        assertTrue(profile.negativeGenreWeights().values().stream().allMatch(value -> value >= 0.0 && value <= 1.0));
    }

    @Test
    void buildProfileReturnsColdStartWithoutUsableSignals() {
        UserTasteProfile profile = userTasteProfileService.buildProfile(List.of(), List.of(), Map.of());

        assertTrue(profile.isColdStart());
        assertFalse(profile.hasSignals());
        assertTrue(profile.positiveGenreWeights().isEmpty());
        assertTrue(profile.negativeGenreWeights().isEmpty());
        assertTrue(profile.netGenreWeights().isEmpty());
    }

    private Map<Long, List<String>> genresByMovieId(MovieGenre... movieGenres) {
        Map<Long, List<String>> genresByMovieId = new LinkedHashMap<>();
        for (MovieGenre movieGenre : movieGenres) {
            genresByMovieId.computeIfAbsent(movieGenre.getMovie().getId(), ignored -> new java.util.ArrayList<>())
                    .add(movieGenre.getGenre().getName());
        }
        return genresByMovieId;
    }
}
