# Cache Behavior

## Purpose
This document explains what AtlasWatch caches today, what it does not cache,
and why.

## Active Caches

### Trending Movies
- Cache name: `trending`
- Key: `daily`
- TTL: 10 minutes
- Source of truth: TMDB, with local persistence to PostgreSQL

Why this is cached:
- trending data changes slowly relative to request volume
- homepage traffic is likely bursty
- repeated TMDB calls add latency and increase rate-limit risk

## Not Currently Cached

### Movie Details DTO Responses
Movie detail responses are currently served directly from the service/database
flow and are **not cached in Redis**.

Why:
- the previous detail-cache path caused instability during serialization
- the `genres` collection shape needed to be normalized first
- correctness is more important than caching for MVP stability

The detail flow still benefits from a DB-first lookup pattern:
- check PostgreSQL for cached movie data
- refresh from TMDB if stale or missing
- store the refreshed movie locally

So details are still "cached" in the broader sense via PostgreSQL, but not in
the Redis DTO-response layer right now.

### Reviews
Reviews are not cached.

Why:
- they change frequently relative to read volume
- stale reviews are more noticeable to users
- correctness is more valuable than a small latency win here

### Watchlist
Watchlist responses are not cached.

Why:
- watchlist status changes are user-specific
- stale watchlist data hurts trust immediately

## Operational Guidance

### When to clear Redis
Clear Redis when:
- cache serialization shapes change
- a deployment changes cached DTO structure
- you suspect stale/corrupt cached values

### MVP Recommendation
For MVP, keep Redis focused on stable, high-read, low-volatility data such as:
- trending lists
- possibly genre lists later

Avoid caching fast-changing or user-specific responses until test coverage and
observability are stronger.

