"use client";

import { useEffect, useState } from "react";
import { getTrending } from "@/lib/api";
import { backdropUrl, type TmdbMovie } from "@/lib/types";
import MovieCard from "@/components/MovieCard";
import { useRouter } from "next/navigation";

export default function HomePage() {
  const [trending, setTrending] = useState<TmdbMovie[]>([]);
  const [heroIdx, setHeroIdx] = useState(0);
  const [loading, setLoading] = useState(true);
  const router = useRouter();

  useEffect(() => {
    getTrending()
      .then((data) => setTrending(data.results ?? []))
      .catch(() => {})
      .finally(() => setLoading(false));
  }, []);

  // Rotate hero every 8 seconds across the top 5 trending
  const heroMovies = trending.slice(0, 5);
  useEffect(() => {
    if (heroMovies.length === 0) return;
    const id = setInterval(() => setHeroIdx((i) => (i + 1) % heroMovies.length), 8000);
    return () => clearInterval(id);
  }, [heroMovies.length]);

  const hero = heroMovies[heroIdx];

  if (loading) {
    return (
      <div className="flex items-center justify-center h-[60vh]">
        <div className="w-8 h-8 border-2 border-amber-500 border-t-transparent rounded-full animate-spin" />
      </div>
    );
  }

  return (
    <>
      {/* ── Hero Banner ─────────────────────────────────────── */}
      {hero && (
        <section className="relative h-[70vh] min-h-[420px] overflow-hidden">
          {/* Background images — crossfade */}
          {heroMovies.map((m, i) => (
            <div
              key={m.id}
              className={`absolute inset-0 transition-opacity duration-1000 ${
                i === heroIdx ? "opacity-100" : "opacity-0"
              }`}
            >
              <img
                src={backdropUrl(m.backdrop_path)}
                alt=""
                className="w-full h-full object-cover"
              />
            </div>
          ))}

          {/* Gradient overlays */}
          <div className="absolute inset-0 bg-gradient-to-t from-black via-black/50 to-transparent" />
          <div className="absolute inset-0 bg-gradient-to-r from-black/80 via-transparent to-transparent" />

          {/* Hero content */}
          <div className="absolute bottom-0 left-0 right-0 px-4 sm:px-6 lg:px-8 pb-16 max-w-7xl mx-auto">
            <h1 className="text-4xl sm:text-5xl font-bold mb-3 max-w-2xl leading-tight">
              {hero.title}
            </h1>
            <div className="flex items-center gap-4 mb-4 text-sm text-gray-300">
              {hero.vote_average > 0 && (
                <span className="flex items-center gap-1">
                  <svg className="w-4 h-4 text-amber-500" fill="currentColor" viewBox="0 0 20 20">
                    <path d="M9.049 2.927c.3-.921 1.603-.921 1.902 0l1.07 3.292a1 1 0 00.95.69h3.462c.969 0 1.371 1.24.588 1.81l-2.8 2.034a1 1 0 00-.364 1.118l1.07 3.292c.3.921-.755 1.688-1.54 1.118l-2.8-2.034a1 1 0 00-1.175 0l-2.8 2.034c-.784.57-1.838-.197-1.539-1.118l1.07-3.292a1 1 0 00-.364-1.118L2.98 8.72c-.783-.57-.38-1.81.588-1.81h3.461a1 1 0 00.951-.69l1.07-3.292z" />
                  </svg>
                  {hero.vote_average.toFixed(1)}
                </span>
              )}
              {hero.release_date && (
                <span>{hero.release_date.split("-")[0]}</span>
              )}
            </div>
            <p className="text-gray-300 text-base max-w-xl line-clamp-3 mb-6">
              {hero.overview}
            </p>
            <button
              onClick={() => router.push(`/movie/${hero.id}`)}
              className="px-6 py-3 bg-amber-500 hover:bg-amber-600 text-black font-semibold rounded-lg transition"
            >
              View Details
            </button>
          </div>

          {/* Hero indicators */}
          <div className="absolute bottom-6 right-8 hidden sm:flex gap-2">
            {heroMovies.map((_, i) => (
              <button
                key={i}
                onClick={() => setHeroIdx(i)}
                className={`h-1.5 rounded-full transition-all ${
                  i === heroIdx ? "w-8 bg-amber-500" : "w-4 bg-white/30 hover:bg-white/50"
                }`}
              />
            ))}
          </div>
        </section>
      )}

      {/* ── Trending Grid ───────────────────────────────────── */}
      <section className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-12">
        <h2 className="text-2xl font-bold mb-6">Trending Today</h2>
        <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 xl:grid-cols-6 gap-4 sm:gap-6">
          {trending.map((movie) => (
            <MovieCard
              key={movie.id}
              tmdbId={movie.id}
              title={movie.title}
              posterPath={movie.poster_path}
              rating={movie.vote_average}
              releaseDate={movie.release_date}
            />
          ))}
        </div>
      </section>
    </>
  );
}
