package com.atlasmind.ai_travel_recommendation.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class RecommendationRequestDto {
    private List<String> moods;

    @Pattern(
            regexp = "(?i)^(any|short|medium|long)$",
            message = "runtimePreference must be any, short, medium, or long."
    )
    private String runtimePreference;

    @Min(value = 1, message = "Recommendation limit must be at least 1.")
    @Max(value = 10, message = "Recommendation limit cannot be more than 10.")
    private Integer limit;
}
