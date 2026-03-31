# AtlasWatch: Context-Aware Hybrid Recommendation Engine

## Context

The current `RecommendationService` only recommends movies from the user's watchlist. This limits its usefulness: users must already know movies to add to their watchlist before the system can help them choose. The real problem is **decision fatigue** — users want to be told "watch this tonight" from the entire catalog, personalized to their current mood and taste, without manually browsing hundreds of options.

This overhaul transforms AtlasWatch into a **full-catalog, context-aware hybrid recommendation engine** following the industry-standard pipeline: **Candidate Generation -> Feature Extraction -> Scoring -> Ranking -> Explanation**. This is the same architectural pattern used in credit decisioning (banks), search ranking (Google), and production recommendation systems (Netflix, Spotify).

Movies from the user's watchlist still participate in recommendations but are annotated with a badge rather than being the sole source. Cold-start users (no watchlist, no reviews) still receive quality recommendations based on popularity and ratings.

---

## Critical Files Reference

**Backend (modify):**
- `src/main/java/.../service/RecommendationService.java` — current scoring engine (refactor to delegate)
- `src/main/java/.../controller/RecommendationController.java` — add new endpoint
- `src/main/java/.../repository/MovieRepository.java` — add candidate queries
- `src/main/java/.../repository/MovieGenreRepository.java` — verify batch genre fetch
- `src/main/java/.../repository/ReviewRepository.java` — add batch + count queries
- `src/main/java/.../repository/WatchlistRepository.java` — add batch + count queries
- `src/main/java/.../service/ReviewService.java` — add cache eviction
- `src/main/java/.../service/WatchlistService.java` — add cache eviction
- `src/main/java/.../service/MovieService.java` — trigger TF-IDF re-index
- `src/main/java/.../config/RedisConfig.java` — add new cache entries
- `src/main/java/.../config/SecurityConfiguration.java` — permit cold-start endpoint
- `pom.xml` — add Apache Commons Math

**Backend (new):**
- `src/main/java/.../service/recommendation/` — entire new package (~30 files)

**Frontend (modify):**
- `moviehub-frontend/app/(app)/pick-for-me/page.tsx` — redesign to use full-catalog recs
- `moviehub-frontend/lib/api.ts` — add new API functions
- `moviehub-frontend/lib/types.ts` — add new interfaces
- `moviehub-frontend/components/MovieCard.tsx` — add watchlist badge prop
- `moviehub-frontend/components/Navbar.tsx` — add "For You" nav link

**Frontend (new):**
- `moviehub-frontend/components/RecommendationCard.tsx` — new card with reasons/sources

---

## Phase 0: Dependencies & Infrastructure

- [ ] **0.1** Add `org.apache.commons:commons-math3:3.6.1` to `pom.xml` for cosine similarity and vector math
- [ ] **0.2** Add a `"recommendations"` cache entry in `RedisConfig.java` with 5-minute TTL, alongside existing `"trending"` and `"movieDetails"` entries
- [ ] **0.3** Add a `"userProfiles"` cache entry in `RedisConfig.java` with 10-minute TTL

---

## Phase 1: Database Layer — New Queries for Full-Catalog Candidate Generation
> *Paper: "A Survey of Collaborative Filtering Techniques" (Su & Khoshgoftaar 2009) — efficient candidate retrieval is foundational to any recommendation system*

- [ ] **1.1** Add `findIdsByGenreIds(Collection<Long> genreIds)` native query to `MovieRepository` — returns `List<Long>` movie IDs where movie has at least one of the given genres (JOIN on `movie_genre` table)
- [ ] **1.2** Add `findTopByPopularity(Pageable pageable)` query to `MovieRepository` — returns `List<Movie>` ordered by `popularity DESC` for cold-start candidate generation
- [ ] **1.3** Add `findTopByRating(Double minRating, Pageable pageable)` query to `MovieRepository` — returns `List<Movie>` where `movieRating >= minRating` ordered by `movieRating DESC`
- [ ] **1.4** Add `findByIdIn(Collection<Long> ids)` batch-fetch query to `MovieRepository` — loads full Movie entities from a set of IDs after candidate generation narrows them down
- [ ] **1.5** Verify `findByMovieIdInWithGenre(Collection<Long>)` exists in `MovieGenreRepository` — needed for batch genre loading across all candidate movies (already exists in current code)
- [ ] **1.6** Add `findByUserIdAndMovieIdIn(Long userId, Collection<Long> movieIds)` query to `WatchlistRepository` — batch-checks which recommended movies are on the user's watchlist
- [ ] **1.7** Add `findByUserIdAndMovieIdIn(Long userId, Collection<Long> movieIds)` query to `ReviewRepository` — batch-checks which candidates the user already reviewed
- [ ] **1.8** Add `countByUserId(Long userId)` to `WatchlistRepository` — detects cold-start state (0 entries)
- [ ] **1.9** Add `countByUserId(Long userId)` to `ReviewRepository` — detects cold-start state (0 reviews)

