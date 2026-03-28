"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { posterUrl } from "@/lib/types";

interface Props {
  tmdbId: number;
  title: string;
  posterPath: string | null;
  rating?: number;
  releaseDate?: string;
}

export default function MovieCard({ tmdbId, title, posterPath, rating, releaseDate }: Props) {
  const router = useRouter();
  const year = releaseDate?.split("-")[0];
  const [imageSrc, setImageSrc] = useState(() => posterUrl(posterPath, "w342"));

  useEffect(() => {
    setImageSrc(posterUrl(posterPath, "w342"));
  }, [posterPath]);

  return (
    <button
      onClick={() => router.push(`/movie/${tmdbId}`)}
      className="group text-left w-full"
    >
      <div className="relative aspect-[2/3] rounded-lg overflow-hidden bg-gray-800">
        <img
          src={imageSrc}
          alt={title}
          className="w-full h-full object-cover transition-transform duration-300 group-hover:scale-105"
          loading="lazy"
          onError={() => setImageSrc("/posters/placeholder.svg")}
        />

        {/* Hover overlay */}
        <div className="absolute inset-0 bg-gradient-to-t from-black/80 via-black/20 to-transparent opacity-0 group-hover:opacity-100 transition-opacity duration-300 flex flex-col justify-end p-3">
          {rating !== undefined && rating > 0 && (
            <div className="flex items-center gap-1 mb-1">
              <svg className="w-4 h-4 text-amber-500" fill="currentColor" viewBox="0 0 20 20">
                <path d="M9.049 2.927c.3-.921 1.603-.921 1.902 0l1.07 3.292a1 1 0 00.95.69h3.462c.969 0 1.371 1.24.588 1.81l-2.8 2.034a1 1 0 00-.364 1.118l1.07 3.292c.3.921-.755 1.688-1.54 1.118l-2.8-2.034a1 1 0 00-1.175 0l-2.8 2.034c-.784.57-1.838-.197-1.539-1.118l1.07-3.292a1 1 0 00-.364-1.118L2.98 8.72c-.783-.57-.38-1.81.588-1.81h3.461a1 1 0 00.951-.69l1.07-3.292z" />
              </svg>
              <span className="text-white text-sm font-medium">{rating.toFixed(1)}</span>
            </div>
          )}
          <p className="text-white text-sm font-medium line-clamp-2">{title}</p>
          {year && <p className="text-gray-400 text-xs">{year}</p>}
        </div>
      </div>

      {/* Title below card — always visible */}
      <p className="mt-2 text-sm text-gray-300 line-clamp-1 group-hover:text-white transition-colors">
        {title}
      </p>
    </button>
  );
}
