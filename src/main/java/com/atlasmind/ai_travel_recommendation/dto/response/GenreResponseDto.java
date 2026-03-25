package com.atlasmind.ai_travel_recommendation.dto.response;

import com.atlasmind.ai_travel_recommendation.models.Genre;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@AllArgsConstructor
@Builder
public class GenreResponseDto {

    private final Integer tmdbId;
    private final String name;

    public static GenreResponseDto fromGenre(Genre genre) {
        return GenreResponseDto.builder()
                .tmdbId(genre.getTmdbId())
                .name(genre.getName())
                .build();
    }
}