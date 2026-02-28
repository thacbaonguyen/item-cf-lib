package io.github.thacbao.itemcf.similarity;

import java.util.Map;

/**
 * Strategy interface for computing the similarity score between two items.
 */
@FunctionalInterface
public interface SimilarityStrategy {

    /**
     * Computes similarity between two item vectors.
     *
     * @param v1 sparse vector for item 1: {@code userId → score}
     * @param v2 sparse vector for item 2: {@code userId → score}
     * @return similarity score; typically in the range [0, 1].
     *         A value of 0 means no similarity; 1 means identical.
     */
    double compute(Map<Integer, Double> v1, Map<Integer, Double> v2);
}
