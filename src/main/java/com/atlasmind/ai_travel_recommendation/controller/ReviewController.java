package com.atlasmind.ai_travel_recommendation.controller;

import com.atlasmind.ai_travel_recommendation.dto.request.CreateReviewDto;
import com.atlasmind.ai_travel_recommendation.dto.response.ReviewResponseDto;
import com.atlasmind.ai_travel_recommendation.models.User;
import com.atlasmind.ai_travel_recommendation.service.ReviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST endpoints for the review system.
 * All endpoints except the GET (read) endpoints require authentication.
 *
 * @AuthenticationPrincipal is how we get the logged-in user. Spring Security
 * populates this from the JWT token that was validated by JwtAuthFilter.
 * Your User entity implements UserDetails, so Spring can inject it directly.
 * No need to manually parse the JWT or query the database for the user.
 */
@RestController
@RequestMapping("/api/reviews")
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;

    /**
     * POST /api/reviews
     *
     * Create a new review. Requires authentication.
     * The user is identified from the JWT cookie — the client
     * never sends a userId in the body.
     */
    @PostMapping
    public ResponseEntity<ReviewResponseDto> createReview(
            @AuthenticationPrincipal User user,
            @RequestBody CreateReviewDto dto) {

        ReviewResponseDto review = reviewService.createReview(user, dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(review);
    }

    /**
     * GET /api/reviews/movie/{tmdbId}
     *
     * Get all reviews for a movie. Public endpoint — anyone can
     * read reviews, you don't need to be logged in.
     */
    @GetMapping("/movie/{tmdbId}")
    public ResponseEntity<List<ReviewResponseDto>> getReviewsByMovie(
            @PathVariable Integer tmdbId) {

        return ResponseEntity.ok(reviewService.getReviewsByMovie(tmdbId));
    }

    /**
     * GET /api/reviews/user/{userId}
     *
     * Get all reviews by a specific user. Public endpoint.
     */
    @GetMapping("/user/{userId}")
    public ResponseEntity<List<ReviewResponseDto>> getReviewsByUser(
            @PathVariable Long userId) {

        return ResponseEntity.ok(reviewService.getReviewsByUser(userId));
    }

    /**
     * PUT /api/reviews/{reviewId}
     *
     * Update an existing review. Only the review owner can do this.
     * The service layer checks ownership and throws 403 if violated.
     */
    @PutMapping("/{reviewId}")
    public ResponseEntity<ReviewResponseDto> updateReview(
            @AuthenticationPrincipal User user,
            @PathVariable Long reviewId,
            @RequestBody CreateReviewDto dto) {

        return ResponseEntity.ok(reviewService.updateReview(user, reviewId, dto));
    }

    /**
     * DELETE /api/reviews/{reviewId}
     *
     * Delete a review. Only the review owner can do this.
     */
    @DeleteMapping("/{reviewId}")
    public ResponseEntity<Void> deleteReview(
            @AuthenticationPrincipal User user,
            @PathVariable Long reviewId) {

        reviewService.deleteReview(user, reviewId);
        return ResponseEntity.noContent().build();
    }
}