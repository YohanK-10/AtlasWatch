"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import {
  getWatchlist,
  updateWatchlistStatus,
  removeFromWatchlist,
} from "@/lib/api";
import { posterUrl } from "@/lib/types";
import type { WatchlistResponse, WatchlistStatus } from "@/lib/types";

const TABS: { label: string; value: WatchlistStatus | "ALL" }[] = [
  { label: "All", value: "ALL" },
  { label: "Plan to Watch", value: "PLAN_TO_WATCH" },
  { label: "Watching", value: "WATCHING" },
  { label: "Watched", value: "WATCHED" },
];

const STATUS_OPTIONS: { label: string; value: WatchlistStatus }[] = [
  { label: "Plan to Watch", value: "PLAN_TO_WATCH" },
  { label: "Watching", value: "WATCHING" },
  { label: "Watched", value: "WATCHED" },
];

const STATUS_COLORS: Record<WatchlistStatus, string> = {
  PLAN_TO_WATCH: "bg-blue-500/20 text-blue-400",
  WATCHING: "bg-amber-500/20 text-amber-400",
  WATCHED: "bg-emerald-500/20 text-emerald-400",
};

export default function WatchlistPage() {
  const [items, setItems] = useState<WatchlistResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [activeTab, setActiveTab] = useState<WatchlistStatus | "ALL">("ALL");
  const [error, setError] = useState("");
  const router = useRouter();

  useEffect(() => {
    setLoading(true);
    getWatchlist()
      .then(setItems)
      .catch((err) => setError(err.message))
      .finally(() => setLoading(false));
  }, []);

  const filtered = activeTab === "ALL" ? items : items.filter((i) => i.status === activeTab);

  const handleStatusChange = async (id: number, status: WatchlistStatus) => {
    try {
      const updated = await updateWatchlistStatus(id, status);
      setItems((prev) => prev.map((i) => (i.id === id ? updated : i)));
    } catch {
      // ignore
    }
  };

  const handleRemove = async (id: number) => {
    try {
      await removeFromWatchlist(id);
      setItems((prev) => prev.filter((i) => i.id !== id));
    } catch {
      // ignore
    }
  };

  const formatDate = (iso: string) =>
    new Date(iso).toLocaleDateString("en-US", { year: "numeric", month: "short", day: "numeric" });

  if (loading) {
    return (
      <div className="flex items-center justify-center h-[60vh]">
        <div className="w-8 h-8 border-2 border-amber-500 border-t-transparent rounded-full animate-spin" />
      </div>
    );
  }

  if (error) {
    return (
      <div className="max-w-7xl mx-auto px-4 py-20 text-center">
        <p className="text-red-400 mb-4">{error}</p>
        <button onClick={() => router.push("/login")} className="text-amber-500 hover:underline">
          Log in to view your watchlist
        </button>
      </div>
    );
  }

  return (
    <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
      <h1 className="text-3xl font-bold mb-2">My Watchlist</h1>
      <p className="text-gray-400 text-sm mb-8">{items.length} movies</p>

      {/* Tabs */}
      <div className="flex gap-2 mb-8 overflow-x-auto pb-2">
        {TABS.map((tab) => {
          const count =
            tab.value === "ALL" ? items.length : items.filter((i) => i.status === tab.value).length;
          return (
            <button
              key={tab.value}
              onClick={() => setActiveTab(tab.value)}
              className={`shrink-0 px-4 py-2 rounded-full text-sm font-medium transition ${
                activeTab === tab.value
                  ? "bg-amber-500 text-black"
                  : "bg-white/10 text-gray-300 hover:bg-white/20"
              }`}
            >
              {tab.label}
              <span className="ml-1.5 opacity-70">({count})</span>
            </button>
          );
        })}
      </div>

      {filtered.length === 0 ? (
        <div className="text-center py-20">
          <p className="text-gray-500 mb-4">No movies in this list yet.</p>
          <button
            onClick={() => router.push("/homepage")}
            className="text-amber-500 hover:underline"
          >
            Discover movies to add
          </button>
        </div>
      ) : (
        <div className="space-y-4">
          {filtered.map((item) => (
            <div
              key={item.id}
              className="flex gap-4 bg-white/5 border border-white/10 rounded-xl p-4 hover:bg-white/[0.07] transition"
            >
              {/* Poster */}
              <button
                onClick={() => router.push(`/movie/${item.tmdbId}`)}
                className="shrink-0"
              >
                <img
                  src={posterUrl(item.posterPath, "w185")}
                  alt={item.movieTitle}
                  className="w-20 h-30 object-cover rounded-lg"
                  loading="lazy"
                />
              </button>

              {/* Info */}
              <div className="flex-1 min-w-0">
                <button
                  onClick={() => router.push(`/movie/${item.tmdbId}`)}
                  className="text-left"
                >
                  <h3 className="font-semibold text-white hover:text-amber-500 transition line-clamp-1">
                    {item.movieTitle}
                  </h3>
                </button>

                <div className="flex items-center gap-3 mt-2">
                  <span
                    className={`px-2.5 py-1 rounded-full text-xs font-medium ${STATUS_COLORS[item.status]}`}
                  >
                    {STATUS_OPTIONS.find((s) => s.value === item.status)?.label}
                  </span>
                  <span className="text-xs text-gray-500">Added {formatDate(item.addedAt)}</span>
                </div>

                {/* Status change + remove */}
                <div className="flex items-center gap-2 mt-3">
                  <select
                    value={item.status}
                    onChange={(e) => handleStatusChange(item.id, e.target.value as WatchlistStatus)}
                    className="bg-white/10 border border-white/10 rounded-lg px-3 py-1.5 text-xs text-gray-300 focus:outline-none focus:ring-1 focus:ring-amber-500"
                  >
                    {STATUS_OPTIONS.map((s) => (
                      <option key={s.value} value={s.value} className="bg-gray-900">
                        {s.label}
                      </option>
                    ))}
                  </select>
                  <button
                    onClick={() => handleRemove(item.id)}
                    className="text-gray-500 hover:text-red-400 transition p-1"
                    title="Remove from watchlist"
                  >
                    <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                      <path strokeLinecap="round" strokeLinejoin="round" d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16" />
                    </svg>
                  </button>
                </div>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
