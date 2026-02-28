package io.github.thacbao.itemcf.port;

import java.time.Duration;
import java.util.Optional;

/**
 * Optional port for plugging in a caching layer
 */
public interface CachePort {

    /**
     * Retrieves a cached object for the given key.
     *
     * @param key cache key
     * @return the cached value, or {@link Optional#empty()} on cache miss
     */
    Optional<Object> get(String key);

    /**
     * Stores an object in the cache with the given TTL.
     *
     * @param key   cache key
     * @param value value to cache
     * @param ttl   time-to-live; must not be null or negative
     */
    void put(String key, Object value, Duration ttl);

    /**
     * Evicts all cache entries whose keys match the given glob-style pattern
     * 
     * @param pattern glob pattern
     */
    void evictByPattern(String pattern);
}
