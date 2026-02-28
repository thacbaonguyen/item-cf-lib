package io.github.thacbao.itemcf.core;

import io.github.thacbao.itemcf.model.Interaction;
import io.github.thacbao.itemcf.port.InteractionLoader;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class InteractionMatrixTest {

    @Test
    void load_shouldBuildCorrectItemUserMatrix() {
        // Given: a loader with 3 interactions
        InteractionLoader loader = (offset, limit) -> offset == 0
                ? List.of(
                        new Interaction(1, 10, 4.0), // user1 → item10
                        new Interaction(1, 20, 3.0), // user1 → item20
                        new Interaction(2, 10, 5.0)) // user2 → item10
                : Collections.emptyList();

        InteractionMatrix matrix = new InteractionMatrix();
        matrix.load(loader, 1000);

        Map<Integer, Map<Integer, Double>> m = matrix.getMatrix();

        // item10 should have both user1 and user2
        assertThat(m).containsKey(10);
        assertThat(m.get(10)).containsEntry(1, 4.0).containsEntry(2, 5.0);

        // item20 should have user1 only
        assertThat(m).containsKey(20);
        assertThat(m.get(20)).containsEntry(1, 3.0);

        assertThat(matrix.itemCount()).isEqualTo(2);
        assertThat(matrix.totalInteractions()).isEqualTo(3);
    }

    @Test
    void load_withDuplicateUserItemPair_shouldKeepMaxScore() {
        // user1 interacts with item10 via both a review (3.0) and an order (5.0)
        InteractionLoader loader = (offset, limit) -> offset == 0
                ? List.of(
                        new Interaction(1, 10, 3.0),
                        new Interaction(1, 10, 5.0)) // same user+item, higher score
                : Collections.emptyList();

        InteractionMatrix matrix = new InteractionMatrix();
        matrix.load(loader, 1000);

        // Max merge: should keep 5.0
        assertThat(matrix.getMatrix().get(10).get(1)).isEqualTo(5.0);
    }

    @Test
    void load_withEmptyData_shouldProduceEmptyMatrix() {
        InteractionLoader emptyLoader = (offset, limit) -> Collections.emptyList();
        InteractionMatrix matrix = new InteractionMatrix();
        matrix.load(emptyLoader, 1000);

        assertThat(matrix.isEmpty()).isTrue();
        assertThat(matrix.itemCount()).isEqualTo(0);
    }

    @Test
    void load_shouldSupportMultipleBatches() {
        // Simulates 3 batches of 2 interactions each
        List<List<Interaction>> batches = List.of(
                List.of(new Interaction(1, 10, 4.0), new Interaction(1, 20, 3.0)),
                List.of(new Interaction(2, 10, 5.0), new Interaction(2, 30, 2.0)),
                List.of(new Interaction(3, 20, 4.0), new Interaction(3, 30, 5.0)));

        InteractionLoader batchLoader = (offset, limit) -> {
            int batchIndex = offset / limit;
            return batchIndex < batches.size() ? batches.get(batchIndex) : Collections.emptyList();
        };

        InteractionMatrix matrix = new InteractionMatrix();
        matrix.load(batchLoader, 2);

        assertThat(matrix.itemCount()).isEqualTo(3); // items: 10, 20, 30
        assertThat(matrix.totalInteractions()).isEqualTo(6);
    }
}
