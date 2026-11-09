package dev.harborline;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Supplier;

/**
 * Caches a host value for a TTL and shares one in-flight call per key.
 * Failures are not stored.
 */
public final class HostMemo {

    private final TimeSource time;
    private final long ttlMillis;
    private final ConcurrentHashMap<String, Entry> cache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CompletableFuture<String>> inflight = new ConcurrentHashMap<>();
    private final LongAdder hostCalls = new LongAdder();
    private final LongAdder waits = new LongAdder();

    public HostMemo(TimeSource time, Duration ttl) {
        if (ttl == null || ttl.isNegative() || ttl.isZero()) {
            throw new IllegalArgumentException("ttl must be positive");
        }
        this.time = time;
        this.ttlMillis = ttl.toMillis();
    }

    public String read(String key, Supplier<String> host) {
        long now = time.millis();
        Entry cached = cache.get(key);
        if (cached != null && cached.expiresAtMillis() > now) {
            waits.increment();
            return cached.value();
        }

        CompletableFuture<String> mine = new CompletableFuture<>();
        CompletableFuture<String> existing = inflight.putIfAbsent(key, mine);
        if (existing != null) {
            waits.increment();
            return join(existing);
        }

        try {
            hostCalls.increment();
            String value = host.get();
            cache.put(key, new Entry(value, time.millis() + ttlMillis));
            mine.complete(value);
            return value;
        } catch (Throwable failure) {
            mine.completeExceptionally(failure);
            if (failure instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw new IllegalStateException(failure);
        } finally {
            inflight.remove(key, mine);
        }
    }

    public long hostCalls() {
        return hostCalls.sum();
    }

    public long waits() {
        return waits.sum();
    }

    private static String join(CompletableFuture<String> future) {
        try {
            return future.join();
        } catch (CompletionException wrapped) {
            Throwable cause = wrapped.getCause();
            if (cause instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw wrapped;
        }
    }

    private record Entry(String value, long expiresAtMillis) {
    }
}
