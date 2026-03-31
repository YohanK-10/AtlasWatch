package com.atlasmind.ai_travel_recommendation.dto.request;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RecommendationRequestDto {
    private List<String> moods;
    private String runtimePreference;
    private Integer limit;
}
