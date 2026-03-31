"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { useRouter } from "next/navigation";
import { useAuth } from "@/components/AuthProvider";
import FeedbackBanner from "@/components/FeedbackBanner";
import RemoteImage from "@/components/RemoteImage";
import StatusPanel from "@/components/StatusPanel";
import {
  ApiError,
  getErrorMessage,
  getSoloRecommendations,
} from "@/lib/api";
import {
  POSTER_PLACEHOLDER,
  posterUrl,
  type SoloRecommendationMood,
  type SoloRecommendationResponse,
  type SoloRuntimePreference,
} from "@/lib/types";

const MOOD_OPTIONS: { value: SoloRecommendationMood; label: string; description: string }[] = [
  { value: "any", label: "Open to anything", description: "No mood bias, just strong watchlist picks." },
  { value: "comforting", label: "Comforting", description: "Warm, easy, low-friction choices." },
  { value: "cozy", label: "Cozy", description: "Soft, familiar, low-stakes comfort." },
  { value: "funny", label: "Funny", description: "Lighter picks with playful energy." },
  { value: "tense", label: "Tense", description: "Suspense, momentum, and edge-of-seat energy." },
  { value: "dark", label: "Dark", description: "Heavier, sharper, moodier stories." },
  { value: "emotional", label: "Emotional", description: "Character-driven and feeling-forward." },
  { value: "thoughtful", label: "Thoughtful", description: "Ideas, reflection, and slower payoff." },
  { value: "adventurous", label: "Adventurous", description: "Escapist, bigger-scale, and propulsive." },
  { value: "romantic", label: "Romantic", description: "Connection, chemistry, and yearning." },
  { value: "hopeful", label: "Hopeful", description: "A more uplifting or restorative finish." },
  { value: "bittersweet", label: "Bittersweet", description: "Tender, wistful, and mixed-emotion stories." },
  { value: "mind-bending", label: "Mind-bending", description: "Twisty ideas and reality-shifting plots." },
  { value: "eerie", label: "Eerie", description: "Unsettling atmosphere without full chaos." },
  { value: "inspiring", label: "Inspiring", description: "A lift, a push, or a sense of momentum." },
];

const RUNTIME_OPTIONS: { value: SoloRuntimePreference; label: string; description: string }[] = [
  { value: "any", label: "Any length", description: "Runtime is not part of the decision." },
  { value: "short", label: "Short", description: "Best when you want a lower-commitment watch." },
  { value: "medium", label: "Medium", description: "A balanced pick for a normal movie night." },
  { value: "long", label: "Long", description: "For when you want a bigger, more immersive watch." },
];

function getRecommendationErrorCopy(error: unknown) {
  if (error instanceof ApiError && (error.status === 401 || error.status === 403)) {
    return {
      title: "Sign in to get personal picks",
      description:
        "This feature scores movies from your own watchlist, so AtlasWatch needs an authenticated session before it can rank anything for you.",
      actionLabel: "Go to login",
      action: "/login",
    };
  }

  if (error instanceof ApiError && error.kind === "network") {
    return {
      title: "We couldn't reach the recommendation service",
      description:
        "AtlasWatch could not connect to the backend just now, so your watchlist triage never finished. Try again in a moment.",
      actionLabel: "Try again",
      action: "retry",
    };
  }

  return {
    title: "We couldn't generate a pick right now",
    description:
      "The recommendation request did not complete successfully. Adjust the filters or retry the request.",
    actionLabel: "Try again",
    action: "retry",
  };
}