---

## Phase 2: User Taste Profile
> *Paper: "Content-Based Recommendation Systems" (Pazzani & Billsus 2007) — building user profiles from implicit + explicit feedback signals*
> *Paper: "Matrix Factorization Techniques for Recommender Systems" (Koren, Bell, Volinsky 2009) — implicit feedback signals from user behavior*

- [ ] **2.1** Create `UserTasteProfile` record in `service/recommendation/` — fields: `Map<String, Double> genreWeights`, `Set<Long> watchedMovieIds`, `Set<Long> watchlistMovieIds`, `double averageRating`, `int totalReviews`, `int totalWatchlistEntries`, `boolean isColdStart`
- [ ] **2.2** Create `UserTasteProfileBuilder` `@Component` in `service/recommendation/` — inject `WatchlistRepository`, `ReviewRepository`, `MovieGenreRepository`
- [ ] **2.3** Implement `computeGenreWeightsFromReviews()` method — for each review, distribute `(rating / 10.0)` weight across the movie's genres. A user who rated 3 Action movies 9/10 accumulates high Action weight
- [ ] **2.4** Implement `computeGenreWeightsFromWatchlist()` method — PLAN_TO_WATCH adds +0.3 weight per genre, WATCHING adds +0.5. Captures intent without requiring reviews
- [ ] **2.5** Implement `normalizeWeights()` method — divide all weights by the maximum, producing [0.0, 1.0] range so profiles are comparable across users with different activity levels
- [ ] **2.6** Implement `buildProfile(User)` method — orchestrates: fetch reviews, fetch watchlist, fetch genre mappings, compute weights from both sources, normalize, compute averageRating, determine cold-start flag, return `UserTasteProfile`
- [ ] **2.7** Add `@Cacheable(value = "userProfiles", key = "#user.id")` to `buildProfile` — cache for 10 minutes to avoid rebuilding on every recommendation request
- [ ] **2.8** Add `@CacheEvict(value = "userProfiles", key = "#user.id")` trigger in `ReviewService` after `createReview`, `updateReview`, `deleteReview` — invalidate stale profile on review changes
- [ ] **2.9** Add `@CacheEvict(value = "userProfiles")` trigger in `WatchlistService` after add/update/remove — invalidate stale profile on watchlist changes

---

## Phase 3: TF-IDF Content Similarity Engine
> *Paper: "Term-weighting approaches in automatic text retrieval" (Salton & Buckley 1988) — TF-IDF term weighting*
> *Paper: "Content-Based Recommendation Systems" (Pazzani & Billsus 2007) — using text features for content-based filtering*

