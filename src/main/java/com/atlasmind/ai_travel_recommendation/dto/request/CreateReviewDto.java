package com.atlasmind.ai_travel_recommendation.dto.request;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * What the client sends when creating or updating a review.
 *
 * Why no userId field? Because the user is identified from the JWT token
 * in the request cookie — the client can't choose who the review belongs to.
 * This prevents a user from creating reviews on behalf of someone else.
 *
 * Why tmdbId and not our internal movie id? Because the frontend only
 * knows about TMDB IDs (that's what we expose in MovieResponseDto).
 * The service layer resolves tmdbId → internal Movie entity.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CreateReviewDto {
    private Integer tmdbId;
    private Integer rating;
    private String reviewText;
    private Boolean containsSpoilers;
}