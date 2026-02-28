package io.github.thacbao.itemcf.port.impl;

import io.github.thacbao.itemcf.port.CachePort;

import java.time.Duration;
import java.util.Optional;

public class NoOpCachePort implements CachePort {

    /** Singleton instance — stateless, safe to share across threads. */
    public static final NoOpCachePort INSTANCE = new NoOpCachePort();

    private NoOpCachePort() {
    }

    @Override
    public Optional<Object> get(String key) {
        return Optional.empty();
    }

    @Override
    public void put(String key, Object value, Duration ttl) {
        // intentional no-op
    }

    @Override
    public void evictByPattern(String pattern) {
        // intentional no-op
    }
}
