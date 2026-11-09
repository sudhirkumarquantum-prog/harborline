package dev.harborline;

import java.util.Set;

/** One passenger write, with the facts it needs and the facts it produces. */
public record Intent(String id, String passengerRef, Set<Fact> reads, Set<Fact> writes) {

    public Intent {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("intent id is required");
        }
        if (passengerRef == null || passengerRef.isBlank()) {
            throw new IllegalArgumentException("passenger is required");
        }
        reads = Set.copyOf(reads);
        writes = Set.copyOf(writes);
    }
}
