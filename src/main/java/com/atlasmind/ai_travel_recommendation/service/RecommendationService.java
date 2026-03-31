package com.atlasmind.ai_travel_recommendation.service;

import com.atlasmind.ai_travel_recommendation.dto.request.RecommendationRequestDto;
import com.atlasmind.ai_travel_recommendation.dto.request.SoloRecommendationRequestDto;
import com.atlasmind.ai_travel_recommendation.dto.response.RecommendationResponseDto;
import com.atlasmind.ai_travel_recommendation.dto.response.SoloRecommendationResponseDto;
import com.atlasmind.ai_travel_recommendation.models.Movie;
import com.atlasmind.ai_travel_recommendation.models.Review;
import com.atlasmind.ai_travel_recommendation.models.User;
import com.atlasmind.ai_travel_recommendation.models.WatchList;
import com.atlasmind.ai_travel_recommendation.models.WatchListStatus;
import com.atlasmind.ai_travel_recommendation.repository.MovieGenreRepository;
import com.atlasmind.ai_travel_recommendation.repository.MovieRepository;
import com.atlasmind.ai_travel_recommendation.repository.ReviewRepository;
import com.atlasmind.ai_travel_recommendation.repository.WatchlistRepository;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RecommendationService {

    private final WatchlistRepository watchlistRepository;
    private final ReviewRepository reviewRepository;
    private final MovieGenreRepository movieGenreRepository;
    private final MovieRepository movieRepository;
    private final MovieService movieService;

    private static final int DEFAULT_LIMIT = 5;
    private static final int MAX_LIMIT = 10;
    private static final int FAVORITE_REVIEW_THRESHOLD = 8;
    private static final int CATALOG_SEED_THRESHOLD = 40;
    private static final int MIN_CANDIDATE_POOL = 36;
    private static final int MAX_TOP_GENRES = 4;

    @Transactional(readOnly = true)
    public List<SoloRecommendationResponseDto> getSoloRecommendations(
            User user,
            SoloRecommendationRequestDto request
    ) {
        Set<SoloMood> moods = SoloMood.from(
                request != null ? request.getMoods() : null,
                request != null ? request.getMood() : null
        );
        RuntimePreference runtimePreference = RuntimePreference.from(
                request != null ? request.getRuntimePreference() : null
        );
        int limit = normalizeLimit(request != null ? request.getLimit() : null);

        List<WatchList> candidates = watchlistRepository.findByUserIdWithDetails(user.getId())
                .stream()
                .filter(this::isActiveWatchlistEntry)
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
                .map(entry -> scoreWatchlistEntry(entry, moods, runtimePreference, favoriteGenres, genresByMovieId))
                .sorted(Comparator
                        .comparingInt(WatchlistRecommendation::score).reversed()
                        .thenComparing((WatchlistRecommendation rec) -> rec.movie().getMovieRating(),
                                Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(rec -> rec.entry().getAddedAt(),
                                Comparator.nullsLast(Comparator.naturalOrder())))
                .limit(limit)
                .map(this::toSoloResponse)
                .toList();
    }

    @Transactional
    public List<RecommendationResponseDto> getRecommendations(User user, RecommendationRequestDto request) {
        seedCatalogIfNeeded();
        RecommendationContext context = buildRecommendationContext(user);
        return buildCatalogRecommendations(request, context);
    }

    @Transactional
    public List<RecommendationResponseDto> getColdStartRecommendations(RecommendationRequestDto request) {
        seedCatalogIfNeeded();
        return buildCatalogRecommendations(request, RecommendationContext.createColdStart());
    }

    private RecommendationContext buildRecommendationContext(User user) {
        List<WatchList> watchlistEntries = watchlistRepository.findByUserIdWithDetails(user.getId());
        List<Review> reviews = reviewRepository.findByUserIdWithDetails(user.getId());

        Map<Long, WatchList> activeWatchlistByMovieId = watchlistEntries.stream()
                .filter(this::isActiveWatchlistEntry)
                .collect(Collectors.toMap(
                        entry -> entry.getMovie().getId(),
                        entry -> entry,
                        (left, right) -> left,
                        LinkedHashMap::new
                ));

        Set<Long> watchedMovieIds = watchlistEntries.stream()
                .filter(entry -> entry.getStatus() == WatchListStatus.WATCHED)
                .map(entry -> entry.getMovie().getId())
                .collect(Collectors.toCollection(LinkedHashSet::new));

        Set<Long> profileMovieIds = new LinkedHashSet<>(activeWatchlistByMovieId.keySet());
        reviews.stream()
                .map(review -> review.getMovie().getId())
                .forEach(profileMovieIds::add);

        Map<Long, List<String>> genresByMovieId = buildGenresByMovieId(profileMovieIds);
        Map<String, Integer> favoriteGenres = buildGenreAffinity(reviews, activeWatchlistByMovieId.values(), genresByMovieId);
        boolean coldStart = reviews.isEmpty() && activeWatchlistByMovieId.isEmpty();

        return new RecommendationContext(activeWatchlistByMovieId, watchedMovieIds, favoriteGenres, coldStart);
    }

    private List<RecommendationResponseDto> buildCatalogRecommendations(
            RecommendationRequestDto request,
            RecommendationContext context
    ) {
        Set<SoloMood> moods = SoloMood.from(request != null ? request.getMoods() : null, null);
        RuntimePreference runtimePreference = RuntimePreference.from(
                request != null ? request.getRuntimePreference() : null
        );
        int limit = normalizeLimit(request != null ? request.getLimit() : null);

        List<Movie> candidates = buildCandidatePool(context, limit);
        if (candidates.isEmpty()) {
            return List.of();
        }

        Map<Long, List<String>> genresByMovieId = buildGenresByMovieId(
                candidates.stream().map(Movie::getId).toList()
        );

        return candidates.stream()
                .filter(movie -> !context.watchedMovieIds().contains(movie.getId()))
                .map(movie -> scoreCatalogMovie(movie, moods, runtimePreference, context, genresByMovieId))
                .sorted(Comparator
                        .comparingInt(CatalogRecommendation::score).reversed()
                        .thenComparing((CatalogRecommendation rec) -> rec.movie().getMovieRating(),
                                Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing((CatalogRecommendation rec) -> rec.movie().getPopularity(),
                                Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(rec -> rec.movie().getMovieTitle(), Comparator.nullsLast(String::compareToIgnoreCase)))
                .limit(limit)
                .map(this::toRecommendationResponse)
                .toList();
    }

    private List<Movie> buildCandidatePool(RecommendationContext context, int limit) {
        int candidatePoolSize = Math.max(MIN_CANDIDATE_POOL, limit * 6);
        LinkedHashMap<Long, Movie> candidates = new LinkedHashMap<>();

        context.watchlistByMovieId().values().stream()
                .map(WatchList::getMovie)
                .forEach(movie -> candidates.putIfAbsent(movie.getId(), movie));

        List<String> topGenres = context.favoriteGenres().entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(MAX_TOP_GENRES)
                .map(Map.Entry::getKey)
                .toList();

        if (!topGenres.isEmpty()) {
            movieGenreRepository.findDistinctMoviesByGenreNames(topGenres, PageRequest.of(0, candidatePoolSize))
                    .forEach(movie -> candidates.putIfAbsent(movie.getId(), movie));
        }

        movieRepository.findTopPopularMovies(PageRequest.of(0, candidatePoolSize))
                .forEach(movie -> candidates.putIfAbsent(movie.getId(), movie));
        movieRepository.findTopRatedMovies(PageRequest.of(0, candidatePoolSize))
                .forEach(movie -> candidates.putIfAbsent(movie.getId(), movie));

        return candidates.values().stream()
                .filter(movie -> movie.getId() != null)
                .toList();
    }

    private WatchlistRecommendation scoreWatchlistEntry(
            WatchList entry,
            Set<SoloMood> moods,
            RuntimePreference runtimePreference,
            Map<String, Integer> favoriteGenres,
            Map<Long, List<String>> genresByMovieId
    ) {
        Movie movie = entry.getMovie();
        List<String> genres = genresByMovieId.getOrDefault(movie.getId(), List.of());

        int score = 8;
        List<String> reasons = new ArrayList<>();

        score += applyWatchlistAgeBonus(entry, reasons);
        score += applyMoodBonus(moods, genres, reasons);
        score += applyRuntimeBonus(runtimePreference, movie.getRuntime(), reasons);
        score += applyFavoriteGenreBonus(favoriteGenres, genres, reasons);
        score += applyCatalogQualityBonus(movie, false, reasons);

        if (reasons.isEmpty()) {
            reasons.add("It is still one of the strongest unfinished options in your current watchlist.");
        }

        return new WatchlistRecommendation(entry, genres, score, dedupeReasons(reasons));
    }

    private CatalogRecommendation scoreCatalogMovie(
            Movie movie,
            Set<SoloMood> moods,
            RuntimePreference runtimePreference,
            RecommendationContext context,
            Map<Long, List<String>> genresByMovieId
    ) {
        List<String> genres = genresByMovieId.getOrDefault(movie.getId(), List.of());
        WatchList watchlistEntry = context.watchlistByMovieId().get(movie.getId());

        int score = 0;
        List<String> reasons = new ArrayList<>();

        score += applyMoodBonus(moods, genres, reasons);
        score += applyRuntimeBonus(runtimePreference, movie.getRuntime(), reasons);
        score += applyWatchlistBoost(watchlistEntry, reasons);
        score += applyFavoriteGenreBonus(context.favoriteGenres(), genres, reasons);
        score += applyCatalogQualityBonus(movie, context.coldStart(), reasons);

        if (reasons.isEmpty()) {
            if (context.coldStart()) {
                reasons.add("It is a strong wider-catalog pick while AtlasWatch learns your taste.");
            } else {
                reasons.add("It fits the strongest combination of mood, runtime, and taste signals available right now.");
            }
        }

        return new CatalogRecommendation(
                movie,
                genres,
                score,
                watchlistEntry != null,
                normalizeWatchlistStatus(watchlistEntry),
                dedupeReasons(reasons)
        );
    }

    private int applyWatchlistBoost(WatchList watchlistEntry, List<String> reasons) {
        if (watchlistEntry == null) {
            return 0;
        }

        reasons.add("It is already on your watchlist, so this lines up with something you were already curious about.");
        return 10;
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

        reasons.add("It matches your " + humanizeLabels(moodLabels) + " vibe mix through " + humanizeGenres(matches) + ".");
        return 18 + Math.min(12, matches.size() * 3) + Math.min(6, Math.max(0, moodLabels.size() - 1) * 2);
    }

    private int applyRuntimeBonus(RuntimePreference runtimePreference, Integer runtime, List<String> reasons) {
        if (runtimePreference == RuntimePreference.ANY || runtime == null) {
            return 0;
        }
        if (!runtimePreference.matches(runtime)) {
            return 0;
        }

        reasons.add("Its runtime fits your " + runtimePreference.label + " preference.");
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
                .mapToInt(match -> Math.min(8, favoriteGenres.get(match) * 2))
                .sum();
        bonus = Math.min(20, bonus);

        reasons.add("It lines up with genres you tend to rate highly, like " + humanizeGenres(matches) + ".");
        return bonus;
    }

    private int applyCatalogQualityBonus(Movie movie, boolean coldStart, List<String> reasons) {
        double bonus = 0;

        if (movie.getMovieRating() != null) {
            if (movie.getMovieRating() >= 8.5) {
                bonus += 10;
                reasons.add(coldStart
                        ? "It has one of the stronger audience ratings in the wider catalog."
                        : "It also stands out as one of the stronger-rated matches here.");
            } else if (movie.getMovieRating() >= 7.5) {
                bonus += 6;
            } else if (movie.getMovieRating() >= 7.0) {
                bonus += 3;
            }
        }

        if (movie.getPopularity() != null) {
            if (movie.getPopularity() >= 150) {
                bonus += 6;
                reasons.add(coldStart
                        ? "It is also one of the more popular catalog options right now."
                        : "It has enough popularity to make it a safer all-around pick.");
            } else if (movie.getPopularity() >= 80) {
                bonus += 3;
            }
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

    private Map<String, Integer> buildGenreAffinity(
            List<Review> reviews,
            Collection<WatchList> watchlistEntries,
            Map<Long, List<String>> genresByMovieId
    ) {
        Map<String, Integer> affinity = new HashMap<>();

        for (Review review : reviews) {
            Integer rating = review.getRating();
            if (rating == null || rating < 6) {
                continue;
            }

            int weight = rating >= FAVORITE_REVIEW_THRESHOLD ? 3 : 1;
            if (review.getReviewText() != null && !review.getReviewText().isBlank()) {
                weight += 1;
            }

            for (String genre : genresByMovieId.getOrDefault(review.getMovie().getId(), List.of())) {
                affinity.merge(normalize(genre), weight, Integer::sum);
            }
        }

        for (WatchList entry : watchlistEntries) {
            for (String genre : genresByMovieId.getOrDefault(entry.getMovie().getId(), List.of())) {
                affinity.merge(normalize(genre), 1, Integer::sum);
            }
        }

        return affinity;
    }

    private SoloRecommendationResponseDto toSoloResponse(WatchlistRecommendation recommendation) {
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
                .watchlistStatus(normalizeWatchlistStatus(entry))
                .addedAt(entry.getAddedAt())
                .score(recommendation.score())
                .reasons(recommendation.reasons())
                .build();
    }

    private RecommendationResponseDto toRecommendationResponse(CatalogRecommendation recommendation) {
        Movie movie = recommendation.movie();

        return RecommendationResponseDto.builder()
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
                .onWatchlist(recommendation.onWatchlist())
                .watchlistStatus(recommendation.watchlistStatus())
                .reasons(recommendation.reasons())
                .build();
    }

    private boolean isActiveWatchlistEntry(WatchList entry) {
        return entry.getStatus() != null && entry.getStatus() != WatchListStatus.WATCHED;
    }

    private String normalizeWatchlistStatus(WatchList entry) {
        if (entry == null || entry.getStatus() == null) {
            return null;
        }

        if (entry.getStatus() == WatchListStatus.WATCHING) {
            return WatchListStatus.PLAN_TO_WATCH.name();
        }

        return entry.getStatus().name();
    }

    private List<String> dedupeReasons(List<String> reasons) {
        return new ArrayList<>(new LinkedHashSet<>(reasons)).stream()
                .limit(3)
                .toList();
    }

    private void seedCatalogIfNeeded() {
        if (movieRepository.count() >= CATALOG_SEED_THRESHOLD) {
            return;
        }

        try {
            movieService.getTrendingMovies();
        } catch (RuntimeException ignored) {
            // If TMDB seeding fails, we still rank whatever is already cached locally.
        }
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

    private record WatchlistRecommendation(
            WatchList entry,
            List<String> genres,
            int score,
            List<String> reasons
    ) {
        private Movie movie() {
            return entry.getMovie();
        }
    }

    private record CatalogRecommendation(
            Movie movie,
            List<String> genres,
            int score,
            boolean onWatchlist,
            String watchlistStatus,
            List<String> reasons
    ) {
    }

    private record RecommendationContext(
            Map<Long, WatchList> watchlistByMovieId,
            Set<Long> watchedMovieIds,
            Map<String, Integer> favoriteGenres,
            boolean coldStart
    ) {
        private static RecommendationContext createColdStart() {
            return new RecommendationContext(Map.of(), Set.of(), Map.of(), true);
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
                throw new IllegalArgumentException("Invalid mood: '" + value + "'.");
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
