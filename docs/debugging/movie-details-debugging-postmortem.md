# Movie Details Debugging Postmortem

## Why This Document Exists
This write-up explains how the `Movie not found` issue was debugged and fixed
across the frontend, backend, and Redis cache layers.

The goal is not just to record the final fix.
The goal is to show the *reasoning process* so the same approach can be reused
in future projects and explained clearly in technical interviews.

## Original Symptoms
There were two user-visible issues:

1. Clicking a movie on the homepage often led to a `Movie not found` page.
2. Some searched movies had no poster image.

During debugging, the movie-details issue evolved through a few stages:

- At first, the detail page showed `Movie not found` immediately.
- Later, the detail page briefly flashed valid content and then switched to
  `Movie not found`.
- Eventually it became clear that the problem was not a single bug, but a
  chain of smaller implementation problems across multiple layers.

## High-Level Debugging Strategy
The debugging process followed this sequence:

1. Start from the user symptom, not from assumptions.
2. Trace the exact route and API calls involved.
3. Separate frontend rendering issues from backend response issues.
4. Compare working endpoints with failing endpoints.
5. Inspect stored/cache data when the live responses are inconsistent.
6. Fix one failure mode at a time, then retest the system.

This is a useful general-purpose debugging pattern:

- Reproduce
- Isolate
- Compare
- Hypothesize
- Verify
- Fix
- Retest

## What Was Actually Wrong

### 1. The frontend treated any detail-page fetch failure as "movie not found"
The detail page was loading both:

- movie details
- reviews

in the same request flow.

Originally, if either request failed, the page fell back to the same
`Movie not found` UI. That masked the true source of failure.

Why this matters:

- A review failure is not the same thing as a movie-details failure.
- A backend `500` should not be silently presented as "movie not found."
- When failures are collapsed into one UI state, debugging becomes much harder.

### 2. The reviews endpoint returned `404` when the movie was not in the local DB
The reviews endpoint looked up the movie locally and threw `404` if the movie
did not exist in PostgreSQL yet.

That was a bad fit for the product behavior:

- A movie can exist in TMDB and appear on the homepage.
- But it may not yet exist in the local database.
- In that case, the correct review result is usually an empty list, not `404`.

Fix:

- The reviews endpoint was changed to return `[]` when the movie does not exist
  locally yet.

### 3. The watchlist DTO had a package/path mismatch and broke startup
At one point the backend would not even start because `WatchlistResponseDto`
was physically in the wrong source folder.

The file declared the `dto.response` package but lived under `dto.request`.
That created a classpath mismatch and caused startup errors.

Why this matters:

- In Java, source layout consistency matters.
- A package declaration and file path mismatch can create confusing runtime or
  incremental-build behavior, especially in IDE builds.

Fix:

- The DTO was moved into the correct `dto/response` source path.

### 4. The movie-details service used a read-only transaction even though it can write
The detail flow uses a DB-first cache strategy:

- Check local DB
- If stale or missing, call TMDB
- Save/update locally
- Return the result

The `getMovieDetailsDto()` method was marked `@Transactional(readOnly = true)`
even though its cache-miss path can write to the database.

Why this matters:

- The transaction declaration did not match the actual behavior.
- That increases the chance of subtle persistence and flush problems.
- In interviews, this is a great example of why transaction boundaries should
  reflect real write behavior.

Fix:

- The detail path was changed to a normal transactional method.

### 5. Duplicate detail requests caused unstable behavior in development
At one stage, the movie page briefly rendered correctly and then switched to
`Movie not found`.

That symptom suggested an important clue:

- One request was probably succeeding.
- A later request was probably failing and overwriting the good state.

Why this mattered:

- Flash-then-fail often points to duplicate requests, race conditions, or stale
  state being overwritten.
- This is very different from a simple routing bug.

Fix:

- The frontend detail page was hardened so a same-movie failure would not
  overwrite already loaded valid data.
- The backend detail path was also hardened to reduce refresh-path races.

### 6. The real backend discrepancy was in Redis caching for movie details
This was the most important finding.

The investigation eventually showed:

- `/api/movies/trending` worked
- `/api/reviews/movie/{id}` worked
- `/api/movies/{id}` returned `500` for multiple movie IDs

That meant the issue was specifically in the movie-details path.

Then Redis was inspected directly.
Cached `movieDetails::...` entries were found for failing movie IDs, and the
cached JSON looked like this conceptually:

```json
"genres": ["java.util.ImmutableCollections$ListN", ["Animation", "Comedy"]]
```

That is a backend implementation detail leaking into cached payload shape.

Why this matters:

- The details endpoint was caching `MovieResponseDto`.
- Its `genres` list was produced via `stream().toList()`.
- In modern Java, `toList()` produces an immutable list implementation.
- That immutable implementation leaked into the Redis serializer metadata.

Even worse, the detail endpoint was the only path using that cache pattern, so
the bug looked like a general detail-page failure while trending/search kept
working.

Fix:

- Movie-details caching was removed for now to restore correctness.
- The `genres` list was normalized to a regular `ArrayList`.

This was the key "discrepancy between frontend and backend" moment:

- The frontend was not the primary problem.
- The backend detail endpoint was failing before it could produce a reliable
  response.

### 7. Missing poster images were mostly a data-quality problem, not a routing bug
Some search results had no poster because TMDB returns `poster_path = null`
for some movies.

