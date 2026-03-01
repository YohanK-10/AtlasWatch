package com.atlasmind.ai_travel_recommendation.models;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
public class MovieGenre {
    @EmbeddedId
    private MovieGenreId movieGenreId;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("movieId")
    @JoinColumn(name = "movie_id")
    private Movie movie;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("genreId")
    @JoinColumn(name = "genre_id")
    private Genre genre;

    /**
     * Convenience constructor — lets you write:
     *   new MovieGenre(movie, genre)
     * instead of manually building the composite key.
     */
//    public MovieGenre(Movie movie, Genre genre) {
//        this.movie = movie;
//        this.genre = genre;
//        this.movieGenreId = new MovieGenreId(movie.getId(), genre.getId());
//    }
}
