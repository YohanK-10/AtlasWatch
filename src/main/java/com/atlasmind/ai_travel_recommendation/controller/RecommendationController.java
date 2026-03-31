package com.atlasmind.ai_travel_recommendation.controller;

import com.atlasmind.ai_travel_recommendation.dto.request.RecommendationRequestDto;
import com.atlasmind.ai_travel_recommendation.dto.request.SoloRecommendationRequestDto;
import com.atlasmind.ai_travel_recommendation.dto.response.RecommendationResponseDto;
import com.atlasmind.ai_travel_recommendation.dto.response.SoloRecommendationResponseDto;
import com.atlasmind.ai_travel_recommendation.models.User;
import com.atlasmind.ai_travel_recommendation.service.RecommendationService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/recommendations")
@RequiredArgsConstructor
@Validated
public class RecommendationController {

    private final RecommendationService recommendationService;

    @PostMapping
    public ResponseEntity<List<RecommendationResponseDto>> getRecommendations(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody RecommendationRequestDto request
    ) {
        return ResponseEntity.ok(recommendationService.getRecommendations(user, request));
    }

    @GetMapping("/cold-start")
    public ResponseEntity<List<RecommendationResponseDto>> getColdStartRecommendations(
            @Valid @ModelAttribute RecommendationRequestDto request
    ) {
        return ResponseEntity.ok(recommendationService.getColdStartRecommendations(request));
    }

    @Deprecated(forRemoval = false)
    @PostMapping("/solo")
    public ResponseEntity<List<SoloRecommendationResponseDto>> getSoloRecommendations(
            @AuthenticationPrincipal User user,
            @RequestBody SoloRecommendationRequestDto request
    ) {
        return ResponseEntity.ok(recommendationService.getSoloRecommendations(user, request));
    }
}