export default function PickForMePage() {
  const router = useRouter();
  const { isAuthenticated } = useAuth();
  const [selectedMoods, setSelectedMoods] = useState<SoloRecommendationMood[]>(["any"]);
  const [runtimePreference, setRuntimePreference] = useState<SoloRuntimePreference>("any");
  const [limit, setLimit] = useState(5);
  const [results, setResults] = useState<SoloRecommendationResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<unknown>(null);
  const [feedback, setFeedback] = useState<{ tone: "success" | "error" | "info"; title: string; message: string } | null>(null);

  const activeMoods = useMemo(
    () => MOOD_OPTIONS.filter((option) => selectedMoods.includes(option.value)),
    [selectedMoods]
  );
  const activeRuntime = useMemo(
    () => RUNTIME_OPTIONS.find((option) => option.value === runtimePreference) ?? RUNTIME_OPTIONS[0],
    [runtimePreference]
  );

  const loadRecommendations = useCallback(
    async (options?: {
      moods?: SoloRecommendationMood[];
      runtimePreference?: SoloRuntimePreference;
      limit?: number;
    }) => {
      setLoading(true);
      setError(null);

      try {
        const data = await getSoloRecommendations({
          moods: options?.moods ?? selectedMoods,
          runtimePreference: options?.runtimePreference ?? runtimePreference,
          limit: options?.limit ?? limit,
        });
        setResults(data);
        setFeedback(null);
      } catch (loadError) {
        setResults([]);
        setError(loadError);
        setFeedback({
          tone: "error",
          title: "Recommendation request failed",
          message: getErrorMessage(loadError, "AtlasWatch couldn't finish ranking your watchlist."),
        });
      } finally {
        setLoading(false);
      }
    },
    [limit, runtimePreference, selectedMoods]
  );

  useEffect(() => {
    if (!isAuthenticated) {
      setLoading(false);
      setResults([]);
      setError(null);
      return;
    }

    void loadRecommendations({
      moods: selectedMoods,
      runtimePreference,
      limit,
    });
  }, [isAuthenticated, limit, loadRecommendations, runtimePreference, selectedMoods]);

  useEffect(() => {
    if (!feedback) return;
    const id = window.setTimeout(() => setFeedback(null), 4200);
    return () => window.clearTimeout(id);
  }, [feedback]);

  const handleSubmit = async (event: React.FormEvent) => {
    event.preventDefault();
    await loadRecommendations();
  };

  const toggleMood = (value: SoloRecommendationMood) => {
    setSelectedMoods((current) => {
      if (value === "any") {
        return ["any"];
      }

      const withoutAny = current.filter((item) => item !== "any");
      if (withoutAny.includes(value)) {
        const next = withoutAny.filter((item) => item !== value);
        return next.length > 0 ? next : ["any"];
      }

      return [...withoutAny, value];
    });
  };

  const formatRuntime = (runtime: number | null) => {
    if (!runtime) return "Unknown runtime";
    const hours = Math.floor(runtime / 60);
    const mins = runtime % 60;
    return hours > 0 ? `${hours}h ${mins}m` : `${mins}m`;
  };

  const formatAddedAt = (iso: string) =>
    new Date(iso).toLocaleDateString("en-US", {
      year: "numeric",
      month: "short",
      day: "numeric",
    });

  const topPick = results[0];
  const remainingPicks = results.slice(1);

  if (!isAuthenticated) {
    return (
      <div className="app-page">
        <StatusPanel
          title="Sign in to use watchlist triage"
          description="Pick for me ranks movies from your own watchlist, so you need an account and an active session before AtlasWatch can narrow the list down for you."
          tone="error"
          actionLabel="Go to login"
          onAction={() => router.push("/login")}
          secondaryLabel="Create account"
          onSecondaryAction={() => router.push("/register")}
        />
      </div>
    );
  }

  if (error && !loading) {
    const copy = getRecommendationErrorCopy(error);
    return (
      <div className="app-page space-y-6">
        <StatusPanel
          title={copy.title}
          description={copy.description}
          tone="error"
          actionLabel={copy.actionLabel}
          onAction={() => {
            if (copy.action === "retry") {
              void loadRecommendations();
            } else {
              router.push(copy.action);
            }
          }}
          secondaryLabel="Open watchlist"
          onSecondaryAction={() => router.push("/watchlist")}
        />
      </div>
    );
  }

  return (
    <div className="app-page space-y-8">
      <section className="app-surface app-card overflow-hidden p-6 sm:p-8">
        <div className="grid gap-8 lg:grid-cols-[1.05fr,0.95fr]">
          <div>
            <p className="text-xs uppercase tracking-[0.26em] text-amber-300/85">Watchlist triage</p>
            <h1 className="app-title mt-3 max-w-3xl">Tell AtlasWatch what kind of night this is.</h1>
            <p className="app-copy-soft mt-4 max-w-2xl text-sm leading-7 sm:text-base">
              This tool ranks movies from your watchlist that you have not finished yet. It balances
              mood, runtime, the age of each saved title, and the genres you tend to rate highly.
            </p>

            <div className="mt-6 grid gap-3 sm:grid-cols-2">
              <div className="rounded-[1.2rem] border border-slate-700/35 bg-white/5 p-4">
                <p className="text-sm font-semibold text-white">Current mood</p>
                <p className="mt-2 text-sm text-slate-300">
                  {activeMoods.map((option) => option.label).join(", ")}
                </p>
                <p className="mt-1 text-sm text-slate-400">
                  {activeMoods.length === 1
                    ? activeMoods[0]?.description
                    : "AtlasWatch blends all selected moods into one recommendation pass."}
                </p>
              </div>
              <div className="rounded-[1.2rem] border border-slate-700/35 bg-white/5 p-4">
                <p className="text-sm font-semibold text-white">Runtime lens</p>
                <p className="mt-2 text-sm text-slate-300">{activeRuntime.label}</p>
                <p className="mt-1 text-sm text-slate-400">{activeRuntime.description}</p>
              </div>
            </div>
          </div>

          <form onSubmit={handleSubmit} className="rounded-[1.5rem] border border-slate-700/35 bg-white/4 p-5 sm:p-6">
            <div>
              <p className="text-sm font-semibold text-white">1. What moods are you in?</p>
              <p className="mt-2 text-sm text-slate-400">Choose one or more. AtlasWatch will blend them together.</p>
              <div className="mt-4 flex flex-wrap gap-2">
                {MOOD_OPTIONS.map((option) => (
                  <button
                    key={option.value}
                    type="button"
                    onClick={() => toggleMood(option.value)}
                    className={`rounded-full px-4 py-2 text-sm font-semibold transition ${
                      selectedMoods.includes(option.value)
                        ? "bg-amber-400/14 text-amber-100"
                        : "border border-slate-700/35 bg-white/5 text-slate-300 hover:bg-white/8 hover:text-white"
                    }`}
                  >
                    {option.label}
                  </button>
                ))}
              </div>
            </div>

            <div className="mt-6">
              <p className="text-sm font-semibold text-white">2. How much time do you want to commit?</p>
              <div className="mt-4 grid gap-2 sm:grid-cols-2">
                {RUNTIME_OPTIONS.map((option) => (
                  <button
                    key={option.value}
                    type="button"
                    onClick={() => setRuntimePreference(option.value)}
                    className={`rounded-[1rem] border px-4 py-3 text-left transition ${
                      runtimePreference === option.value
                        ? "border-amber-400/28 bg-amber-400/8 text-amber-100"
                        : "border-slate-700/35 bg-white/5 text-slate-300 hover:bg-white/8 hover:text-white"
                    }`}
                  >
                    <p className="text-sm font-semibold">{option.label}</p>
                    <p className="mt-1 text-sm text-slate-400">{option.description}</p>
                  </button>
                ))}
              </div>
            </div>

            <div className="mt-6">
              <label className="mb-2 block text-sm font-semibold text-white" htmlFor="result-limit">
                3. How many picks should AtlasWatch return?
              </label>
              <select
                id="result-limit"
                value={limit}
                onChange={(event) => setLimit(Number(event.target.value))}
                className="field-select max-w-xs"
              >
                {[3, 5, 7, 10].map((option) => (
                  <option key={option} value={option} className="bg-slate-900">
                    {option} picks
                  </option>
                ))}
              </select>
              <p className="mt-2 text-sm text-slate-400">
                AtlasWatch is currently showing {results.length} pick{results.length === 1 ? "" : "s"} for this filter set.
              </p>
            </div>

            <div className="mt-8 flex flex-wrap gap-3">
              <button type="submit" disabled={loading} className="btn-primary">
                {loading ? "Ranking watchlist..." : "Pick for me"}
              </button>
              <button
                type="button"
                onClick={() => {
                  setSelectedMoods(["any"]);
                  setRuntimePreference("any");
                  setLimit(5);
                  void loadRecommendations({
                    moods: ["any"],
                    runtimePreference: "any",
                    limit: 5,
                  });
                }}
                disabled={loading}
                className="btn-secondary"
              >
                Reset filters
              </button>
            </div>
          </form>
        </div>
      </section>

      {feedback && (
        <FeedbackBanner
          tone={feedback.tone}
          title={feedback.title}
          message={feedback.message}
          onDismiss={() => setFeedback(null)}
        />
      )}

      {loading ? (
        <section className="space-y-4">
          <div className="skeleton-block h-72 rounded-[1.5rem]" />
          <div className="grid gap-4 lg:grid-cols-2">
            <div className="skeleton-block h-52 rounded-[1.5rem]" />
            <div className="skeleton-block h-52 rounded-[1.5rem]" />
          </div>
        </section>
      ) : results.length === 0 ? (
        <StatusPanel
          title="No watchlist candidates yet"
          description="AtlasWatch could not find any unfinished movies to rank. Add a few titles to your watchlist or move finished films into the watched state, then come back here."
          actionLabel="Open watchlist"
          onAction={() => router.push("/watchlist")}
          secondaryLabel="Discover movies"
          onSecondaryAction={() => router.push("/homepage")}
        />
      ) : (
        <section className="space-y-6">
          {topPick && (
            <article className="app-surface app-card overflow-hidden">
              <div className="grid gap-6 p-5 sm:p-6 lg:grid-cols-[220px,1fr] lg:items-start">
                <button
                  type="button"
                  onClick={() => router.push(`/movie/${topPick.tmdbId}`)}
                  className="mx-auto w-[210px] max-w-full lg:mx-0"
                >
                  <RemoteImage
                    src={posterUrl(topPick.posterPath, "w342")}
                    fallbackSrc={POSTER_PLACEHOLDER}
                    alt={topPick.movieTitle}
                    className="aspect-[2/3] w-full rounded-[1.2rem] object-cover"
                    loading="lazy"
                  />
                </button>

                <div className="space-y-5">
                  <div className="flex flex-col gap-3 xl:flex-row xl:items-start xl:justify-between">
                    <div>
                      <p className="text-xs uppercase tracking-[0.26em] text-amber-300/85">Top pick</p>
                      <h2 className="app-section-title mt-2">{topPick.movieTitle}</h2>
                      <p className="app-copy-muted mt-2 text-sm">
                        Added to your watchlist on {formatAddedAt(topPick.addedAt)}
                      </p>
                    </div>
                    <span className="app-pill">
                      {topPick.watchlistStatus === "PLAN_TO_WATCH" ? "Plan to watch" : "Watched"}
                    </span>
                  </div>

                  <p className="text-sm leading-7 text-slate-200 sm:text-base">
                    {topPick.movieOverview || "No overview is available for this movie yet."}
                  </p>

                  <div className="flex flex-wrap gap-2">
                    {topPick.rating && topPick.rating > 0 && (
                      <span className="app-pill border-amber-400/18 bg-black/20 text-amber-100">
                        {topPick.rating.toFixed(1)} / 10
                      </span>
                    )}
                    <span className="app-pill bg-black/20">{formatRuntime(topPick.runtime)}</span>
                    {topPick.releaseDate && (
                      <span className="app-pill bg-black/20">
                        {topPick.releaseDate.split("-")[0]}
                      </span>
                    )}
                    {topPick.genres.map((genre) => (
                      <span
                        key={genre}
                        className="rounded-full border border-cyan-400/15 bg-cyan-400/8 px-3 py-1.5 text-xs font-medium text-cyan-100"
                      >
                        {genre}
                      </span>
                    ))}
                  </div>

                  <div className="rounded-[1.2rem] border border-slate-700/35 bg-white/4 p-4">
                    <p className="text-sm font-semibold text-white">Why this rose to the top</p>
                    <div className="mt-3 space-y-2">
                      {topPick.reasons.map((reason) => (
                        <div key={reason} className="flex gap-3 text-sm text-slate-300">
                          <span className="mt-1 h-2 w-2 shrink-0 rounded-full bg-amber-400" />
                          <span>{reason}</span>
                        </div>
                      ))}
                    </div>
                  </div>

                  <div className="flex flex-wrap gap-3">
                    <button
                      type="button"
                      onClick={() => router.push(`/movie/${topPick.tmdbId}`)}
                      className="btn-primary"
                    >
                      Open movie
                    </button>
                    <button
                      type="button"
                      onClick={() => router.push("/watchlist")}
                      className="btn-secondary"
                    >
                      Back to watchlist
                    </button>
                  </div>
                </div>
              </div>
            </article>
          )}

          {remainingPicks.length > 0 && (
            <div className="grid gap-4 xl:grid-cols-2">
              {remainingPicks.map((pick) => (
                <article key={pick.tmdbId} className="app-surface app-card p-4 sm:p-5">
                  <div className="flex gap-4">
                    <button
                      type="button"
                      onClick={() => router.push(`/movie/${pick.tmdbId}`)}
                      className="shrink-0"
                    >
                      <RemoteImage
                        src={posterUrl(pick.posterPath, "w185")}
                        fallbackSrc={POSTER_PLACEHOLDER}
                        alt={pick.movieTitle}
                        className="aspect-[2/3] w-24 rounded-[1rem] object-cover"
                        loading="lazy"
                      />
                    </button>

                    <div className="min-w-0 flex-1">
                      <div>
                        <div>
                          <button
                            type="button"
                            onClick={() => router.push(`/movie/${pick.tmdbId}`)}
                            className="text-left"
                          >
                            <h3 className="text-lg font-semibold text-white transition hover:text-amber-200">
                              {pick.movieTitle}
                            </h3>
                          </button>
                          <p className="mt-1 text-sm text-slate-400">
                            Added {formatAddedAt(pick.addedAt)}
                          </p>
                        </div>
                      </div>

                      <div className="mt-3 flex flex-wrap gap-2">
                        <span className="app-pill bg-black/20">{formatRuntime(pick.runtime)}</span>
                        {pick.genres.slice(0, 3).map((genre) => (
                          <span
                            key={genre}
                            className="rounded-full border border-cyan-400/15 bg-cyan-400/8 px-3 py-1.5 text-xs font-medium text-cyan-100"
                          >
                            {genre}
                          </span>
                        ))}
                      </div>

                      <div className="mt-4 space-y-2">
                        {pick.reasons.map((reason) => (
                          <div key={reason} className="flex gap-3 text-sm text-slate-300">
                            <span className="mt-1 h-2 w-2 shrink-0 rounded-full bg-amber-400" />
                            <span>{reason}</span>
                          </div>
                        ))}
                      </div>
                    </div>
                  </div>
                </article>
              ))}
            </div>
          )}
        </section>
      )}
    </div>
  );
}
