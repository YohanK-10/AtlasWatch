import type {
  SearchResponse,
  MovieResponse,
  ReviewResponse,
  WatchlistResponse,
  CreateReviewRequest,
  AddToWatchlistRequest,
  WatchlistStatus,
} from "./types";

const BASE = "http://localhost:8080";

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const res = await fetch(`${BASE}${path}`, {
    credentials: "include",
    ...init,
    headers: { "Content-Type": "application/json", ...init?.headers },
  });
  if (!res.ok) {
    const text = await res.text();
    throw new Error(text || `${res.status} ${res.statusText}`);
  }
  if (res.status === 204) return undefined as T;
  return res.json();
}

// ── Movies (public) ──────────────────────────────────────────────────

export function searchMovies(query: string, page = 1) {
  return request<SearchResponse>(
    `/api/movies/search?query=${encodeURIComponent(query)}&page=${page}`
  );
}

export function getTrending() {
  return request<SearchResponse>("/api/movies/trending");
}

export function getMovieDetails(tmdbId: number) {
  return request<MovieResponse>(`/api/movies/${tmdbId}`);
}

// ── Reviews ──────────────────────────────────────────────────────────

export function getReviewsByMovie(tmdbId: number) {
  return request<ReviewResponse[]>(`/api/reviews/movie/${tmdbId}`);
}

export function createReview(body: CreateReviewRequest) {
  return request<ReviewResponse>("/api/reviews", {
    method: "POST",
    body: JSON.stringify(body),
  });
}

export function updateReview(reviewId: number, body: CreateReviewRequest) {
  return request<ReviewResponse>(`/api/reviews/${reviewId}`, {
    method: "PUT",
    body: JSON.stringify(body),
  });
}

export function deleteReview(reviewId: number) {
  return request<void>(`/api/reviews/${reviewId}`, { method: "DELETE" });
}

// ── Watchlist ────────────────────────────────────────────────────────

export function getWatchlist() {
  return request<WatchlistResponse[]>("/api/watchlist");
}

export function addToWatchlist(body: AddToWatchlistRequest) {
  return request<WatchlistResponse>("/api/watchlist", {
    method: "POST",
    body: JSON.stringify(body),
  });
}

export function updateWatchlistStatus(id: number, status: WatchlistStatus) {
  return request<WatchlistResponse>(`/api/watchlist/${id}/status`, {
    method: "PUT",
    body: JSON.stringify({ status }),
  });
}

export function removeFromWatchlist(id: number) {
  return request<void>(`/api/watchlist/${id}`, { method: "DELETE" });
}

// ── Auth ─────────────────────────────────────────────────────────────

export function logout() {
  return request<void>("/auth/logout", { method: "POST" });
}
