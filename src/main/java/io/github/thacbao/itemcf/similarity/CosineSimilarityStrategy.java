package io.github.thacbao.itemcf.similarity;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class CosineSimilarityStrategy implements SimilarityStrategy {

    /**
     * Singleton instance — stateless, safe to share across threads.
     */
    public static final CosineSimilarityStrategy INSTANCE = new CosineSimilarityStrategy();

    @Override
    public double compute(Map<Integer, Double> v1, Map<Integer, Double> v2) {
        // Find users who rated both items
        Set<Integer> commonUsers = new HashSet<>(v1.keySet());
        commonUsers.retainAll(v2.keySet());

        if (commonUsers.isEmpty()) {
            return 0.0;
        }

        // Dot product — only over common users
        double dotProduct = 0.0;
        for (Integer userId : commonUsers) {
            dotProduct += v1.get(userId) * v2.get(userId);
        }

        // L2 norm of v1 — over ALL users who rated item 1
        double norm1 = l2Norm(v1);

        // L2 norm of v2 — over ALL users who rated item 2
        double norm2 = l2Norm(v2);

        if (norm1 == 0.0 || norm2 == 0.0) {
            return 0.0;
        }

        return dotProduct / (norm1 * norm2);
    }

    private double l2Norm(Map<Integer, Double> vector) {
        double sumOfSquares = 0.0;
        for (double score : vector.values()) {
            sumOfSquares += score * score;
        }
        return Math.sqrt(sumOfSquares);
    }
}
