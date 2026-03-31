"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { useParams, useRouter } from "next/navigation";
import { useAuth } from "@/components/AuthProvider";
import FeedbackBanner from "@/components/FeedbackBanner";
import RemoteImage from "@/components/RemoteImage";
import StarRating from "@/components/StarRating";
import StatusPanel from "@/components/StatusPanel";
import { MovieDetailsSkeleton } from "@/components/Skeletons";
import {
  addToWatchlist,
  ApiError,
  createReview,
  getErrorMessage,
  getMovieDetails,
  getMyReviewByMovie,
  getReviewSummaryByMovie,
  getReviewsByMovie,
  updateReview,
} from "@/lib/api";
import {
  BACKDROP_PLACEHOLDER,
  POSTER_PLACEHOLDER,
  backdropUrl,
  posterUrl,
  type MovieResponse,
  type ReviewResponse,
  type ReviewSummaryResponse,
  type WatchlistStatus,
} from "@/lib/types";

type FeedbackTone = "success" | "error" | "info";

interface FeedbackState {
  tone: FeedbackTone;
  title: string;
  message: string;
}

function movieErrorCopy(error: unknown) {
  if (error instanceof ApiError && error.kind === "network") {
    return {
      title: "Movie details can't reach the backend",
      description: "Make sure the backend is running and try again.",
    };
  }

  if (error instanceof ApiError && error.status === 404) {
    return {
      title: "This movie could not be found",
      description: "The movie details endpoint returned a not-found response for this TMDB id.",
    };
  }

  return {
    title: "We couldn't load this movie",
    description: "Try again, or head back to the homepage.",
  };
}

function reviewErrorCopy(error: unknown) {
  if (error instanceof ApiError && error.kind === "network") {
    return "AtlasWatch could not reach the backend API.";
  }

  if (error instanceof ApiError && (error.status === 401 || error.status === 403)) {
    return "Your session is not authenticated for this action.";
  }

  return "Reviews could not be loaded right now.";
}

function formatDate(value: string) {
  return new Date(value).toLocaleDateString("en-US", {
    year: "numeric",
    month: "short",
    day: "numeric",
  });
}

function distributionRows(summary: ReviewSummaryResponse | null) {
  return Array.from({ length: 10 }, (_, index) => {
    const rating = 10 - index;
    return {
      rating,
      count: summary?.ratingDistribution?.[rating] ?? 0,
    };
  });
}

