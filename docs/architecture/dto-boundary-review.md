# DTO Boundary Review

## Purpose
This document records the API response review for AtlasWatch so it is clear
which objects cross the HTTP boundary and why.

## Review Result
The controller layer does **not** return JPA entities directly.

Current controller responses use:
- `UserResponseDto` for user registration responses
- `MovieResponseDto` for movie detail and local-search responses
- `ReviewResponseDto` for review responses
- `WatchlistResponseDto` for watchlist responses
- `GenreResponseDto` for genre list responses
- `SearchResponseDto` / `MovieDto` for TMDB search and trending payloads
- `ErrorResponse` for API error bodies
- simple JSON maps for operational endpoints such as `/api/health`

## Why This Matters
Returning entities directly is risky because it can:
- expose internal fields unintentionally
- leak persistence structure into the API contract
- create serialization problems with lazy relationships
- make refactors harder because DB shape and API shape become coupled

## Sensitive Field Review

### User Data
`AuthController` returns `UserResponseDto` on registration instead of returning
the `User` entity. This avoids leaking fields such as:
- password hash
- verification code internals
- security/account state fields not needed by clients

`AuthControllerTest` includes a serialization assertion that the registration
response does not contain a password field.

### Movie, Review, and Watchlist Data
Movie, review, and watchlist endpoints return dedicated DTOs rather than
entities from the `models` package. This keeps:
- entity relationships out of the API surface
- user ownership internals out of JSON responses
- serialization predictable for the frontend

## Tradeoff Note
`SearchResponseDto` is passed through from the TMDB-shaped response because it
already matches the frontend needs for search/trending lists and avoids an
unnecessary local remapping layer for paginated lists.

That is still a DTO boundary, not an entity leak.

## MVP Guidance
Keep the current rule:
- controllers may return DTOs, error DTOs, or explicit operational maps
- controllers should not return JPA entities directly

If new endpoints are added later, treat DTO creation as part of the endpoint
definition rather than something to retrofit after the fact.
