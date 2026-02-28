package io.github.thacbao.itemcf.model;

/**
 * Holds the computed similarity score between two items
 * 
 * @param itemId1 identifier of the first item
 * @param itemId2 identifier of the second item (always different from
 *                {@code itemId1})
 * @param score   cosine similarity score in the range (0, 1]
 */
public record SimilarityResult(int itemId1, int itemId2, double score) {

    public SimilarityResult {
        if (itemId1 == itemId2) {
            throw new IllegalArgumentException("itemId1 and itemId2 must be different");
        }
        if (score <= 0 || score > 1.0) {
            throw new IllegalArgumentException("Score must be in range (0, 1], got: " + score);
        }
    }
}
