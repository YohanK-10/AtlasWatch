package com.atlasmind.ai_travel_recommendation.controller;

import com.atlasmind.ai_travel_recommendation.dto.request.RecommendationRequestDto;
import com.atlasmind.ai_travel_recommendation.dto.response.RecommendationResponseDto;
import com.atlasmind.ai_travel_recommendation.exceptions.GlobalExceptionHandler;
import com.atlasmind.ai_travel_recommendation.service.RecommendationService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class RecommendationControllerWebTest {

    @Mock
    private RecommendationService recommendationService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        RecommendationController controller = new RecommendationController(recommendationService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(GlobalExceptionHandler.builder().build())
                .setValidator(validator)
                .build();
    }

    @Test
    void coldStartEndpointBindsQueryParamsAndReturnsRecommendationPayload() throws Exception {
        RecommendationResponseDto recommendation = RecommendationResponseDto.builder()
                .tmdbId(27205)
                .movieTitle("Inception")
                .genres(List.of("Thriller", "Science Fiction"))
                .onWatchlist(false)
                .reasons(List.of("It matches your tense vibe mix through Thriller."))
                .build();

        when(recommendationService.getColdStartRecommendations(argThat(request ->
                request.getLimit() != null
                        && request.getLimit() == 3
                        && "short".equals(request.getRuntimePreference())
                        && request.getMoods() != null
                        && request.getMoods().equals(List.of("tense", "dark"))
        ))).thenReturn(List.of(recommendation));

        mockMvc.perform(get("/api/recommendations/cold-start")
                        .param("moods", "tense", "dark")
                        .param("runtimePreference", "short")
                        .param("limit", "3")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].tmdbId").value(27205))
                .andExpect(jsonPath("$[0].movieTitle").value("Inception"))
                .andExpect(jsonPath("$[0].onWatchlist").value(false))
                .andExpect(jsonPath("$[0].reasons", hasSize(1)));

        verify(recommendationService).getColdStartRecommendations(argThat(request ->
                request.getLimit() != null
                        && request.getLimit() == 3
                        && "short".equals(request.getRuntimePreference())
                        && request.getMoods() != null
                        && request.getMoods().equals(List.of("tense", "dark"))
        ));
    }

    @Test
    void coldStartEndpointReturnsBadRequestForInvalidLimit() throws Exception {
        mockMvc.perform(get("/api/recommendations/cold-start")
                        .param("limit", "11")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Recommendation limit cannot be more than 10."));

        verifyNoInteractions(recommendationService);
    }

    @Test
    void coldStartEndpointReturnsBadRequestForInvalidRuntimePreference() throws Exception {
        mockMvc.perform(get("/api/recommendations/cold-start")
                        .param("runtimePreference", "marathon")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("runtimePreference must be any, short, medium, or long."));

        verifyNoInteractions(recommendationService);
    }
}