- [ ] **3.1** Create `TfIdfEngine` `@Component` in `service/recommendation/` — holds in-memory index: `Map<Long, double[]> movieVectors`, `String[] vocabulary`, `Map<String, Integer> termIndex`, `ReadWriteLock` for thread safety
- [ ] **3.2** Implement `tokenize(String text)` method — lowercase, remove punctuation, split on whitespace, remove English stop words (inline static set of ~175 common words)
- [ ] **3.3** Implement `computeTf(List<String> tokens)` method — TF(term) = count(term) / totalTokens (normalized term frequency)
- [ ] **3.4** Implement `computeIdf(Map<String, Integer> docFrequency, int totalDocs)` method — IDF(term) = log(totalDocs / (1 + docsContaining(term)))
- [ ] **3.5** Implement `buildVector(Map<String, Double> tf, Map<String, Double> idf)` method — produces dense double[] where each dimension is TF * IDF for the vocabulary term at that index
- [ ] **3.6** Implement `cosineSimilarity(double[] a, double[] b)` method — dot(a,b) / (norm(a) * norm(b)) using Apache Commons Math. Return 0.0 if either vector is zero-norm
- [ ] **3.7** Implement `buildIndex()` method with `@PostConstruct` — loads all movies with overviews from DB, computes TF-IDF vectors, stores in `movieVectors` map. Runs on startup
- [ ] **3.8** Implement `findSimilar(Long movieId, int topK)` method — computes cosine similarity against all other vectors, returns top K most similar movie IDs with scores
- [ ] **3.9** Implement `findSimilarToProfile(Set<Long> likedMovieIds, int topK)` method — averages TF-IDF vectors of liked movies to build a "user centroid" vector, then finds top K closest movies to that centroid
- [ ] **3.10** Implement `@Scheduled(fixedRate = 3600000)` re-index method — rebuilds index hourly using `ReadWriteLock.writeLock()` so read queries are not blocked during rebuild

---

## Phase 4: Candidate Generation Pipeline
> *Paper: "Wide & Deep Learning for Recommender Systems" (Cheng et al. 2016, Google) — multi-channel candidate generation where each channel contributes candidates from a different signal*

- [ ] **4.1** Create `CandidateSource` enum in `service/recommendation/` — values: `GENRE_MATCH`, `CONTENT_SIMILARITY`, `POPULARITY`, `HIGH_RATED`, `WATCHLIST`
- [ ] **4.2** Create `RecommendationCandidate` record — fields: `Long movieId`, `Movie movie`, `List<String> genres`, `Set<CandidateSource> sources`, `Map<String, Double> features`
- [ ] **4.3** Create `CandidateGenerator` interface — method: `List<RecommendationCandidate> generate(User user, UserTasteProfile profile, RecommendationContext context, int maxCandidates)`
- [ ] **4.4** Implement `GenreMatchCandidateGenerator` — finds movies sharing genres with user's top 3-5 weighted genres. Uses `MovieRepository.findIdsByGenreIds()`. Tags with `GENRE_MATCH` source
- [ ] **4.5** Implement `ContentSimilarityCandidateGenerator` — uses `TfIdfEngine.findSimilarToProfile()` to find movies with overview text similar to user's liked movies. Tags with `CONTENT_SIMILARITY` source
- [ ] **4.6** Implement `PopularityCandidateGenerator` — fetches top-N popular movies via `MovieRepository.findTopByPopularity()`. Primary cold-start channel. Tags with `POPULARITY` source
- [ ] **4.7** Implement `HighRatedCandidateGenerator` — fetches top-N highest-rated movies (>= 7.5). Another cold-start-friendly channel. Tags with `HIGH_RATED` source
- [ ] **4.8** Implement `WatchlistCandidateGenerator` — fetches user's PLAN_TO_WATCH/WATCHING items. Tags with `WATCHLIST` source. Preserves current watchlist-aware behavior as one signal
- [ ] **4.9** Create `CandidateMerger` utility — `merge(List<List<RecommendationCandidate>>)` deduplicates by movieId, unions sources from multiple channels (a movie from 3 channels gets all 3 sources listed)
- [ ] **4.10** Add post-merge filter: exclude movies with status `WATCHED` — batch-check via `WatchlistRepository.findByUserIdAndMovieIdIn()`
- [ ] **4.11** Add post-merge filter: exclude movies user already reviewed (configurable flag `excludeReviewed`, default false)

---

## Phase 5: Feature Extraction
> *Paper: "Wide & Deep Learning for Recommender Systems" (Cheng et al. 2016) — the "Wide" component is a linear model over engineered features*
> *Paper: "Context-Aware Recommender Systems" (Adomavicius & Tuzhilin 2011) — incorporating contextual information (mood, time) as features*

