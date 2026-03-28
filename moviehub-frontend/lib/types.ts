// ── Backend response shapes ──────────────────────────────────────────

/** Matches MovieResponseDto.java */
export interface MovieResponse {
  tmdbId: number;
  movieTitle: string;
  movieOverview: string;
  releaseDate: string;
  posterPath: string | null;
  backdropPath: string | null;
  rating: number;
  runtime: number | null;
  popularity: number;
  genres: string[];
}

/** Matches ReviewResponseDto.java */
export interface ReviewResponse {
  id: number;
  tmdbId: number;
  movieTitle: string;
  username: string;
  rating: number;
  reviewText: string;
  containsSpoilers: boolean;
  createdAt: string;
  updatedAt: string;
}

/** Matches WatchlistResponseDto.java */
export interface WatchlistResponse {
  id: number;
  tmdbId: number;
  movieTitle: string;
  posterPath: string | null;
  status: WatchlistStatus;
  addedAt: string;
}

export type WatchlistStatus = "PLAN_TO_WATCH" | "WATCHING" | "WATCHED";

/** Matches GenreResponseDto.java */
export interface GenreResponse {
  tmdbId: number;
  name: string;
}

// ── TMDB search shapes (raw from backend passthrough) ────────────────

/** Matches SearchResponseDto.java — note: field names use @JsonProperty snake_case */
export interface SearchResponse {
  page: number;
  results: TmdbMovie[];
  total_pages: number;
  total_results: number;
}

/** Matches MovieDto.java — TMDB search result item */
export interface TmdbMovie {
  id: number;
  title: string;
  overview: string;
  popularity: number;
  poster_path: string | null;
  backdrop_path: string | null;
  release_date: string;
  vote_average: number;
  genre_ids: number[];
}

// ── Request DTOs ─────────────────────────────────────────────────────

export interface CreateReviewRequest {
  tmdbId: number;
  rating: number;
  reviewText: string;
  containsSpoilers: boolean;
}

export interface AddToWatchlistRequest {
  tmdbId: number;
  status?: WatchlistStatus;
}

// ── Helpers ──────────────────────────────────────────────────────────

export const TMDB_IMAGE_BASE = "https://image.tmdb.org/t/p";

export function posterUrl(path: string | null, size: "w185" | "w342" | "w500" = "w500"): string {
  if (!path) return "/posters/placeholder.svg";
  return `${TMDB_IMAGE_BASE}/${size}${path}`;
}

export function backdropUrl(path: string | null, size: "w780" | "w1280" | "original" = "w1280"): string {
  if (!path) return "";
  return `${TMDB_IMAGE_BASE}/${size}${path}`;
}
