package io.github.thacbao.itemcf.model;

/**
 * A recommended item along with its aggregated relevance score
 * 
 * @param itemId the recommended item's identifier
 * @param score  aggregated similarity score used for ranking; higher is better
 */
public record RecommendationResult(int itemId, double score) implements Comparable<RecommendationResult> {

    @Override
    public int compareTo(RecommendationResult other) {
        // Natural order: highest score first
        return Double.compare(other.score, this.score);
    }
}
