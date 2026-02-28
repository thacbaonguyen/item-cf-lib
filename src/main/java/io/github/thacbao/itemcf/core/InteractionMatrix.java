package io.github.thacbao.itemcf.core;

import io.github.thacbao.itemcf.model.Interaction;
import io.github.thacbao.itemcf.port.InteractionLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class InteractionMatrix {

    private static final Logger log = LoggerFactory.getLogger(InteractionMatrix.class);

    /** item-user matrix: itemId → (userId → score) */
    private final Map<Integer, Map<Integer, Double>> matrix = new HashMap<>();

    private int totalInteractions = 0;

    /**
     * Loads all interactions from the given loader in batches and builds the
     * matrix.
     *
     * @param loader    source of interaction data
     * @param batchSize number of interactions to load per batch
     */
    public void load(InteractionLoader loader, int batchSize) {
        int offset = 0;
        int batchCount = 0;

        while (true) {
            List<Interaction> batch = loader.loadBatch(offset, batchSize);

            if (batch == null || batch.isEmpty()) {
                break;
            }

            for (Interaction interaction : batch) {
                matrix.computeIfAbsent(interaction.itemId(), k -> new HashMap<>())
                        .merge(interaction.userId(), interaction.score(), Double::max);
            }

            totalInteractions += batch.size();
            offset += batchSize;
            batchCount++;

            if (batch.size() < batchSize) {
                break;
            }
        }

        log.debug("Loaded {} interactions across {} batches into item-user matrix ({} unique items)",
                totalInteractions, batchCount, matrix.size());
    }

    /**
     * Returns the item-user matrix.
     *
     * @return map of itemId → (userId → score); never null, may be empty
     */
    public Map<Integer, Map<Integer, Double>> getMatrix() {
        return matrix;
    }

    public boolean isEmpty() {
        return matrix.isEmpty();
    }

    public int itemCount() {
        return matrix.size();
    }

    public int totalInteractions() {
        return totalInteractions;
    }
}
