package io.github.thacbao.itemcf.port;

import io.github.thacbao.itemcf.model.Interaction;

import java.util.List;

/**
 * Port for loading user–item interactions in batches
 * The caller (consumer application) must implement this interface to bridge
 * the library with their actual data source (JPA repositories, JDBC...
 */
public interface InteractionLoader {

    /**
     * Loads a batch of interactions starting at {@code offset}.
     *
     * @param offset zero-based row offset
     * @param limit  maximum number of interactions to return
     * @return interactions in this batch; an empty list signals end of data
     */
    List<Interaction> loadBatch(int offset, int limit);
}
