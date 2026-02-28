package io.github.thacbao.itemcf.port.impl;

import io.github.thacbao.itemcf.model.SimilarityResult;
import io.github.thacbao.itemcf.port.SimilarityStore;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Thread-safe, in-memory implementation of {@link SimilarityStore}.
 */
public class InMemorySimilarityStore implements SimilarityStore {

    // itemId → list of SimilarityResult sorted by score desc
    private final Map<Integer, List<SimilarityResult>> store = new ConcurrentHashMap<>();

    @Override
    public void saveAll(List<SimilarityResult> results) {
        for (SimilarityResult r : results) {
            store.computeIfAbsent(r.itemId1(), k -> new ArrayList<>()).add(r);
        }
    }

    @Override
    public List<SimilarityResult> findSimilar(int itemId, int topK) {
        return store.getOrDefault(itemId, Collections.emptyList())
                .stream()
                .sorted(Comparator.comparingDouble(SimilarityResult::score).reversed())
                .limit(topK)
                .collect(Collectors.toList());
    }

    @Override
    public void deleteAll() {
        store.clear();
    }

    /** Returns total number of stored similarity pairs (for testing/debugging). */
    public int size() {
        return store.values().stream().mapToInt(List::size).sum();
    }
}
