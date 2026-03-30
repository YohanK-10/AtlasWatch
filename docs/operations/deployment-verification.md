# Deployment Verification

## Purpose
Use this checklist after deploying AtlasWatch to verify the MVP is healthy.

## Automated Check
- `GET /api/health`

Expected response:
- HTTP `200` when the app, database, and Redis are reachable
- JSON containing:
  - overall `status`
  - `database`
  - `redis`
  - `timeStamp`

## Manual Smoke Test

### Backend
- Open `/api/health`
- Open `/api/movies/trending`
- Open `/api/movies/{tmdbId}` for a known movie
- Open `/api/reviews/movie/{tmdbId}`

### Frontend
- Register
- Verify account
- Log in
- Load homepage
- Search for a movie
- Open movie details
- Add a review
- Add to watchlist
- Log out

## Failure Triage
- If `/api/health` is down, check backend, DB, and Redis connectivity first.
- If `/api/movies/trending` works but `/api/movies/{tmdbId}` fails, inspect the
  movie-details service path and recent cache/data changes.
- If auth fails only in production, verify cookie security, HTTPS, and CORS
  configuration.

