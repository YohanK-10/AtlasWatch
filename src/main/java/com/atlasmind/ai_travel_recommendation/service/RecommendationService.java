package com.atlasmind.ai_travel_recommendation.service;

import com.atlasmind.ai_travel_recommendation.dto.request.SoloRecommendationRequestDto;
import com.atlasmind.ai_travel_recommendation.dto.response.SoloRecommendationResponseDto;
import com.atlasmind.ai_travel_recommendation.models.Movie;
import com.atlasmind.ai_travel_recommendation.models.MovieGenre;
import com.atlasmind.ai_travel_recommendation.models.Review;
import com.atlasmind.ai_travel_recommendation.models.User;
import com.atlasmind.ai_travel_recommendation.models.WatchList;
import com.atlasmind.ai_travel_recommendation.models.WatchListStatus;
import com.atlasmind.ai_travel_recommendation.repository.MovieGenreRepository;
import com.atlasmind.ai_travel_recommendation.repository.ReviewRepository;
import com.atlasmind.ai_travel_recommendation.repository.WatchlistRepository;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RecommendationService {

    private final WatchlistRepository watchlistRepository;
    private final ReviewRepository reviewRepository;
    private final MovieGenreRepository movieGenreRepository;

    private static final int DEFAULT_LIMIT = 5;
    private static final int MAX_LIMIT = 10;
    private static final int FAVORITE_REVIEW_THRESHOLD = 8;

    @Transactional(readOnly = true)
    public List<SoloRecommendationResponseDto> getSoloRecommendations(
            User user,
            SoloRecommendationRequestDto request
    ) {
        Set<SoloMood> moods = SoloMood.from(request != null ? request.getMoods() : null,
                request != null ? request.getMood() : null);
        RuntimePreference runtimePreference = RuntimePreference.from(
                request != null ? request.getRuntimePreference() : null
        );
        int limit = normalizeLimit(request != null ? request.getLimit() : null);

        List<WatchList> candidates = watchlistRepository.findByUserIdWithDetails(user.getId())
                .stream()
                .filter(entry -> entry.getStatus() != WatchListStatus.WATCHED)
                .toList();

        if (candidates.isEmpty()) {
            return List.of();
        }

        List<Review> userReviews = reviewRepository.findByUserIdWithDetails(user.getId());

        Set<Long> movieIds = candidates.stream()
                .map(entry -> entry.getMovie().getId())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        userReviews.stream()
                .map(review -> review.getMovie().getId())
                .forEach(movieIds::add);

        Map<Long, List<String>> genresByMovieId = buildGenresByMovieId(movieIds);
        Map<String, Integer> favoriteGenres = buildFavoriteGenres(userReviews, genresByMovieId);

        return candidates.stream()
                .map(entry -> scoreEntry(entry, moods, runtimePreference, favoriteGenres, genresByMovieId))
                .sorted(Comparator
                        .comparingInt(RecommendationScore::score).reversed()
                        .thenComparing((RecommendationScore rec) -> rec.movie().getMovieRating(),
                                Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(rec -> rec.entry().getAddedAt(),
                                Comparator.nullsLast(Comparator.naturalOrder())))
                .limit(limit)
                .map(this::toResponse)
                .toList();
    }

    private RecommendationScore scoreEntry(
            WatchList entry,
            Set<SoloMood> moods,
            RuntimePreference runtimePreference,
            Map<String, Integer> favoriteGenres,
            Map<Long, List<String>> genresByMovieId
    ) {
        Movie movie = entry.getMovie();
        List<String> genres = genresByMovieId.getOrDefault(movie.getId(), List.of());

        int score = 0;
        List<String> reasons = new ArrayList<>();

        if (entry.getStatus() == WatchListStatus.PLAN_TO_WATCH
                || entry.getStatus() == WatchListStatus.WATCHING) {
            score += 8;
        }

        score += applyWatchlistAgeBonus(entry, reasons);
        score += applyMoodBonus(moods, genres, reasons);
        score += applyRuntimeBonus(runtimePreference, movie.getRuntime(), reasons);
        score += applyFavoriteGenreBonus(favoriteGenres, genres, reasons);
        score += applyQualityBonus(movie, reasons);

        if (reasons.isEmpty()) {
            reasons.add("It is still one of the strongest unfinished options in your current watchlist.");
        }

        return new RecommendationScore(entry, genres, score, dedupeReasons(reasons));
    }

    private int applyWatchlistAgeBonus(WatchList entry, List<String> reasons) {
        LocalDateTime addedAt = entry.getAddedAt();
        if (addedAt == null) {
            return 0;
        }

        long days = Duration.between(addedAt, LocalDateTime.now()).toDays();
        if (days >= 180) {
            reasons.add("It has been sitting in your watchlist for a while, so this is a good time to finally watch it.");
            return 15;
        }
        if (days >= 60) {
            reasons.add("It has been on your watchlist long enough to deserve a bump.");
            return 9;
        }
        if (days >= 14) {
            return 4;
        }
        return 0;
    }

    private int applyMoodBonus(Set<SoloMood> moods, List<String> genres, List<String> reasons) {
        if (moods.isEmpty() || (moods.size() == 1 && moods.contains(SoloMood.ANY))) {
            return 0;
        }

        Set<String> preferredGenres = moods.stream()
                .filter(mood -> mood != SoloMood.ANY)
                .map(SoloMood::preferredGenres)
                .flatMap(Set::stream)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        List<String> matches = genres.stream()
                .map(this::normalize)
                .filter(preferredGenres::contains)
                .distinct()
                .toList();

        if (matches.isEmpty()) {
            return 0;
        }

        List<String> moodLabels = moods.stream()
                .filter(mood -> mood != SoloMood.ANY)
                .map(SoloMood::displayLabel)
                .toList();

        reasons.add("It matches your " + humanizeLabels(moodLabels) + " mood mix through " + humanizeGenres(matches) + ".");
        return 18 + Math.min(12, matches.size() * 3) + Math.min(6, Math.max(0, moodLabels.size() - 1) * 2);
    }

    private int applyRuntimeBonus(
            RuntimePreference runtimePreference,
            Integer runtime,
            List<String> reasons
    ) {
        if (runtimePreference == RuntimePreference.ANY || runtime == null) {
            return 0;
        }
        if (!runtimePreference.matches(runtime)) {
            return 0;
        }

        reasons.add("Its runtime fits your " + runtimePreference.label + " pick.");
        return 18;
    }

    private int applyFavoriteGenreBonus(
            Map<String, Integer> favoriteGenres,
            List<String> genres,
            List<String> reasons
    ) {
        if (favoriteGenres.isEmpty() || genres.isEmpty()) {
            return 0;
        }

        List<String> matches = genres.stream()
                .map(this::normalize)
                .filter(favoriteGenres::containsKey)
                .distinct()
                .toList();

        if (matches.isEmpty()) {
            return 0;
        }

        int bonus = matches.stream()
                .mapToInt(match -> Math.min(6, favoriteGenres.get(match) * 2))
                .sum();
        bonus = Math.min(18, bonus);

        reasons.add("It lines up with genres you tend to rate highly, like " + humanizeGenres(matches) + ".");
        return bonus;
    }

    private int applyQualityBonus(Movie movie, List<String> reasons) {
        double bonus = 0;

        if (movie.getMovieRating() != null) {
            if (movie.getMovieRating() >= 8.0) {
                bonus += 6;
                reasons.add("It is also one of the stronger-rated options in your list.");
            } else if (movie.getMovieRating() >= 7.0) {
                bonus += 3;
            }
        }

        if (movie.getPopularity() != null && movie.getPopularity() >= 100) {
            bonus += 2;
        }

        return (int) bonus;
    }

    private Map<Long, List<String>> buildGenresByMovieId(Collection<Long> movieIds) {
        if (movieIds.isEmpty()) {
            return Collections.emptyMap();
        }

        return movieGenreRepository.findByMovieIdInWithGenre(movieIds)
                .stream()
                .collect(Collectors.groupingBy(
                        mg -> mg.getMovie().getId(),
                        Collectors.mapping(mg -> mg.getGenre().getName(), Collectors.toList())
                ));
    }

    private Map<String, Integer> buildFavoriteGenres(
            List<Review> reviews,
            Map<Long, List<String>> genresByMovieId
    ) {
        Map<String, Integer> favoriteGenres = new HashMap<>();

        reviews.stream()
                .filter(review -> review.getRating() != null && review.getRating() >= FAVORITE_REVIEW_THRESHOLD)
                .forEach(review -> genresByMovieId.getOrDefault(review.getMovie().getId(), List.of())
                        .forEach(genre -> favoriteGenres.merge(normalize(genre), 1, Integer::sum)));

        return favoriteGenres;
    }

    private SoloRecommendationResponseDto toResponse(RecommendationScore recommendation) {
        WatchList entry = recommendation.entry();
        Movie movie = entry.getMovie();

        return SoloRecommendationResponseDto.builder()
                .tmdbId(movie.getTmdbId())
                .movieTitle(movie.getMovieTitle())
                .movieOverview(movie.getOverview())
                .posterPath(movie.getPosterPath())
                .backdropPath(movie.getBackdropPath())
                .releaseDate(movie.getReleaseDate())
                .rating(movie.getMovieRating())
                .runtime(movie.getRuntime())
                .popularity(movie.getPopularity())
                .genres(recommendation.genres())
                .watchlistStatus(entry.getStatus().name())
                .addedAt(entry.getAddedAt())
                .score(recommendation.score())
                .reasons(recommendation.reasons())
                .build();
    }

    private List<String> dedupeReasons(List<String> reasons) {
        return new ArrayList<>(new LinkedHashSet<>(reasons)).stream()
                .limit(3)
                .toList();
    }

    private String humanizeGenres(List<String> genres) {
        return genres.stream()
                .map(genre -> genre.substring(0, 1).toUpperCase(Locale.ROOT) + genre.substring(1))
                .collect(Collectors.joining(", "));
    }

    private String humanizeLabels(List<String> labels) {
        return String.join(", ", labels);
    }

    private int normalizeLimit(Integer requestedLimit) {
        if (requestedLimit == null) {
            return DEFAULT_LIMIT;
        }
        if (requestedLimit < 1) {
            throw new IllegalArgumentException("Recommendation limit must be at least 1.");
        }
        return Math.min(requestedLimit, MAX_LIMIT);
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private record RecommendationScore(
            WatchList entry,
            List<String> genres,
            int score,
            List<String> reasons
    ) {
        private Movie movie() {
            return entry.getMovie();
        }
    }

    private enum SoloMood {
        ANY("any", Set.of()),
        COMFORTING("comforting", Set.of("comedy", "family", "romance", "animation")),
        FUNNY("funny", Set.of("comedy", "animation")),
        TENSE("tense", Set.of("thriller", "mystery", "crime", "action")),
        DARK("dark", Set.of("thriller", "horror", "crime", "drama")),
        EMOTIONAL("emotional", Set.of("drama", "romance")),
        THOUGHTFUL("thoughtful", Set.of("drama", "science fiction", "history")),
        ADVENTUROUS("adventurous", Set.of("adventure", "fantasy", "action", "science fiction")),
        COZY("cozy", Set.of("family", "romance", "comedy", "animation")),
        ROMANTIC("romantic", Set.of("romance", "drama", "comedy")),
        EERIE("eerie", Set.of("horror", "mystery", "thriller")),
        HOPEFUL("hopeful", Set.of("family", "adventure", "drama", "animation")),
        BITTERSWEET("bittersweet", Set.of("drama", "romance", "music")),
        MIND_BENDING("mind bending", Set.of("science fiction", "mystery", "thriller")),
        INSPIRING("inspiring", Set.of("history", "drama", "music", "adventure"));

        private static final Map<String, SoloMood> LOOKUP = new HashMap<>();

        static {
            for (SoloMood mood : values()) {
                LOOKUP.put(mood.label, mood);
            }
        }

        private final String label;
        private final Set<String> preferredGenres;

        SoloMood(String label, Set<String> preferredGenres) {
            this.label = label;
            this.preferredGenres = preferredGenres;
        }

        private String displayLabel() {
            return label.substring(0, 1).toUpperCase(Locale.ROOT) + label.substring(1);
        }

        private Set<String> preferredGenres() {
            return preferredGenres;
        }

        private static Set<SoloMood> from(List<String> values, String fallbackValue) {
            List<String> rawValues = values == null || values.isEmpty()
                    ? (fallbackValue == null || fallbackValue.isBlank() ? List.of("any") : List.of(fallbackValue))
                    : values;

            Set<SoloMood> resolved = rawValues.stream()
                    .map(SoloMood::fromSingle)
                    .collect(Collectors.toCollection(LinkedHashSet::new));

            if (resolved.isEmpty()) {
                return Set.of(ANY);
            }

            if (resolved.size() > 1) {
                resolved.remove(ANY);
            }

            return resolved.isEmpty() ? Set.of(ANY) : resolved;
        }

        private static SoloMood fromSingle(String value) {
            if (value == null || value.isBlank()) {
                return ANY;
            }

            String normalized = value.trim().toLowerCase(Locale.ROOT).replace('_', ' ').replace('-', ' ');
            SoloMood mood = LOOKUP.get(normalized);
            if (mood == null) {
                throw new IllegalArgumentException(
                        "Invalid mood: '" + value + "'."
                );
            }
            return mood;
        }
    }

    private enum RuntimePreference {
        ANY("any", runtime -> true),
        SHORT("short", runtime -> runtime <= 105),
        MEDIUM("medium", runtime -> runtime > 105 && runtime <= 135),
        LONG("long", runtime -> runtime > 135);

        private static final Map<String, RuntimePreference> LOOKUP = new HashMap<>();

        static {
            for (RuntimePreference preference : values()) {
                LOOKUP.put(preference.label, preference);
            }
        }

        private final String label;
        private final Predicate<Integer> matcher;

        RuntimePreference(String label, Predicate<Integer> matcher) {
            this.label = label;
            this.matcher = matcher;
        }

        private boolean matches(int runtime) {
            return matcher.test(runtime);
        }

        private static RuntimePreference from(String value) {
            if (value == null || value.isBlank()) {
                return ANY;
            }
            String normalized = value.trim().toLowerCase(Locale.ROOT).replace('_', ' ').replace('-', ' ');
            RuntimePreference preference = LOOKUP.get(normalized);
            if (preference == null) {
                throw new IllegalArgumentException(
                        "Invalid runtimePreference: '" + value + "'. Must be any, short, medium, or long."
                );
            }
            return preference;
        }
    }
}
