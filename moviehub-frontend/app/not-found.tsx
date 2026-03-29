import Link from "next/link";

export default function NotFound() {
  return (
    <main className="flex min-h-screen items-center justify-center px-4 py-10 sm:px-6">
      <section className="app-surface app-card w-full max-w-4xl overflow-hidden rounded-[2rem]">
        <div className="grid gap-8 p-6 sm:p-8 lg:grid-cols-[1.1fr_0.9fr] lg:p-10">
          <div className="space-y-6">
            <div className="inline-flex items-center gap-2 rounded-full border border-amber-400/20 bg-amber-400/10 px-4 py-2 text-xs font-semibold uppercase tracking-[0.28em] text-amber-200">
              AtlasWatch
            </div>

            <div className="space-y-4">
              <p className="text-sm font-semibold uppercase tracking-[0.28em] text-slate-400">
                404 page not found
              </p>
              <h1 className="app-title max-w-2xl">
                This page slipped out of the watchlist.
              </h1>
              <p className="max-w-2xl text-base leading-8 text-slate-300 sm:text-lg">
                The route you tried to open does not exist, may have been moved, or was typed
                incorrectly. You can jump back into discovery, search for a movie directly, or head
                to the login screen.
              </p>
            </div>

            <div className="flex flex-wrap gap-3">
              <Link href="/homepage" className="btn-primary">
                Go to homepage
              </Link>
              <Link href="/search" className="btn-secondary">
                Search movies
              </Link>
              <Link href="/login" className="btn-ghost">
                Back to login
              </Link>
            </div>
          </div>

          <div className="app-surface-soft rounded-[1.5rem] p-6">
            <div className="flex h-full flex-col justify-between gap-6">
              <div className="space-y-3">
                <div className="inline-flex h-16 w-16 items-center justify-center rounded-3xl border border-white/10 bg-white/6">
                  <svg width="34" height="34" viewBox="0 0 256 256" xmlns="http://www.w3.org/2000/svg">
                    <defs>
                      <linearGradient id="missingGrad" x1="0%" y1="0%" x2="100%" y2="100%">
                        <stop offset="0%" stopColor="#f59e0b" />
                        <stop offset="35%" stopColor="#f97316" />
                        <stop offset="75%" stopColor="#fb7185" />
                      </linearGradient>
                    </defs>
                    <path
                      fill="url(#missingGrad)"
                      d="M 65 42 L 56 55 L 56 165 L 59 172 L 68 179 L 78 180 L 81 199 L 87 203 L 95 203 L 101 198 L 103 193 L 103 158 L 107 156 L 110 160 L 110 178 L 112 182 L 118 186 L 129 184 L 133 178 L 133 156 L 138 153 L 140 155 L 141 184 L 148 190 L 160 188 L 164 181 L 164 133 L 184 118 L 187 110 L 184 100 L 178 94 L 150 79 L 143 79 L 135 87 L 133 109 L 128 107 L 127 97 L 122 91 L 113 90 L 107 93 L 104 98 L 104 121 L 99 123 L 97 121 L 97 103 L 95 99 L 89 95 L 75 96 L 77 103 L 86 103 L 88 105 L 89 124 L 97 132 L 104 132 L 110 128 L 112 124 L 112 101 L 114 99 L 119 101 L 119 108 L 124 116 L 129 118 L 138 116 L 143 109 L 143 90 L 145 88 L 149 88 L 174 102 L 178 108 L 178 111 L 165 122 L 162 114 L 158 114 L 155 117 L 154 181 L 151 182 L 149 180 L 149 155 L 144 146 L 133 144 L 128 147 L 125 152 L 125 174 L 123 177 L 118 175 L 118 156 L 116 152 L 111 148 L 102 148 L 97 151 L 94 157 L 94 193 L 90 195 L 87 192 L 87 162 L 81 161 L 78 171 L 71 171 L 65 165 L 65 55 L 71 49 L 78 49 L 126 76 L 131 76 L 133 71 L 81 41 L 71 40 Z"
                    />
                  </svg>
                </div>
                <h2 className="text-2xl font-bold text-white">A quick recovery path</h2>
                <p className="text-sm leading-7 text-slate-300">
                  If you landed here from an old bookmark or a stale link, the movie may still be
                  searchable from the homepage or the search page even though this route is missing.
                </p>
              </div>

              <div className="rounded-[1.25rem] border border-white/10 bg-black/20 p-4">
                <p className="text-xs font-semibold uppercase tracking-[0.24em] text-slate-400">
                  Helpful routes
                </p>
                <div className="mt-4 space-y-3 text-sm text-slate-200">
                  <p>
                    <span className="text-slate-400">Home:</span> `/homepage`
                  </p>
                  <p>
                    <span className="text-slate-400">Search:</span> `/search?q=inception`
                  </p>
                  <p>
                    <span className="text-slate-400">Movie details:</span> `/movie/27205`
                  </p>
                </div>
              </div>
            </div>
          </div>
        </div>
      </section>
    </main>
  );
}