There was also a frontend UX problem:

- The app did not consistently fall back to the placeholder poster image.

Fix:

- The movie card was updated to use a stable placeholder fallback.
- An image error fallback was added too.

## How the Investigation Narrowed Down the Problem

### Step 1: Trace the UI route
The first step was to identify:

- which page rendered `Movie not found`
- which route params it used
- which API functions it called

This ruled out random guessing.

### Step 2: Compare frontend behavior with backend behavior
The next step was to ask:

- Is the frontend calling the wrong ID?
- Or is the backend rejecting a correct request?

Direct API checks showed that the detail endpoint itself was returning `500`.
That immediately changed the problem from:

- "frontend routing bug"

to:

- "backend detail-path failure that the frontend was masking"

This is a crucial debugging skill:
always translate a vague UI symptom into a concrete HTTP/API fact.

### Step 3: Compare working and failing endpoints
This comparison was extremely helpful:

- `trending` worked
- `reviews` worked
- `movie details` failed

That isolated the problem to the detail path instead of the whole movie system.

### Step 4: Look at stored data, not just live responses
When the detail endpoint kept failing generically, the next useful place to
look was Redis.

That exposed the unexpected serialized list type in cached movie detail values.

Lesson:

- If a bug appears after data crosses process boundaries, inspect the stored
  representation directly.
- Serialization bugs often become obvious once you look at the raw cached
  payload.

### Step 5: Improve observability, not just logic
Another problem was that the global exception handler swallowed the real error
and returned only a generic `500`.

That made debugging slower than it needed to be.

Fix:

- The global exception handler was updated to log the full exception stack trace
  while still returning a safe generic API message.

Lesson:

- Good systems are not just correct.
- They are also diagnosable.

## Concrete Fixes That Were Applied

### Frontend
- Decoupled movie-details and reviews loading behavior.
- Prevented same-ID failures from overwriting an already loaded movie.
- Added better poster fallback behavior.

### Backend
- Changed reviews lookup for missing local movies to return an empty list.
- Fixed the misplaced `WatchlistResponseDto` source file.
- Corrected the transaction behavior on the detail path.
- Flushed old genre mappings before reinserting refreshed genre rows.
- Removed movie-details caching for now.
- Normalized cached/detail genre lists to regular list types.
- Added real logging for unhandled exceptions.

## What This Teaches for Future Projects

### 1. Do not collapse all failures into one UI state
`Movie not found` should mean exactly that.
It should not also mean:

- review fetch failed
- backend returned `500`
- cache deserialization failed

Good debugging starts with good error separation.

### 2. Cache only data shapes you fully control
Caching DTOs can be good, but only if:

- the DTO shape is stable
- serialization is predictable
- nested collections use safe concrete types

Immutable Java collection implementations can be fine in memory but become
surprising when serialized across service boundaries.

### 3. Compare "working path" vs "failing path"
This is one of the highest-signal debugging moves.

Instead of staring only at the broken endpoint, compare it to the similar path
that still works.

In this case:

- trending path worked
- details path failed

That narrowed the search dramatically.

### 4. Improve logging early when the system is opaque
If the app hides the exception, fix observability before making too many logic
changes.

Otherwise you risk debugging by guesswork.

### 5. Race-condition symptoms often look like UI bugs
When the UI:

- flashes correct content
- then becomes incorrect

that often means:

- duplicate requests
- stale state overwrite
- backend race
- cache timing issue

not just a bad route.

## How to Explain This in a Technical Interview

A strong interview answer would sound like this:

> I started by reproducing the issue and tracing the exact failing user flow.
> Then I separated frontend state handling from backend API behavior by testing
> the detail endpoint directly. I found that the UI symptom was misleading:
> the real failure was a backend `500` in the movie-details path. I compared
> that against working endpoints like trending and reviews, which narrowed the
> issue to the detail-specific implementation. I then inspected Redis and found
> that cached movie detail payloads contained immutable Java list metadata in
> the `genres` field. That told me the cache serialization strategy was not
> safe for that DTO shape. I fixed the issue by removing detail caching for now,
> normalizing collection types, hardening the frontend against duplicate request
> overwrites, and improving backend exception logging so future issues would be
> easier to diagnose.

This answer is strong because it shows:

- methodical debugging
- cross-layer reasoning
- understanding of caching and serialization
- practical tradeoff thinking
- concern for observability, not just correctness

## Reusable Debugging Checklist

When a similar bug happens in the future, use this checklist:

1. Reproduce the exact user flow.
2. Identify the exact page/component and route params involved.
3. Identify every API call made by that page.
4. Test those API calls directly outside the frontend.
5. Compare working endpoints vs failing endpoints.
6. Inspect cache/storage if the behavior looks inconsistent.
7. Check whether the error is:
   - routing
   - data retrieval
   - serialization
   - caching
   - state overwrite
   - transaction boundary mismatch
8. Improve logging if the true exception is hidden.
9. Fix the smallest confirmed problem first.
10. Retest after each fix instead of batching guesses.

## Final Takeaway
The hardest part of this issue was not the code change itself.
The hardest part was that the user-facing symptom was misleading.

The real lesson is this:

**Good debugging is not about guessing the bug quickly.**

It is about shrinking the search space with evidence until the bug becomes
obvious.

