package io.github.thacbao.itemcf.config;

import io.github.thacbao.itemcf.port.CachePort;
import io.github.thacbao.itemcf.port.impl.NoOpCachePort;
import io.github.thacbao.itemcf.similarity.CosineSimilarityStrategy;
import io.github.thacbao.itemcf.similarity.SimilarityStrategy;

import java.util.Objects;

public final class RecommendationConfig {

    /** Default similarity threshold — pairs below this are discarded. */
    public static final double DEFAULT_SIMILARITY_THRESHOLD = 0.15;

    /** Default minimum number of common users for a pair to be considered. */
    public static final int DEFAULT_MIN_COMMON_USERS = 2;

    /** Default top-K similar items to store per item. */
    public static final int DEFAULT_TOP_K_SIMILAR = 50;

    /** Default batch size for loading interactions. */
    public static final int DEFAULT_BATCH_SIZE = 1000;

    /** Default batch flush size for saving similarity results. */
    public static final int DEFAULT_SAVE_BATCH_SIZE = 1000;

    private final double similarityThreshold;
    private final int minCommonUsers;
    private final int topKSimilar;
    private final int batchSize;
    private final int saveBatchSize;
    private final SimilarityStrategy strategy;
    private final CachePort cachePort;

    private RecommendationConfig(Builder builder) {
        this.similarityThreshold = builder.similarityThreshold;
        this.minCommonUsers = builder.minCommonUsers;
        this.topKSimilar = builder.topKSimilar;
        this.batchSize = builder.batchSize;
        this.saveBatchSize = builder.saveBatchSize;
        this.strategy = builder.strategy;
        this.cachePort = builder.cachePort;
    }

    /** Minimum cosine score for a pair to be persisted. */
    public double getSimilarityThreshold() {
        return similarityThreshold;
    }

    /** Minimum number of users who interacted with both items. */
    public int getMinCommonUsers() {
        return minCommonUsers;
    }

    /**
     * Maximum number of similar items stored per item in
     */
    public int getTopKSimilar() {
        return topKSimilar;
    }

    /**
     * Number of interactions loaded per
     */
    public int getBatchSize() {
        return batchSize;
    }

    /**
     * Number of similarity pairs flushed to
     */
    public int getSaveBatchSize() {
        return saveBatchSize;
    }

    /** The similarity algorithm to use. */
    public SimilarityStrategy getStrategy() {
        return strategy;
    }

    /** Cache port (defaults to no-op). */
    public CachePort getCachePort() {
        return cachePort;
    }

    /** Returns a builder pre-configured with all default values. */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Returns a config with all default values. Shortcut for
     */
    public static RecommendationConfig defaults() {
        return builder().build();
    }

    public static final class Builder {

        private double similarityThreshold = DEFAULT_SIMILARITY_THRESHOLD;
        private int minCommonUsers = DEFAULT_MIN_COMMON_USERS;
        private int topKSimilar = DEFAULT_TOP_K_SIMILAR;
        private int batchSize = DEFAULT_BATCH_SIZE;
        private int saveBatchSize = DEFAULT_SAVE_BATCH_SIZE;
        private SimilarityStrategy strategy = CosineSimilarityStrategy.INSTANCE;
        private CachePort cachePort = NoOpCachePort.INSTANCE;

        private Builder() {
        }

        /**
         * Minimum cosine similarity score to store a pair. Default:
         * {@value DEFAULT_SIMILARITY_THRESHOLD}.
         */
        public Builder similarityThreshold(double threshold) {
            if (threshold < 0 || threshold > 1)
                throw new IllegalArgumentException("threshold must be in [0,1]");
            this.similarityThreshold = threshold;
            return this;
        }

        /**
         * Minimum number of users who interacted with both items for a pair to be
         * considered.
         * Default: {@value DEFAULT_MIN_COMMON_USERS}.
         */
        public Builder minCommonUsers(int minCommonUsers) {
            if (minCommonUsers < 1)
                throw new IllegalArgumentException("minCommonUsers must be >= 1");
            this.minCommonUsers = minCommonUsers;
            return this;
        }

        /**
         * Top-K similar items to query when building recommendations for a user.
         * Default: {@value DEFAULT_TOP_K_SIMILAR}.
         */
        public Builder topKSimilar(int topKSimilar) {
            if (topKSimilar < 1)
                throw new IllegalArgumentException("topKSimilar must be >= 1");
            this.topKSimilar = topKSimilar;
            return this;
        }

        /**
         * Number of interactions loaded per batch. Default:
         * {@value DEFAULT_BATCH_SIZE}.
         */
        public Builder batchSize(int batchSize) {
            if (batchSize < 1)
                throw new IllegalArgumentException("batchSize must be >= 1");
            this.batchSize = batchSize;
            return this;
        }

        /**
         * Number of similarity pairs flushed per save batch. Default:
         * {@value DEFAULT_SAVE_BATCH_SIZE}.
         */
        public Builder saveBatchSize(int saveBatchSize) {
            if (saveBatchSize < 1)
                throw new IllegalArgumentException("saveBatchSize must be >= 1");
            this.saveBatchSize = saveBatchSize;
            return this;
        }

        /**
         * Similarity strategy to use. Default: {@link CosineSimilarityStrategy}.
         */
        public Builder strategy(SimilarityStrategy strategy) {
            this.strategy = Objects.requireNonNull(strategy, "strategy must not be null");
            return this;
        }

        /**
         * Cache port to use. Default: {@link NoOpCachePort} (no caching).
         */
        public Builder cachePort(CachePort cachePort) {
            this.cachePort = Objects.requireNonNull(cachePort, "cachePort must not be null");
            return this;
        }

        public RecommendationConfig build() {
            return new RecommendationConfig(this);
        }
    }
}
