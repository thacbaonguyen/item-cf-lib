package io.github.thacbao.itemcf.core;

import io.github.thacbao.itemcf.model.SimilarityResult;
import io.github.thacbao.itemcf.similarity.SimilarityStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class SimilarityCalculator {

    private static final Logger log = LoggerFactory.getLogger(SimilarityCalculator.class);

    private final SimilarityStrategy strategy;
    private final double threshold;
    private final int minCommonUsers;

    /**
     * @param strategy       algorithm used to compute similarity
     * @param threshold      minimum score for a pair to be retained
     * @param minCommonUsers minimum number of shared users
     */
    public SimilarityCalculator(SimilarityStrategy strategy, double threshold, int minCommonUsers) {
        this.strategy = Objects.requireNonNull(strategy);
        this.threshold = threshold;
        this.minCommonUsers = minCommonUsers;
    }

    /**
     * Computes all qualifying pairwise similarities from the given item-user
     * matrix.
     *
     * @param itemMatrix item-user matrix: itemId → (userId → score)
     * @return list of qualified {@link SimilarityResult} pairs (bidirectional)
     */
    public List<SimilarityResult> compute(Map<Integer, Map<Integer, Double>> itemMatrix) {
        List<Integer> itemIds = new ArrayList<>(itemMatrix.keySet());
        int n = itemIds.size();
        List<SimilarityResult> results = new ArrayList<>();

        log.debug("Computing pairwise similarities for {} items ({} pairs)", n, (long) n * (n - 1) / 2);

        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                int id1 = itemIds.get(i);
                int id2 = itemIds.get(j);

                Map<Integer, Double> v1 = itemMatrix.get(id1);
                Map<Integer, Double> v2 = itemMatrix.get(id2);

                // Check common users BEFORE running the full similarity computation
                int commonUsers = countCommonUsers(v1, v2);
                if (commonUsers < minCommonUsers) {
                    continue;
                }

                double sim = strategy.compute(v1, v2);

                if (sim >= threshold) {
                    // Store bidirectional: A→B and B→A (same score)
                    results.add(new SimilarityResult(id1, id2, sim));
                    results.add(new SimilarityResult(id2, id1, sim));
                }
            }
        }

        log.debug("Found {} qualifying similarity pairs (bidirectional) from {} items", results.size(), n);
        return results;
    }

    private int countCommonUsers(Map<Integer, Double> v1, Map<Integer, Double> v2) {
        Map<Integer, Double> smaller = v1.size() <= v2.size() ? v1 : v2;
        Map<Integer, Double> larger = v1.size() <= v2.size() ? v2 : v1;

        int count = 0;
        for (Integer userId : smaller.keySet()) {
            if (larger.containsKey(userId)) {
                count++;
                if (count >= minCommonUsers) {
                    return count; // early exit once threshold is met
                }
            }
        }
        return count;
    }
}
