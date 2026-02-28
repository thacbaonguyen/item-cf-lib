package io.github.thacbao.itemcf.model;

/**
 * Represents a single user–item interaction with a numeric score
 * 
 * @param userId numeric identifier for the user
 * @param itemId numeric identifier for the item (product)
 * @param score  interaction strength; must be &gt; 0
 */
public record Interaction(int userId, int itemId, double score) {

    public Interaction {
        if (score <= 0) {
            throw new IllegalArgumentException("Interaction score must be > 0, got: " + score);
        }
    }
}