- [ ] **5.1** Create `FeatureExtractor` interface — method: `void extract(RecommendationCandidate candidate, UserTasteProfile profile, RecommendationContext context)` — populates the candidate's `features` map
- [ ] **5.2** Implement `GenreOverlapFeatureExtractor` — `genreOverlap` = (count of movie genres in user's top genres) / (total movie genres). Range [0.0, 1.0]
- [ ] **5.3** Implement `ContentSimilarityFeatureExtractor` — `contentSimilarity` = cosine similarity between movie's TF-IDF vector and user's centroid vector. Range [0.0, 1.0]. Uses `TfIdfEngine`
- [ ] **5.4** Implement `MoodMatchFeatureExtractor` — `moodMatch` = proportion of movie genres mapping to user's selected moods. Reuses existing `SoloMood.preferredGenres()` mapping. Range [0.0, 1.0]
- [ ] **5.5** Implement `RuntimeMatchFeatureExtractor` — `runtimeMatch` = 1.0 if movie runtime matches user preference, 0.0 otherwise. Reuses existing `RuntimePreference.matches()` logic
- [ ] **5.6** Implement `QualityFeatureExtractor` — `quality` = movieRating / 10.0; `popularity` = min(1.0, movie.popularity / 500.0). Both normalized to [0.0, 1.0]
- [ ] **5.7** Implement `FreshnessFeatureExtractor` — `recency` = max(0, 1.0 - yearsOld / 10.0). Gentle bias toward newer movies without excluding classics
- [ ] **5.8** Implement `WatchlistContextFeatureExtractor` — `isOnWatchlist` (1.0/0.0), `watchlistAgeDays`, `watchlistStatusWeight` (PLAN_TO_WATCH=0.8, WATCHING=1.0, not on list=0.0)
- [ ] **5.9** Implement `SourceCountFeatureExtractor` — `sourceCount` = number of candidate channels that nominated this movie / 5.0 (normalized). Multi-signal bonus
- [ ] **5.10** Create `FeatureExtractionPipeline` orchestrator — holds `List<FeatureExtractor>` (Spring-injected), iterates all candidates and applies each extractor

---

## Phase 6: Scoring & Ranking
> *Paper: "Wide & Deep Learning for Recommender Systems" (Cheng et al. 2016) — weighted linear combination as the "Wide" scoring baseline*
> *Paper: "Neural Collaborative Filtering" (He et al. 2017) — future extension point for nonlinear scoring*

- [ ] **6.1** Create `ScoringWeights` `@ConfigurationProperties(prefix = "atlaswatch.recommendation.weights")` class — fields with defaults: `genreOverlap=25`, `contentSimilarity=20`, `moodMatch=20`, `runtimeMatch=8`, `quality=10`, `freshness=5`, `watchlistBoost=7`, `sourceCountBonus=5`
- [ ] **6.2** Add `atlaswatch.recommendation.weights.*` default entries to `application.properties`
- [ ] **6.3** Add `atlaswatch.recommendation.pool-size=200` property — caps total candidates before scoring
- [ ] **6.4** Create `ScoredRecommendation` record — fields: `RecommendationCandidate candidate`, `double score`, `Map<String, Double> featureBreakdown` (each feature's weighted contribution for explainability)
- [ ] **6.5** Create `RecommendationScorer` `@Component` — method: `double score(RecommendationCandidate, ScoringWeights)` computes weighted linear combination: Score = sum(feature_i * weight_i) for all features
- [ ] **6.6** Implement cold-start weight adjustment in scorer — when `UserTasteProfile.isColdStart()`, zero out `genreOverlap` and `contentSimilarity` weights, boost `quality` and `popularity`
- [ ] **6.7** Create `RecommendationRanker` `@Component` — scores all candidates, sorts descending, takes top N with diversity re-ranking (if 2 consecutive movies share all genres, swap the second with the next different-genre movie)

---

## Phase 7: Explanation Generation
> *Paper: "Explaining Recommendations: Satisfaction vs. Promotion" (Bilgic & Mooney 2005) — transparent explanations increase user trust and satisfaction*

- [ ] **7.1** Create `RecommendationExplainer` `@Component` — method: `List<String> explain(ScoredRecommendation, UserTasteProfile, RecommendationContext)` — generates 1-3 human-readable reasons
- [ ] **7.2** Implement genre-overlap explanation — fires when `genreOverlap >= 0.5`: "Matches genres you enjoy like {topMatchingGenres}"
- [ ] **7.3** Implement content-similarity explanation — fires when `contentSimilarity >= 0.3`: "Its storyline has a lot in common with movies you have rated highly"
- [ ] **7.4** Implement mood-match explanation — fires when `moodMatch >= 0.5`: "Fits your {moodLabels} mood through its {genres} elements"
- [ ] **7.5** Implement quality explanation — fires when `quality >= 0.75`: "One of the higher-rated movies in the catalog at {rating}/10"
- [ ] **7.6** Implement watchlist explanation — fires when `isOnWatchlist`: "Already on your watchlist — a sign you were already interested". If `watchlistAgeDays >= 60`: "Has been on your watchlist for {days} days, might be a good time to finally watch it"
- [ ] **7.7** Implement multi-signal explanation — fires when `sourceCount >= 3`: "Multiple signals point to this movie — genre match, past ratings, and strong reviews"
- [ ] **7.8** Implement cold-start explanation — fires when `isColdStart`: "Since you are new, we are showing highly-rated popular movies. Rate a few to get more personal picks"
- [ ] **7.9** Implement fallback explanation — if no specific reason fired: "A strong movie that stood out in the current catalog"

---

## Phase 8: DTOs and Context Model

- [ ] **8.1** Create `RecommendationContext` record in `service/recommendation/` — fields: `Set<String> moods`, `String runtimePreference`, `int limit`, `boolean watchlistOnly`
- [ ] **8.2** Create `RecommendationRequestDto` in `dto/request/` — fields: `List<String> moods`, `String runtimePreference`, `Integer limit` (default 5), `Boolean watchlistOnly` (default false)
- [ ] **8.3** Create `RecommendationResponseDto` in `dto/response/` with `@Builder` — fields: `tmdbId`, `movieTitle`, `movieOverview`, `posterPath`, `backdropPath`, `releaseDate`, `rating`, `runtime`, `popularity`, `List<String> genres`, `boolean onWatchlist` (NEW), `String watchlistStatus` (null if not on watchlist), `double score`, `List<String> reasons`, `List<String> sources`
- [ ] **8.4** Mark existing `SoloRecommendationRequestDto` and `SoloRecommendationResponseDto` as `@Deprecated` — keep for backward compatibility during transition

---

## Phase 9: Pipeline Orchestrator

- [ ] **9.1** Extract `SoloMood` enum from `RecommendationService` into standalone `service/recommendation/SoloMood.java` — both old service and new pipeline can share it
- [ ] **9.2** Extract `RuntimePreference` enum from `RecommendationService` into standalone `service/recommendation/RuntimePreference.java`
- [ ] **9.3** Update imports in `RecommendationService` to reference the extracted enums
- [ ] **9.4** Create `RecommendationPipeline` `@Service` in `service/recommendation/` — inject: `UserTasteProfileBuilder`, all `CandidateGenerator` implementations (via `List<CandidateGenerator>`), `CandidateMerger`, `FeatureExtractionPipeline`, `RecommendationRanker`, `RecommendationExplainer`, `ScoringWeights`
- [ ] **9.5** Implement `recommend(User, RecommendationContext)` method — full pipeline: (1) build taste profile, (2) generate candidates in parallel via `CompletableFuture`, (3) merge, (4) extract features, (5) score + rank, (6) explain, (7) annotate watchlist badges, (8) map to DTOs
- [ ] **9.6** Implement parallel candidate generation — run all generators concurrently using `CompletableFuture.supplyAsync()`, wait with `CompletableFuture.allOf()`
- [ ] **9.7** Implement watchlist badge annotation — after ranking, batch-fetch `WatchlistRepository.findByUserIdAndMovieIdIn(userId, rankedMovieIds)`, build `Map<Long, WatchList>`, set `onWatchlist`/`watchlistStatus` on each DTO
- [ ] **9.8** Add `@Cacheable(value = "recommendations")` on `recommend()` — cache key: `userId + hash(moods + runtime + limit)`, 5-minute TTL
- [ ] **9.9** Refactor `RecommendationService.getSoloRecommendations()` to delegate to `RecommendationPipeline.recommend()` with `watchlistOnly=true`, mapping output back to `SoloRecommendationResponseDto` for backward compatibility

---

## Phase 10: Controller Layer

- [ ] **10.1** Add `POST /api/recommendations` endpoint in `RecommendationController` — accepts `RecommendationRequestDto`, returns `List<RecommendationResponseDto>`, requires authentication
- [ ] **10.2** Add `GET /api/recommendations/cold-start` endpoint — returns popular + highly-rated movies for unauthenticated or cold-start users, no auth required
- [ ] **10.3** Add `"/api/recommendations/cold-start"` to `SecurityConfiguration.requestMatchers().permitAll()` — public access for cold-start
- [ ] **10.4** Keep existing `POST /api/recommendations/solo` endpoint working unchanged — internally delegates to new pipeline with watchlist-only flag

---

## Phase 11: Frontend Types & API Client

- [ ] **11.1** Add `RecommendationResponse` TypeScript interface to `lib/types.ts` — includes `onWatchlist: boolean`, `watchlistStatus: WatchlistStatus | null`, `score: number`, `reasons: string[]`, `sources: string[]`, plus all movie fields
- [ ] **11.2** Add `RecommendationRequest` TypeScript interface to `lib/types.ts` — `moods: string[]`, `runtimePreference: string`, `limit: number`, `watchlistOnly: boolean`
- [ ] **11.3** Add `getRecommendations(body: RecommendationRequest)` function to `lib/api.ts` — calls `POST /api/recommendations`
- [ ] **11.4** Add `getColdStartRecommendations()` function to `lib/api.ts` — calls `GET /api/recommendations/cold-start`
- [ ] **11.5** Keep existing `getSoloRecommendations()` function in `lib/api.ts` untouched

---

## Phase 12: Frontend — Watchlist Badge on MovieCard

- [ ] **12.1** Add `onWatchlist?: boolean` prop to `MovieCard` component's `Props` interface
- [ ] **12.2** Render a small bookmark badge overlay on the poster when `onWatchlist` is true — use green accent pill ("On your list") positioned at top-right of the poster

---

## Phase 13: Frontend — RecommendationCard Component

- [ ] **13.1** Create `RecommendationCard.tsx` component — accepts `RecommendationResponse` as props, renders movie poster, title, rating, genres, release year
- [ ] **13.2** Add "Why this movie?" section — renders up to 3 reason strings as bullet points with amber dots (reusing existing pattern from pick-for-me page)
- [ ] **13.3** Add source pills row — render `sources` as small colored pills below genres (e.g., "Genre match" in blue, "Story similarity" in purple, "Popular" in amber, "On your list" in green)
- [ ] **13.4** Add watchlist badge — when `onWatchlist` is true, show a green "On your watchlist" badge
- [ ] **13.5** Add "hero" vs "compact" layout mode prop — hero mode for the top pick (large poster + full details), compact mode for remaining picks (small poster + condensed info)

---

## Phase 14: Frontend — Redesigned Pick-for-Me Page

- [ ] **14.1** Replace `getSoloRecommendations()` call with `getRecommendations({ watchlistOnly: false })` — full-catalog recommendations
- [ ] **14.2** Handle unauthenticated state — call `getColdStartRecommendations()` instead and show message: "Sign in and rate some movies for personalized picks"
- [ ] **14.3** Handle cold-start (authenticated but empty results) — show cold-start explanation with actionable links to trending/search
- [ ] **14.4** Replace inline top-pick article with `RecommendationCard` in hero mode
- [ ] **14.5** Replace inline remaining-picks articles with `RecommendationCard` in compact mode
- [ ] **14.6** Update page header copy — change "Watchlist triage" to "Recommended for you" and update description to reflect full-catalog recommendations
- [ ] **14.7** Update empty-state message — change "No watchlist candidates yet" to "Browse trending movies, add some to your watchlist, or rate a few films — AtlasWatch learns from every signal"
- [ ] **14.8** Add "Watchlist only" toggle — a checkbox that sets `watchlistOnly: true` in the request, giving users the option to restrict recommendations to their watchlist
- [ ] **14.9** Add "For You" link to Navbar's `PRIVATE_NAV_LINKS` array — make the recommendation page easily discoverable

---

## Phase 15: Testing

- [ ] **15.1** Unit test `UserTasteProfileBuilder` — user with reviews and watchlist entries produces correct genre weights and averageRating
- [ ] **15.2** Unit test `UserTasteProfileBuilder` — cold-start user (no reviews, no watchlist) produces `isColdStart = true`
- [ ] **15.3** Unit test `TfIdfEngine.tokenize()` — verify stop word removal, punctuation stripping, lowercase normalization
- [ ] **15.4** Unit test `TfIdfEngine.cosineSimilarity()` — identical vectors return 1.0, orthogonal return 0.0
- [ ] **15.5** Unit test `TfIdfEngine.findSimilar()` — 5-movie index, verify most textually similar movie is returned first
- [ ] **15.6** Unit test `RecommendationScorer` — known feature values produce expected weighted sum
- [ ] **15.7** Unit test `RecommendationScorer` cold-start mode — genre/content weights zeroed, quality/popularity boosted
- [ ] **15.8** Unit test `RecommendationExplainer` — verify each template fires at correct threshold
- [ ] **15.9** Unit test `CandidateMerger` — deduplication works, sources are unioned, WATCHED movies filtered
- [ ] **15.10** Integration test `RecommendationPipeline.recommend()` — end-to-end with test data: user + watchlist + reviews -> full pipeline -> response includes watchlist badges and explanations
- [ ] **15.11** Controller test `POST /api/recommendations` — returns 200 with valid JSON including `onWatchlist` flag and `reasons`
- [ ] **15.12** Controller test `GET /api/recommendations/cold-start` — returns results without authentication

---

## Phase 16: Cleanup & Documentation

- [ ] **16.1** Add Javadoc citations on each major class referencing the paper that inspired it (e.g., `/** See: Salton & Buckley, 1988 */` on `TfIdfEngine`)
- [ ] **16.2** Add request timing logs — `StopWatch` in `RecommendationPipeline` logging per-stage time at DEBUG level
- [ ] **16.3** Remove any dead code in old `RecommendationService` that is now fully handled by the pipeline

---

## Research Paper → Feature Mapping

| Paper | Year | What it inspires in this plan |
|---|---|---|
| Salton & Buckley, "Term-weighting approaches in automatic text retrieval" | 1988 | TF-IDF engine (Phase 3), content similarity feature (5.3) |
| Pazzani & Billsus, "Content-Based Recommendation Systems" | 2007 | User taste profile from reviews (Phase 2), genre + text features |
| Bilgic & Mooney, "Explaining Recommendations: Satisfaction vs. Promotion" | 2005 | Explanation generation (Phase 7), diversity re-ranking (6.7) |
| Su & Khoshgoftaar, "A Survey of Collaborative Filtering Techniques" | 2009 | Candidate retrieval patterns (Phase 1), cold-start handling |
| Koren, Bell, Volinsky, "Matrix Factorization Techniques for Recommender Systems" | 2009 | Implicit feedback signals (2.3-2.4), future collaborative filtering extension |
| Adomavicius & Tuzhilin, "Context-Aware Recommender Systems" | 2011 | Mood/context features (5.4), context model (8.1), cold-start fallback (6.6) |
| Cheng et al., "Wide & Deep Learning for Recommender Systems" (Google) | 2016 | Multi-channel candidate generation (Phase 4), weighted linear scoring (Phase 6), feature engineering pipeline (Phase 5) |
| He et al., "Neural Collaborative Filtering" | 2017 | Future extension point — nonlinear scoring model replacing the linear scorer |

---

## Verification Plan

1. **Backend unit tests**: Run `./mvnw test` — all new tests in Phase 15 pass
2. **Backend integration**: Start Docker stack (`docker compose up`), call `POST /api/recommendations` with Postman/curl:
   - Authenticated user with watchlist + reviews → personalized results with `onWatchlist` badges
   - Authenticated user with empty watchlist → cold-start results with appropriate explanations
   - `GET /api/recommendations/cold-start` without auth → popular/highly-rated results
3. **Backward compatibility**: Call `POST /api/recommendations/solo` → still returns watchlist-only results in old format
4. **Frontend**: Navigate to `/pick-for-me`:
   - While logged in with data → see personalized full-catalog picks with reasons and watchlist badges
   - While logged in with no data → see cold-start message with popular picks
   - While logged out → see cold-start picks with sign-in prompt
   - Toggle "Watchlist only" → results narrow to watchlist items only
5. **Cache behavior**: Make 2 identical requests within 5 minutes — second should be near-instant (Redis hit)
6. **TF-IDF**: Check startup logs for index build message; verify content-similarity explanations appear for users with review history
