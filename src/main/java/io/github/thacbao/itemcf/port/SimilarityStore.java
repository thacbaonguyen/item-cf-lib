package io.github.thacbao.itemcf.port;

import io.github.thacbao.itemcf.model.SimilarityResult;

import java.util.List;

/**
 * Port for persisting and retrieving pre-computed item-similarity scores
 */
public interface SimilarityStore {

    /**
     * Persists a batch of similarity results
     *
     * @param results non-null, non-empty list of similarity results to save
     */
    void saveAll(List<SimilarityResult> results);

    /**
     * Retrieves the top-{@code topK} most similar items for a given item
     * 
     * @param itemId the reference item
     * @param topK   maximum number of similar items to return
     * @return a list of similarity results ordered by score descending
     */
    List<SimilarityResult> findSimilar(int itemId, int topK);

    /**
     * Removes all previously computed similarity data
     * Called at the beginning of a full recalculation
     */
    void deleteAll();
}