export default function MovieDetailPage() {
  const { tmdbId } = useParams<{ tmdbId: string }>();
  const router = useRouter();
  const { isAuthenticated } = useAuth();
  const id = Number(tmdbId);

  const [movie, setMovie] = useState<MovieResponse | null>(null);
  const [movieLoading, setMovieLoading] = useState(true);
  const [movieError, setMovieError] = useState<unknown>(null);

  const [reviews, setReviews] = useState<ReviewResponse[]>([]);
  const [reviewSummary, setReviewSummary] = useState<ReviewSummaryResponse | null>(null);
  const [myReview, setMyReview] = useState<ReviewResponse | null>(null);
  const [reviewsLoading, setReviewsLoading] = useState(true);
  const [reviewsError, setReviewsError] = useState<unknown>(null);
  const [showSpoilers, setShowSpoilers] = useState<Set<number>>(new Set());

  const [ratingInput, setRatingInput] = useState(0);
  const [reviewText, setReviewText] = useState("");
  const [reviewSpoiler, setReviewSpoiler] = useState(false);
  const [editorMode, setEditorMode] = useState<"closed" | "composer" | "inline">("closed");
  const [isRatingsSnapshotExpanded, setIsRatingsSnapshotExpanded] = useState(false);
  const [ratingError, setRatingError] = useState("");
  const [reviewError, setReviewError] = useState("");
  const [savingRating, setSavingRating] = useState(false);
  const [savingReview, setSavingReview] = useState(false);

  const [watchlistAdded, setWatchlistAdded] = useState(false);
  const [watchlistLoading, setWatchlistLoading] = useState(false);
  const [feedback, setFeedback] = useState<FeedbackState | null>(null);

  const syncComposer = useCallback((review: ReviewResponse | null) => {
    setRatingInput(review?.rating ?? 0);
    setReviewText(review?.reviewText ?? "");
    setReviewSpoiler(review?.containsSpoilers ?? false);
  }, []);

  const loadReviewData = useCallback(async (nextMyReview?: ReviewResponse | null) => {
    if (!Number.isFinite(id)) {
      return;
    }

    setReviewsLoading(true);
    setReviewsError(null);

    try {
      const [communityReviews, summary, ownReview] = await Promise.all([
        getReviewsByMovie(id),
        getReviewSummaryByMovie(id),
        nextMyReview !== undefined
          ? Promise.resolve(nextMyReview)
          : isAuthenticated
            ? getMyReviewByMovie(id)
            : Promise.resolve(null),
      ]);

      setReviews(communityReviews);
      setReviewSummary(summary);
      setMyReview(ownReview);
      syncComposer(ownReview);
    } catch (error) {
      setReviews([]);
      setReviewSummary(null);
      setMyReview(null);
      setReviewsError(error);
    } finally {
      setReviewsLoading(false);
    }
  }, [id, isAuthenticated, syncComposer]);

  useEffect(() => {
    if (!feedback) return;
    const timeoutId = window.setTimeout(() => setFeedback(null), 4200);
    return () => window.clearTimeout(timeoutId);
  }, [feedback]);

  useEffect(() => {
    if (!Number.isFinite(id)) {
      setMovie(null);
      setMovieError(new ApiError("Invalid movie id", { status: 404, kind: "http" }));
      setMovieLoading(false);
      return;
    }

    let active = true;
    setMovieLoading(true);
    setMovieError(null);
    setReviewsError(null);
    setRatingError("");
    setReviewError("");
    setShowSpoilers(new Set());
    setEditorMode("closed");

    void getMovieDetails(id)
      .then((data) => {
        if (active) setMovie(data);
      })
      .catch((error) => {
        if (active) {
          setMovie(null);
          setMovieError(error);
        }
      })
      .finally(() => {
        if (active) setMovieLoading(false);
      });

    void loadReviewData();

    return () => {
      active = false;
    };
  }, [id, isAuthenticated, loadReviewData]);

  const movieFacts = useMemo(() => {
    if (!movie) return [];

    const hours = movie.runtime ? Math.floor(movie.runtime / 60) : 0;
    const mins = movie.runtime ? movie.runtime % 60 : 0;

    return [
      { label: "Release year", value: movie.releaseDate ? movie.releaseDate.split("-")[0] : "Unknown" },
      { label: "Runtime", value: movie.runtime ? (hours > 0 ? `${hours}h ${mins}m` : `${mins}m`) : "Unknown" },
    ];
  }, [movie]);

  const hasOwnWrittenReview = Boolean(myReview?.hasReviewText);
  const isInlineEditingOwnReview = editorMode === "inline" && hasOwnWrittenReview;
  const rows = useMemo(() => distributionRows(reviewSummary), [reviewSummary]);
  const maxBucket = Math.max(...rows.map((row) => row.count), 1);

  const closeEditor = useCallback(() => {
    setEditorMode("closed");
    setRatingError("");
    setReviewError("");
    syncComposer(myReview);
  }, [myReview, syncComposer]);

  const openInlineEditor = useCallback(() => {
    syncComposer(myReview);
    setRatingError("");
    setReviewError("");
    setEditorMode("inline");
  }, [myReview, syncComposer]);

  const openComposer = useCallback(() => {
    syncComposer(myReview);
    setRatingError("");
    setReviewError("");
    setEditorMode("composer");
  }, [myReview, syncComposer]);

  const saveReviewRecord = async (
    payload: { rating: number; reviewText?: string; containsSpoilers?: boolean },
    success: FeedbackState
  ) => {
    const body = {
      tmdbId: id,
      rating: payload.rating,
      reviewText: payload.reviewText ?? "",
      containsSpoilers: payload.containsSpoilers ?? false,
    };

    const saved = myReview ? await updateReview(myReview.id, body) : await createReview(body);
    await loadReviewData(saved);
    setFeedback(success);
    return saved;
  };

  const handleRatingSelection = async (nextRating: number) => {
    if (nextRating === 0 || savingRating) {
      return;
    }

    setRatingInput(nextRating);
    setSavingRating(true);
    setRatingError("");

    try {
      await saveReviewRecord(
        {
          rating: nextRating,
          reviewText: myReview?.reviewText ?? "",
          containsSpoilers: myReview?.hasReviewText ? myReview.containsSpoilers : false,
        },
        {
          tone: "success",
          title: myReview ? "Rating updated" : "Rating saved",
          message: "Your score is now counted in the movie's rating breakdown.",
        }
      );
    } catch (error) {
      const message =
        error instanceof ApiError && (error.status === 401 || error.status === 403)
          ? "Your session is not authenticated for rating changes."
          : getErrorMessage(error, "We couldn't save your rating.");

      setRatingError(message);
      setFeedback({ tone: "error", title: "Rating not saved", message });
    } finally {
      setSavingRating(false);
    }
  };

  const handleSubmitReview = async (event: React.FormEvent) => {
    event.preventDefault();

    if (ratingInput === 0) {
      setReviewError("Choose a rating before posting your review.");
      return;
    }

    if (!reviewText.trim()) {
      setReviewError("Write a review, or just save the rating on its own.");
      return;
    }

    setSavingReview(true);
    setReviewError("");

    try {
      await saveReviewRecord(
        {
          rating: ratingInput,
          reviewText: reviewText.trim(),
          containsSpoilers: reviewSpoiler,
        },
        {
          tone: "success",
          title: hasOwnWrittenReview ? "Review updated" : "Review posted",
          message: hasOwnWrittenReview ? "Your updated review is now live." : "Your review is now visible on the movie page.",
        }
      );
      setEditorMode("closed");
    } catch (error) {
      const message =
        error instanceof ApiError && (error.status === 401 || error.status === 403)
          ? "Your session is not authenticated for review changes."
          : getErrorMessage(error, "The review could not be saved.");

      setReviewError(message);
      setFeedback({ tone: "error", title: "Review not saved", message });
    } finally {
      setSavingReview(false);
    }
  };

  const handleAddToWatchlist = async (status: WatchlistStatus) => {
    setWatchlistLoading(true);

    try {
      await addToWatchlist({ tmdbId: id, status });
      setWatchlistAdded(true);
      setFeedback({
        tone: "success",
        title: status === "WATCHED" ? "Marked as watched" : "Added to watchlist",
        message: status === "WATCHED"
          ? "This movie is now in your watched list."
          : "You can manage it later from your watchlist page.",
      });
    } catch (error) {
      const message =
        error instanceof ApiError && (error.status === 401 || error.status === 403)
          ? "Your session is not authenticated for watchlist changes."
          : getErrorMessage(error, "The movie was not added to your watchlist.");

      if (error instanceof ApiError && error.status === 409) {
        setWatchlistAdded(true);
        setFeedback({
          tone: "info",
          title: "Already in your watchlist",
          message: "AtlasWatch already has this movie saved in your watchlist.",
        });
      } else {
        setFeedback({ tone: "error", title: "Couldn't update watchlist", message });
      }
    } finally {
      setWatchlistLoading(false);
    }
  };

  const toggleSpoiler = (reviewId: number) => {
    setShowSpoilers((current) => {
      const next = new Set(current);
      if (next.has(reviewId)) next.delete(reviewId);
      else next.add(reviewId);
      return next;
    });
  };

  if (movieLoading) {
    return <MovieDetailsSkeleton />;
  }

  if (movieError || !movie) {
    const copy = movieErrorCopy(movieError);
    return (
      <div className="app-page">
        <StatusPanel
          title={copy.title}
          description={copy.description}
          tone="error"
          actionLabel="Go back"
          onAction={() => router.back()}
          secondaryLabel="Browse homepage"
          onSecondaryAction={() => router.push("/homepage")}
        />
      </div>
    );
  }

  return (
    <div className="pb-12">
      <section className="relative overflow-hidden border-b border-slate-800/60">
        <RemoteImage
          src={backdropUrl(movie.backdropPath)}
          fallbackSrc={BACKDROP_PLACEHOLDER}
          alt={`${movie.movieTitle} backdrop`}
          className="absolute inset-0 h-full w-full object-cover"
        />
        <div className="absolute inset-0 bg-gradient-to-b from-slate-950/55 via-slate-950/58 to-slate-950" />
        <div className="absolute inset-0 bg-[radial-gradient(circle_at_top_left,rgba(245,158,11,0.18),transparent_28%)]" />

        <div className="app-page relative z-10 pt-8">
          <button type="button" onClick={() => router.back()} className="btn-ghost mb-6 !px-0 text-sm text-slate-300">
            <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
              <path strokeLinecap="round" strokeLinejoin="round" d="M15 19l-7-7 7-7" />
            </svg>
            Back
          </button>

          <div className="grid gap-8 pb-10 md:grid-cols-[220px,1fr] lg:grid-cols-[280px,1fr]">
            <div className="mx-auto w-full max-w-[280px] md:mx-0">
              <div className="app-surface app-card overflow-hidden p-2">
                <RemoteImage
                  src={posterUrl(movie.posterPath)}
                  fallbackSrc={POSTER_PLACEHOLDER}
                  alt={movie.movieTitle}
                  className="aspect-[2/3] w-full rounded-[1.2rem] object-cover"
                />
              </div>
            </div>

            <div className="space-y-6">
              <div>
                <p className="text-xs uppercase tracking-[0.26em] text-amber-300/85">Movie details</p>
                <h1 className="app-title mt-3 max-w-4xl">{movie.movieTitle}</h1>
              </div>

              <div className="flex flex-wrap gap-2">
                {movie.rating > 0 && (
                  <span className="app-pill border-amber-400/20 bg-amber-400/10 text-amber-100">
                    <svg className="h-4 w-4 text-amber-400" fill="currentColor" viewBox="0 0 20 20">
                      <path d="M9.049 2.927c.3-.921 1.603-.921 1.902 0l1.07 3.292a1 1 0 00.95.69h3.462c.969 0 1.371 1.24.588 1.81l-2.8 2.034a1 1 0 00-.364 1.118l1.07 3.292c.3.921-.755 1.688-1.54 1.118l-2.8-2.034a1 1 0 00-1.175 0l-2.8 2.034c-.784.57-1.838-.197-1.539-1.118l1.07-3.292a1 1 0 00-.364-1.118L2.98 8.72c-.783-.57-.38-1.81.588-1.81h3.461a1 1 0 00.951-.69l1.07-3.292z" />
                    </svg>
                    {movie.rating.toFixed(1)} / 10
                  </span>
                )}
                {movieFacts.map((fact) => (
                  <span key={fact.label} className="app-pill">
                    {fact.label}: {fact.value}
                  </span>
                ))}
              </div>

              {movie.genres.length > 0 && (
                <div className="flex flex-wrap gap-2">
                  {movie.genres.map((genre) => (
                    <span
                      key={genre}
                      className="rounded-full border border-cyan-400/15 bg-cyan-400/8 px-3 py-1.5 text-xs font-medium text-cyan-100"
                    >
                      {genre}
                    </span>
                  ))}
                </div>
              )}

              <p className="max-w-3xl text-sm leading-7 text-slate-200 sm:text-base">
                {movie.movieOverview || "No overview was provided for this title."}
              </p>

              {feedback && (
                <FeedbackBanner
                  tone={feedback.tone}
                  title={feedback.title}
                  message={feedback.message}
                  onDismiss={() => setFeedback(null)}
                />
              )}

              {isAuthenticated && (
                <div className="flex flex-wrap gap-3">
                  {!watchlistAdded ? (
                    <>
                      <button
                        type="button"
                        onClick={() => void handleAddToWatchlist("PLAN_TO_WATCH")}
                        disabled={watchlistLoading}
                        className="btn-primary"
                      >
                        Add to watchlist
                      </button>
                      <button
                        type="button"
                        onClick={() => void handleAddToWatchlist("WATCHED")}
                        disabled={watchlistLoading}
                        className="btn-secondary"
                      >
                        Mark as watched
                      </button>
                    </>
                  ) : (
                    <span className="app-pill border-emerald-400/20 bg-emerald-500/10 px-4 py-2 text-emerald-100">
                      Saved in your watchlist
                    </span>
                  )}
                </div>
              )}
            </div>
          </div>
        </div>
      </section>
      <section className="app-page grid gap-6 xl:grid-cols-[1.35fr,0.95fr]">
        <div className="space-y-6">
          <div className="app-surface app-card p-5 sm:p-6">
            <div className="mb-5 flex flex-col gap-2 sm:flex-row sm:items-end sm:justify-between">
              <div>
                <h2 className="app-section-title text-[1.5rem]">Community reviews</h2>
                <p className="app-copy-muted mt-2 text-sm">
                  {reviewsLoading
                    ? "Loading reactions..."
                    : `${reviewSummary?.writtenReviewCount ?? reviews.length} written review${(reviewSummary?.writtenReviewCount ?? reviews.length) === 1 ? "" : "s"} and ${reviewSummary?.totalRatings ?? 0} total rating${(reviewSummary?.totalRatings ?? 0) === 1 ? "" : "s"}`}
                </p>
              </div>
            </div>

            {reviewsLoading ? (
              <div className="space-y-4">
                <div className="skeleton-block h-28 rounded-[1.2rem]" />
                <div className="skeleton-block h-28 rounded-[1.2rem]" />
              </div>
            ) : reviewsError ? (
              <StatusPanel
                compact
                tone="error"
                title="Reviews unavailable"
                description={reviewErrorCopy(reviewsError)}
                actionLabel="Retry"
                onAction={() => void loadReviewData()}
              />
            ) : reviews.length === 0 ? (
              <StatusPanel
                compact
                title="No written reviews yet"
                description={
                  isAuthenticated
                    ? "This movie has ratings, but no one has written a review yet."
                    : "This movie has no written reviews yet. Sign in if you want to leave the first one."
                }
                actionLabel={isAuthenticated && !hasOwnWrittenReview ? "Write the first review" : undefined}
                onAction={isAuthenticated && !hasOwnWrittenReview ? openComposer : undefined}
              />
            ) : (
              <div className="space-y-4">
                {reviews.map((review) => {
                  const spoilerHidden = review.containsSpoilers && !showSpoilers.has(review.id);
                  const isOwnReview = myReview?.id === review.id;

                  return (
                    <article
                      key={review.id}
                      className="rounded-[1.2rem] border border-slate-700/35 bg-slate-900/55 p-4 sm:p-5"
                    >
                      <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
                        <div className="flex items-center gap-3">
                          <div className="flex h-10 w-10 items-center justify-center rounded-full bg-gradient-to-br from-amber-400 to-orange-500 text-sm font-bold text-slate-950">
                            {review.username[0]?.toUpperCase()}
                          </div>
                          <div>
                            <p className="font-semibold text-white">{review.username}</p>
                            <p className="text-xs text-slate-400">
                              {formatDate(review.createdAt)}
                              {review.edited ? " (edited)" : ""}
                            </p>
                          </div>
                        </div>

                        <div className="flex items-center gap-2">
                          {isOwnReview && (
                            <button
                              type="button"
                              onClick={() => {
                                openInlineEditor();
                              }}
                              className="rounded-full border border-slate-700/35 bg-white/5 p-2 text-slate-300 transition hover:bg-white/8 hover:text-white"
                              aria-label="Edit your review"
                            >
                              <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                                <path strokeLinecap="round" strokeLinejoin="round" d="M11 5H6a2 2 0 00-2 2v11a2 2 0 002 2h11a2 2 0 002-2v-5m-1.414-9.414a2 2 0 112.828 2.828L11.828 15H9v-2.828l8.586-8.586z" />
                              </svg>
                            </button>
                          )}
                          <div className="app-pill border-amber-400/20 bg-black/25 text-amber-100">
                            {review.rating}/10
                          </div>
                        </div>
                      </div>

                      <div className="mt-4">
                        {isOwnReview && isInlineEditingOwnReview ? (
                          <form onSubmit={handleSubmitReview} className="space-y-4">
                            <textarea
                              value={reviewText}
                              onChange={(event) => setReviewText(event.target.value)}
                              rows={5}
                              placeholder="What worked, what didn't, and would you recommend it?"
                              className="field-textarea"
                              autoFocus
                            />
                            <label className="flex items-center gap-3 rounded-[1rem] border border-slate-700/35 bg-white/3 px-4 py-3 text-sm text-slate-300">
                              <input
                                type="checkbox"
                                checked={reviewSpoiler}
                                onChange={(event) => setReviewSpoiler(event.target.checked)}
                                className="h-4 w-4 rounded border-slate-500 bg-slate-900 text-amber-500 focus:ring-amber-400"
                              />
                              Mark this review as containing spoilers
                            </label>
                            {reviewError && (
                              <p className="rounded-[1rem] border border-rose-400/18 bg-rose-500/8 px-4 py-3 text-sm text-rose-200">
                                {reviewError}
                              </p>
                            )}
                            <div className="flex flex-wrap gap-3">
                              <button type="submit" disabled={savingReview} className="btn-primary">
                                {savingReview ? "Saving..." : "Update review"}
                              </button>
                              <button type="button" onClick={closeEditor} className="btn-secondary">
                                Cancel
                              </button>
                            </div>
                          </form>
                        ) : spoilerHidden ? (
                          <button
                            type="button"
                            onClick={() => toggleSpoiler(review.id)}
                            className="btn-ghost !px-0 text-sm text-amber-200"
                          >
                            Reveal spoiler review
                          </button>
                        ) : (
                          <p className="text-sm leading-7 text-slate-200">{review.reviewText}</p>
                        )}
                      </div>
                    </article>
                  );
                })}
              </div>
            )}
          </div>
        </div>

        <aside className="space-y-6">
          <div className="app-surface app-card p-5 sm:p-6">
            <h2 className="text-xl font-semibold text-white">Your take</h2>
            <p className="app-copy-muted mt-2 text-sm">
              Rate the movie on its own, then add a written review only if you want to say more.
            </p>

            {!isAuthenticated ? (
              <div className="mt-5 space-y-4 rounded-[1.2rem] border border-slate-700/35 bg-white/4 p-4">
                <p className="text-sm leading-7 text-slate-300">
                  Sign in or create an account to rate this film and join the discussion.
                </p>
                <div className="flex flex-wrap gap-3">
                  <button type="button" onClick={() => router.push("/login")} className="btn-primary">
                    Log in
                  </button>
                  <button type="button" onClick={() => router.push("/register")} className="btn-secondary">
                    Sign up
                  </button>
                </div>
              </div>
            ) : (
              <div className="mt-5 space-y-5">
                <div className="rounded-[1.2rem] border border-slate-700/35 bg-white/4 p-4">
                  <p className="text-sm font-semibold text-white">Your rating</p>
                  <p className="mt-1 text-sm text-slate-400">
                    {myReview
                      ? "Your score updates as soon as you tap a different star."
                      : "Tap a star to save a score even if you do not want to write a review."}
                  </p>
                  <div className="mt-4 flex flex-wrap items-center gap-3">
                    <StarRating
                      value={ratingInput}
                      max={10}
                      onChange={(nextRating) => void handleRatingSelection(nextRating)}
                      size="lg"
                      disabled={savingRating}
                    />
                    <span className="text-sm text-slate-400">
                      {savingRating
                        ? "Saving your rating..."
                        : ratingInput > 0
                          ? `${ratingInput}/10`
                          : "Tap a star to rate"}
                    </span>
                  </div>
                  {!hasOwnWrittenReview && (
                    <div className="mt-4 flex flex-wrap gap-3">
                      <button type="button" onClick={openComposer} className="btn-secondary">
                        {myReview ? "Add written review" : "Write a review too"}
                      </button>
                    </div>
                  )}
                  {ratingError && (
                    <p className="mt-4 rounded-[1rem] border border-rose-400/18 bg-rose-500/8 px-4 py-3 text-sm text-rose-200">
                      {ratingError}
                    </p>
                  )}
                </div>

                {hasOwnWrittenReview && !isInlineEditingOwnReview ? (
                  <div className="rounded-[1.2rem] border border-slate-700/35 bg-white/4 p-4">
                    <p className="text-sm leading-7 text-slate-300">
                      You already have a written review on this movie. Use the edit icon on your review card to update it.
                    </p>
                  </div>
                ) : editorMode === "composer" ? (
                  <form onSubmit={handleSubmitReview} className="space-y-4 rounded-[1.2rem] border border-slate-700/35 bg-white/4 p-4">
                    <div>
                      <p className="text-sm font-semibold text-white">{hasOwnWrittenReview ? "Update your review" : "Write your review"}</p>
                      <p className="mt-1 text-sm text-slate-400">Your rating stays separate, but you can edit both together here.</p>
                    </div>
                    <textarea
                      value={reviewText}
                      onChange={(event) => setReviewText(event.target.value)}
                      rows={5}
                      placeholder="What worked, what didn't, and would you recommend it?"
                      className="field-textarea"
                    />
                    <label className="flex items-center gap-3 rounded-[1rem] border border-slate-700/35 bg-white/3 px-4 py-3 text-sm text-slate-300">
                      <input
                        type="checkbox"
                        checked={reviewSpoiler}
                        onChange={(event) => setReviewSpoiler(event.target.checked)}
                        className="h-4 w-4 rounded border-slate-500 bg-slate-900 text-amber-500 focus:ring-amber-400"
                      />
                      Mark this review as containing spoilers
                    </label>
                    {reviewError && (
                      <p className="rounded-[1rem] border border-rose-400/18 bg-rose-500/8 px-4 py-3 text-sm text-rose-200">
                        {reviewError}
                      </p>
                    )}
                    <div className="flex flex-wrap gap-3">
                      <button type="submit" disabled={savingReview} className="btn-primary">
                        {savingReview ? "Saving..." : hasOwnWrittenReview ? "Update review" : "Post review"}
                      </button>
                      <button
                        type="button"
                        onClick={closeEditor}
                        className="btn-secondary"
                      >
                        Cancel
                      </button>
                    </div>
                  </form>
                ) : null}
              </div>
            )}
          </div>

          <div className="app-surface app-card p-5 sm:p-6">
            <div className="flex items-start justify-between gap-4">
              <div>
                <h2 className="text-xl font-semibold text-white">Ratings snapshot</h2>
                <p className="app-copy-muted mt-2 text-sm">
                  A simple 1 to 10 breakdown from AtlasWatch users.
                </p>
              </div>
              <div className="flex items-start gap-3">
                <button
                  type="button"
                  onClick={() => setIsRatingsSnapshotExpanded((current) => !current)}
                  className="rounded-full border border-slate-700/35 bg-white/5 px-3 py-1.5 text-xs font-semibold text-slate-300 transition hover:bg-white/8 hover:text-white"
                >
                  {isRatingsSnapshotExpanded ? "Hide breakdown" : "Show breakdown"}
                </button>
                <div className="text-right">
                  <p className="text-2xl font-semibold text-white">
                    {reviewSummary?.averageRating != null ? reviewSummary.averageRating.toFixed(1) : "-"}
                  </p>
                  <p className="text-xs text-slate-400">{reviewSummary?.totalRatings ?? 0} ratings</p>
                </div>
              </div>
            </div>

            {isRatingsSnapshotExpanded ? (
              <div className="mt-5 space-y-2">
                {rows.map((row) => (
                  <div key={row.rating} className="grid grid-cols-[2.2rem,1fr,2.8rem] items-center gap-3">
                    <span className="text-sm text-slate-300">{row.rating}</span>
                    <div className="h-2 overflow-hidden rounded-full bg-slate-800">
                      <div
                        className="h-full rounded-full bg-gradient-to-r from-amber-400 to-orange-400"
                        style={{ width: `${(row.count / maxBucket) * 100}%` }}
                      />
                    </div>
                    <span className="text-right text-sm text-slate-400">{row.count}</span>
                  </div>
                ))}
              </div>
            ) : (
              <div className="mt-5 rounded-[1rem] border border-slate-700/35 bg-white/4 px-4 py-3">
                <p className="text-sm text-slate-300">
                  Expand this card if you want to see the full 1 to 10 rating distribution.
                </p>
              </div>
            )}
          </div>

          <div className="app-surface app-card p-5 sm:p-6">
            <h2 className="text-xl font-semibold text-white">Quick facts</h2>
            <div className="mt-5 space-y-3">
              {movieFacts.map((fact) => (
                <div
                  key={fact.label}
                  className="flex items-center justify-between rounded-[1rem] border border-slate-700/35 bg-white/4 px-4 py-3"
                >
                  <span className="text-sm text-slate-400">{fact.label}</span>
                  <span className="text-sm font-semibold text-white">{fact.value}</span>
                </div>
              ))}
            </div>
          </div>
        </aside>
      </section>
    </div>
  );
}
