package io.github.thacbao.itemcf.api;

import io.github.thacbao.itemcf.config.RecommendationConfig;
import io.github.thacbao.itemcf.core.InteractionMatrix;
import io.github.thacbao.itemcf.core.SimilarityCalculator;
import io.github.thacbao.itemcf.model.RecommendationResult;
import io.github.thacbao.itemcf.model.SimilarityResult;
import io.github.thacbao.itemcf.port.CachePort;
import io.github.thacbao.itemcf.port.InteractionLoader;
import io.github.thacbao.itemcf.port.SimilarityStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Main entry point of the ItemCF library
 */
public class RecommendationEngine {

    private static final Logger log = LoggerFactory.getLogger(RecommendationEngine.class);

    private static final String CACHE_PREFIX_SIMILAR = "itemcf:similar:";
    private static final String CACHE_PREFIX_USER = "itemcf:user:";

    private static final Duration CACHE_TTL_SIMILAR = Duration.ofHours(24);
    private static final Duration CACHE_TTL_USER = Duration.ofHours(6);

    private final InteractionLoader loader;
    private final SimilarityStore store;
    private final RecommendationConfig config;
    private final SimilarityCalculator calculator;
    private final CachePort cache;

    /**
     * Creates a new engine with the given ports and configuration.
     *
     * @param loader the source of user-item interactions
     * @param store  where computed similarity scores are persisted and queried
     * @param config algorithm configuration
     */
    public RecommendationEngine(InteractionLoader loader, SimilarityStore store, RecommendationConfig config) {
        this.loader = Objects.requireNonNull(loader, "loader must not be null");
        this.store = Objects.requireNonNull(store, "store must not be null");
        this.config = Objects.requireNonNull(config, "config must not be null");
        this.cache = config.getCachePort();
        this.calculator = new SimilarityCalculator(
                config.getStrategy(),
                config.getSimilarityThreshold(),
                config.getMinCommonUsers());
    }
    /**
     * Runs a full recalculation of all item-similarity scores
     */
    public void calculateAndStoreSimilarities() {
        log.info("ItemCF: starting full similarity recalculation");
        long startTime = System.currentTimeMillis();

        try {
            // Clear old data
            store.deleteAll();
            log.debug("ItemCF: cleared existing similarity store");

            // Build item-user matrix
            InteractionMatrix matrix = new InteractionMatrix();
            matrix.load(loader, config.getBatchSize());

            if (matrix.isEmpty()) {
                log.warn("ItemCF: no interactions found, aborting similarity calculation");
                return;
            }

            log.info("ItemCF: loaded {} interactions across {} items",
                    matrix.totalInteractions(), matrix.itemCount());

            // Compute pairwise similarities
            List<SimilarityResult> allResults = calculator.compute(matrix.getMatrix());

            // Flush to store in configurable batches (avoid OOM for large datasets)
            int saveBatch = config.getSaveBatchSize();
            for (int i = 0; i < allResults.size(); i += saveBatch) {
                int end = Math.min(i + saveBatch, allResults.size());
                store.saveAll(allResults.subList(i, end));
            }

            log.info("ItemCF: saved {} similarity pairs to store", allResults.size());

            // Evict stale caches
            cache.evictByPattern(CACHE_PREFIX_SIMILAR + "*");
            cache.evictByPattern(CACHE_PREFIX_USER + "*");

            long durationSec = (System.currentTimeMillis() - startTime) / 1000;
            log.info("ItemCF: recalculation complete in {}s ({} items, {} pairs)",
                    durationSec, matrix.itemCount(), allResults.size());

        } catch (Exception e) {
            log.error("ItemCF: similarity recalculation failed", e);
            throw new RuntimeException("Similarity recalculation failed", e);
        }
    }

    /**
     * Returns the top-{@code limit} items most similar to the given item.
     * 
     * @param itemId the reference item ID
     * @param limit  maximum number of results to return
     * @return similar items ordered by similarity score descending
     */
    @SuppressWarnings("unchecked")
    public List<RecommendationResult> getSimilarItems(int itemId, int limit) {
        String cacheKey = CACHE_PREFIX_SIMILAR + itemId;

        // Cache hit
        Optional<Object> cached = cache.get(cacheKey);
        if (cached.isPresent()) {
            log.debug("ItemCF: cache hit for similar items of item {}", itemId);
            List<RecommendationResult> full = (List<RecommendationResult>) cached.get();
            return full.stream().limit(limit).collect(Collectors.toList());
        }

        // Fetch from store — use topKSimilar as the internal cap
        List<SimilarityResult> similarities = store.findSimilar(itemId, config.getTopKSimilar());

        List<RecommendationResult> results = similarities.stream()
                .map(sr -> new RecommendationResult(sr.itemId2(), sr.score()))
                .sorted()
                .collect(Collectors.toList());

        if (!results.isEmpty()) {
            cache.put(cacheKey, results, CACHE_TTL_SIMILAR);
        }

        return results.stream().limit(limit).collect(Collectors.toList());
    }

    /**
     * Returns personalized item recommendations for a user based on their
     *
     * @param userId              the user to recommend items for
     * @param userInteractedItems set of item IDs the user has already interacted
     *                            with
     * @param limit               maximum number of results to return
     * @return personalized recommendations ordered by aggregated score descending
     */
    @SuppressWarnings("unchecked")
    public List<RecommendationResult> getRecommendationsForUser(
            int userId,
            Set<Integer> userInteractedItems,
            int limit) {

        String cacheKey = CACHE_PREFIX_USER + userId;

        // Cache hit
        Optional<Object> cached = cache.get(cacheKey);
        if (cached.isPresent()) {
            log.debug("ItemCF: cache hit for user recommendations of user {}", userId);
            List<RecommendationResult> full = (List<RecommendationResult>) cached.get();
            return full.stream().limit(limit).collect(Collectors.toList());
        }

        if (userInteractedItems.isEmpty()) {
            log.debug("ItemCF: user {} has no interaction history, returning empty list", userId);
            return Collections.emptyList();
        }

        // Aggregate scores from similar items of each interacted item
        Map<Integer, Double> scoreAccumulator = new HashMap<>();

        for (int interactedItemId : userInteractedItems) {
            List<SimilarityResult> similarities = store.findSimilar(interactedItemId, config.getTopKSimilar());

            for (SimilarityResult sr : similarities) {
                int candidateId = sr.itemId2();
                // Never recommend items the user already interacted with
                if (!userInteractedItems.contains(candidateId)) {
                    scoreAccumulator.merge(candidateId, sr.score(), Double::sum);
                }
            }
        }

        if (scoreAccumulator.isEmpty()) {
            log.debug("ItemCF: no similar items found for user {}, returning empty list", userId);
            return Collections.emptyList();
        }

        List<RecommendationResult> recommendations = scoreAccumulator.entrySet().stream()
                .map(e -> new RecommendationResult(e.getKey(), e.getValue()))
                .sorted()
                .limit(limit)
                .collect(Collectors.toList());

        if (!recommendations.isEmpty()) {
            cache.put(cacheKey, recommendations, CACHE_TTL_USER);
        }

        log.debug("ItemCF: generated {} recommendations for user {}", recommendations.size(), userId);
        return recommendations;
    }
}
