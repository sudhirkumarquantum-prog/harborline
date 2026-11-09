package dev.harborline;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Acquires seat locks, then passenger locks, each group sorted.
 * Disjoint seats and passengers do not share a lock.
 */
public final class PassengerGate {

    private final ConcurrentHashMap<String, ReentrantLock> locks = new ConcurrentHashMap<>();

    public void run(String flight, Set<String> seats, Set<String> passengers, Runnable body) {
        if (flight == null || flight.isBlank()) {
            throw new IllegalArgumentException("flight is required");
        }
        List<String> keys = new ArrayList<>();
        sorted(seats).forEach(seat -> keys.add("seat:" + flight + ":" + seat));
        sorted(passengers).forEach(passenger -> keys.add("pax:" + passenger));

        List<ReentrantLock> held = new ArrayList<>(keys.size());
        try {
            for (String key : keys) {
                ReentrantLock lock = locks.computeIfAbsent(key, ignored -> new ReentrantLock());
                lock.lock();
                held.add(lock);
            }
            body.run();
        } finally {
            for (int i = held.size() - 1; i >= 0; i--) {
                held.get(i).unlock();
            }
        }
    }

    private static List<String> sorted(Collection<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return values.stream().sorted().toList();
    }
}
