"use client";

import { useState } from "react";
import { useRouter, usePathname } from "next/navigation";
import { logout } from "@/lib/api";

const NAV_LINKS = [
  { href: "/homepage", label: "Home" },
  { href: "/watchlist", label: "Watchlist" },
];

export default function Navbar() {
  const [query, setQuery] = useState("");
  const [mobileOpen, setMobileOpen] = useState(false);
  const router = useRouter();
  const pathname = usePathname();

  const handleSearch = (e: React.FormEvent) => {
    e.preventDefault();
    const trimmed = query.trim();
    if (!trimmed) return;
    router.push(`/search?q=${encodeURIComponent(trimmed)}`);
    setMobileOpen(false);
  };

  const handleLogout = async () => {
    try {
      await logout();
    } catch {
      // even if the call fails, redirect to login
    }
    router.push("/login");
  };

  return (
    <nav className="sticky top-0 z-50 bg-black/80 backdrop-blur-md border-b border-white/10">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div className="flex items-center justify-between h-16">
          {/* Logo */}
          <button
            onClick={() => router.push("/homepage")}
            className="flex items-center gap-2 shrink-0"
          >
            <svg width="36" height="36" viewBox="0 0 256 256" xmlns="http://www.w3.org/2000/svg">
              <defs>
                <linearGradient id="navGrad" x1="0%" y1="0%" x2="100%" y2="100%">
                  <stop offset="0%" stopColor="#f59e0b" />
                  <stop offset="35%" stopColor="#f97316" />
                  <stop offset="75%" stopColor="#fb7185" />
                </linearGradient>
              </defs>
              <path
                fill="url(#navGrad)"
                d="M 65 42 L 56 55 L 56 165 L 59 172 L 68 179 L 78 180 L 81 199 L 87 203 L 95 203 L 101 198 L 103 193 L 103 158 L 107 156 L 110 160 L 110 178 L 112 182 L 118 186 L 129 184 L 133 178 L 133 156 L 138 153 L 140 155 L 141 184 L 148 190 L 160 188 L 164 181 L 164 133 L 184 118 L 187 110 L 184 100 L 178 94 L 150 79 L 143 79 L 135 87 L 133 109 L 128 107 L 127 97 L 122 91 L 113 90 L 107 93 L 104 98 L 104 121 L 99 123 L 97 121 L 97 103 L 95 99 L 89 95 L 75 96 L 77 103 L 86 103 L 88 105 L 89 124 L 97 132 L 104 132 L 110 128 L 112 124 L 112 101 L 114 99 L 119 101 L 119 108 L 124 116 L 129 118 L 138 116 L 143 109 L 143 90 L 145 88 L 149 88 L 174 102 L 178 108 L 178 111 L 165 122 L 162 114 L 158 114 L 155 117 L 154 181 L 151 182 L 149 180 L 149 155 L 144 146 L 133 144 L 128 147 L 125 152 L 125 174 L 123 177 L 118 175 L 118 156 L 116 152 L 111 148 L 102 148 L 97 151 L 94 157 L 94 193 L 90 195 L 87 192 L 87 162 L 81 161 L 78 171 L 71 171 L 65 165 L 65 55 L 71 49 L 78 49 L 126 76 L 131 76 L 133 71 L 81 41 L 71 40 Z"
              />
            </svg>
            <span className="text-white font-bold text-lg hidden sm:inline">
              AtlasWatch
            </span>
          </button>

          {/* Search bar — desktop */}
          <form onSubmit={handleSearch} className="hidden md:flex flex-1 max-w-lg mx-8">
            <div className="relative w-full">
              <input
                type="text"
                value={query}
                onChange={(e) => setQuery(e.target.value)}
                placeholder="Search movies..."
                className="w-full h-10 rounded-full bg-white/10 border border-white/10 pl-10 pr-4 text-sm text-white placeholder-gray-400 focus:outline-none focus:ring-2 focus:ring-amber-500/50 focus:border-amber-500/50 transition"
              />
              <svg
                className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-gray-400"
                fill="none"
                viewBox="0 0 24 24"
                stroke="currentColor"
                strokeWidth={2}
              >
                <path strokeLinecap="round" strokeLinejoin="round" d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z" />
              </svg>
            </div>
          </form>

          {/* Nav links — desktop */}
          <div className="hidden md:flex items-center gap-6">
            {NAV_LINKS.map((link) => (
              <button
                key={link.href}
                onClick={() => router.push(link.href)}
                className={`text-sm font-medium transition ${
                  pathname === link.href
                    ? "text-amber-500"
                    : "text-gray-300 hover:text-white"
                }`}
              >
                {link.label}
              </button>
            ))}
            <button
              onClick={handleLogout}
              className="text-sm text-gray-400 hover:text-red-400 transition"
            >
              Logout
            </button>
          </div>

          {/* Mobile hamburger */}
          <button
            onClick={() => setMobileOpen(!mobileOpen)}
            className="md:hidden text-gray-300 hover:text-white"
          >
            <svg className="w-6 h-6" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
              {mobileOpen ? (
                <path strokeLinecap="round" strokeLinejoin="round" d="M6 18L18 6M6 6l12 12" />
              ) : (
                <path strokeLinecap="round" strokeLinejoin="round" d="M4 6h16M4 12h16M4 18h16" />
              )}
            </svg>
          </button>
        </div>

        {/* Mobile menu */}
        {mobileOpen && (
          <div className="md:hidden pb-4 space-y-3">
            <form onSubmit={handleSearch}>
              <input
                type="text"
                value={query}
                onChange={(e) => setQuery(e.target.value)}
                placeholder="Search movies..."
                className="w-full h-10 rounded-lg bg-white/10 border border-white/10 px-4 text-sm text-white placeholder-gray-400 focus:outline-none focus:ring-2 focus:ring-amber-500/50"
              />
            </form>
            {NAV_LINKS.map((link) => (
              <button
                key={link.href}
                onClick={() => { router.push(link.href); setMobileOpen(false); }}
                className={`block w-full text-left text-sm py-2 ${
                  pathname === link.href ? "text-amber-500" : "text-gray-300"
                }`}
              >
                {link.label}
              </button>
            ))}
            <button
              onClick={handleLogout}
              className="block w-full text-left text-sm py-2 text-gray-400 hover:text-red-400"
            >
              Logout
            </button>
          </div>
        )}
      </div>
    </nav>
  );
}
