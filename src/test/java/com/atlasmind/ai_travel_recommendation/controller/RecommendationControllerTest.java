package com.atlasmind.ai_travel_recommendation.controller;

import com.atlasmind.ai_travel_recommendation.dto.request.SoloRecommendationRequestDto;
import com.atlasmind.ai_travel_recommendation.dto.response.SoloRecommendationResponseDto;
import com.atlasmind.ai_travel_recommendation.models.User;
import com.atlasmind.ai_travel_recommendation.service.RecommendationService;
import com.atlasmind.ai_travel_recommendation.support.TestFixtures;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecommendationControllerTest {

    @Mock
    private RecommendationService recommendationService;

    @InjectMocks
    private RecommendationController recommendationController;

    @Test
    void getSoloRecommendationsReturnsOkResponse() {
        User user = TestFixtures.user(1L, "alice", "alice@example.com");
        SoloRecommendationRequestDto request = new SoloRecommendationRequestDto();
        request.setMoods(List.of("tense"));
        request.setRuntimePreference("short");
        request.setLimit(3);
        SoloRecommendationResponseDto recommendation = SoloRecommendationResponseDto.builder()
                .tmdbId(27205)
                .movieTitle("Inception")
                .score(84)
                .reasons(List.of("It matches your tense mood through Thriller."))
                .build();

        when(recommendationService.getSoloRecommendations(user, request))
                .thenReturn(List.of(recommendation));

        ResponseEntity<List<SoloRecommendationResponseDto>> response =
                recommendationController.getSoloRecommendations(user, request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1, response.getBody().size());
        assertEquals(27205, response.getBody().get(0).getTmdbId());
    }
}
