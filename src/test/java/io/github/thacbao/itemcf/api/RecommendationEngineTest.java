package io.github.thacbao.itemcf.api;

import io.github.thacbao.itemcf.config.RecommendationConfig;
import io.github.thacbao.itemcf.model.Interaction;
import io.github.thacbao.itemcf.model.RecommendationResult;
import io.github.thacbao.itemcf.port.InteractionLoader;
import io.github.thacbao.itemcf.port.impl.InMemorySimilarityStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for the full ItemCF pipeline using in-memory adapters.
 * No Spring, no database, no Redis — tests the library in isolation.
 */
class RecommendationEngineTest {

    /*
     * Test scenario:
     * item10 = "Jacket" (users 1,2,3 bought/reviewed)
     * item20 = "Sweater" (users 1,2 bought/reviewed) → similar to Jacket
     * item30 = "Shorts" (users 4,5 only) → NOT similar to Jacket/Sweater
     * item40 = "Sandals" (users 1,3 bought) → somewhat similar to Jacket
     *
     * Expected: item10 ↔ item20 should have high similarity;
     * item30 should NOT be similar to item10/item20.
     */
    private static final List<Interaction> TEST_INTERACTIONS = List.of(
            new Interaction(1, 10, 5.0),
            new Interaction(2, 10, 4.0),
            new Interaction(3, 10, 3.0),

            new Interaction(1, 20, 5.0),
            new Interaction(2, 20, 4.0),

            new Interaction(4, 30, 5.0),
            new Interaction(5, 30, 4.0),

            new Interaction(1, 40, 4.0),
            new Interaction(3, 40, 3.0));

    private RecommendationEngine engine;
    private InMemorySimilarityStore store;

    @BeforeEach
    void setUp() {
        store = new InMemorySimilarityStore();

        InteractionLoader loader = (offset, limit) -> offset == 0 ? TEST_INTERACTIONS : Collections.emptyList();

        RecommendationConfig config = RecommendationConfig.builder()
                .similarityThreshold(0.10)
                .minCommonUsers(2)
                .topKSimilar(20)
                .batchSize(100)
                .build();

        engine = new RecommendationEngine(loader, store, config);
        engine.calculateAndStoreSimilarities();
    }

    @Test
    void calculateAndStoreSimilarities_shouldPopulateStore() {
        assertThat(store.size()).isGreaterThan(0);
    }

    @Test
    void getSimilarItems_forItem10_shouldReturnItem20() {
        List<RecommendationResult> similar = engine.getSimilarItems(10, 5);

        assertThat(similar).isNotEmpty();
        List<Integer> itemIds = similar.stream().map(RecommendationResult::itemId).toList();
        assertThat(itemIds).contains(20); // Sweater should be similar to Jacket
    }

    @Test
    void getSimilarItems_forItem10_shouldNotReturnItem30() {
        // item30 shares no users with item10 → should not appear
        List<RecommendationResult> similar = engine.getSimilarItems(10, 10);
        List<Integer> itemIds = similar.stream().map(RecommendationResult::itemId).toList();
        assertThat(itemIds).doesNotContain(30);
    }

    @Test
    void getSimilarItems_shouldReturnResultsOrderedByScoreDesc() {
        List<RecommendationResult> similar = engine.getSimilarItems(10, 10);
        for (int i = 0; i < similar.size() - 1; i++) {
            assertThat(similar.get(i).score()).isGreaterThanOrEqualTo(similar.get(i + 1).score());
        }
    }

    @Test
    void getRecommendationsForUser_givenInteractionHistory_shouldFilterOutAlreadySeen() {
        // user 6 (new) has only seen item10 → recommend from similar items, not item10
        // itself
        Set<Integer> userHistory = Set.of(10);
        List<RecommendationResult> recs = engine.getRecommendationsForUser(6, userHistory, 5);

        List<Integer> recommendedIds = recs.stream().map(RecommendationResult::itemId).toList();
        assertThat(recommendedIds).doesNotContain(10); // already interacted
    }

    @Test
    void getRecommendationsForUser_withEmptyHistory_shouldReturnEmpty() {
        List<RecommendationResult> recs = engine.getRecommendationsForUser(99, Set.of(), 10);
        assertThat(recs).isEmpty();
    }

    @Test
    void calculateAndStoreSimilarities_whenCalledTwice_shouldReplaceOldData() {
        int sizeAfterFirstRun = store.size();
        engine.calculateAndStoreSimilarities(); // second run
        int sizeAfterSecondRun = store.size();

        // Second run should produce the same number of results (store was cleared
        // first)
        assertThat(sizeAfterSecondRun).isEqualTo(sizeAfterFirstRun);
    }
}
