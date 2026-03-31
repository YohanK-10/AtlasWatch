package com.atlasmind.ai_travel_recommendation.service;

import com.atlasmind.ai_travel_recommendation.models.Review;
import com.atlasmind.ai_travel_recommendation.models.WatchList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class UserTasteProfileService {

    private static final double REVIEW_TEXT_CONFIDENCE_BONUS = 0.25;
    private static final double WATCHLIST_SIGNAL_WEIGHT = 0.45;

    public UserTasteProfile buildProfile(
            List<Review> reviews,
            Collection<WatchList> watchlistEntries,
            Map<Long, List<String>> genresByMovieId
    ) {
        Map<String, Double> positiveSignals = new HashMap<>();
        Map<String, Double> negativeSignals = new HashMap<>();
        int reviewSignalCount = 0;
        int watchlistSignalCount = 0;

        if (reviews != null) {
            for (Review review : reviews) {
                List<String> genres = normalizedGenres(genresByMovieId.getOrDefault(movieId(review), List.of()));
                if (genres.isEmpty()) {
                    continue;
                }

                double signal = reviewSignal(review);
                if (signal > 0) {
                    distributeSignal(positiveSignals, genres, signal);
                    reviewSignalCount++;
                } else if (signal < 0) {
                    distributeSignal(negativeSignals, genres, Math.abs(signal));
                    reviewSignalCount++;
                }
            }
        }

        if (watchlistEntries != null) {
            for (WatchList watchlistEntry : watchlistEntries) {
                List<String> genres = normalizedGenres(genresByMovieId.getOrDefault(movieId(watchlistEntry), List.of()));
                if (genres.isEmpty()) {
                    continue;
                }

                distributeSignal(positiveSignals, genres, WATCHLIST_SIGNAL_WEIGHT);
                watchlistSignalCount++;
            }
        }

        Map<String, Double> positiveGenreWeights = normalizeSignals(positiveSignals);
        Map<String, Double> negativeGenreWeights = normalizeSignals(negativeSignals);
        Map<String, Double> netGenreWeights = buildNetWeights(positiveGenreWeights, negativeGenreWeights);

        return new UserTasteProfile(
                positiveGenreWeights,
                negativeGenreWeights,
                netGenreWeights,
                reviewSignalCount,
                watchlistSignalCount
        );
    }

    private double reviewSignal(Review review) {
        if (review == null || review.getRating() == null) {
            return 0.0;
        }

        double centeredRating = review.getRating() - 5.5;
        if (centeredRating == 0.0) {
            return 0.0;
        }

        double confidenceBonus = hasReviewText(review) ? REVIEW_TEXT_CONFIDENCE_BONUS : 0.0;
        if (centeredRating > 0) {
            return centeredRating + confidenceBonus;
        }

        return centeredRating - (confidenceBonus * 0.5);
    }

    private boolean hasReviewText(Review review) {
        return review.getReviewText() != null && !review.getReviewText().isBlank();
    }

    private void distributeSignal(Map<String, Double> target, List<String> genres, double weight) {
        if (weight <= 0 || genres.isEmpty()) {
            return;
        }

        double perGenreWeight = weight / genres.size();
        for (String genre : genres) {
            target.merge(genre, perGenreWeight, Double::sum);
        }
    }

    private Map<String, Double> normalizeSignals(Map<String, Double> rawSignals) {
        if (rawSignals.isEmpty()) {
            return Map.of();
        }

        double maxWeight = rawSignals.values().stream()
                .mapToDouble(Double::doubleValue)
                .max()
                .orElse(0.0);

        if (maxWeight <= 0) {
            return Map.of();
        }

        return rawSignals.entrySet().stream()
                .collect(LinkedHashMap::new,
                        (map, entry) -> map.put(entry.getKey(), entry.getValue() / maxWeight),
                        LinkedHashMap::putAll);
    }

    private Map<String, Double> buildNetWeights(
            Map<String, Double> positiveGenreWeights,
            Map<String, Double> negativeGenreWeights
    ) {
        Set<String> allGenres = new LinkedHashSet<>();
        allGenres.addAll(positiveGenreWeights.keySet());
        allGenres.addAll(negativeGenreWeights.keySet());

        if (allGenres.isEmpty()) {
            return Map.of();
        }

        Map<String, Double> netWeights = new LinkedHashMap<>();
        for (String genre : allGenres) {
            netWeights.put(
                    genre,
                    positiveGenreWeights.getOrDefault(genre, 0.0) - negativeGenreWeights.getOrDefault(genre, 0.0)
            );
        }
        return netWeights;
    }

    private List<String> normalizedGenres(List<String> genres) {
        if (genres == null || genres.isEmpty()) {
            return List.of();
        }

        return genres.stream()
                .map(this::normalize)
                .filter(genre -> !genre.isBlank())
                .distinct()
                .toList();
    }

    private Long movieId(Review review) {
        return review == null || review.getMovie() == null ? null : review.getMovie().getId();
    }

    private Long movieId(WatchList watchlistEntry) {
        return watchlistEntry == null || watchlistEntry.getMovie() == null ? null : watchlistEntry.getMovie().getId();
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
