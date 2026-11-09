package dev.harborline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class PassengerGateTest {

    @Test
    void theSamePassengerCannotOverlap() throws Exception {
        assertEquals(1, peakOverlap("H4K1", "H4K1", "12C", "14A"));
    }

    @Test
    void theSameSeatCannotOverlap() throws Exception {
        assertEquals(1, peakOverlap("H4K1", "H4K4", "12C", "12C"));
    }

    @Test
    void disjointSeatsAndPassengersOverlap() throws Exception {
        assertEquals(2, peakOverlap("H4K1", "H4K4", "12C", "14A"));
    }

    @Test
    void crossedSeatAndPassengerPairsFinish() throws Exception {
        PassengerGate gate = new PassengerGate();
        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < 40; i++) {
                String passenger = "P" + (i % 7);
                String seat = (i % 5) + "C";
                futures.add(pool.submit(() -> gate.run("HB418", Set.of(seat), Set.of(passenger), () -> pause(2))));
            }
            for (Future<?> future : futures) {
                future.get(5, TimeUnit.SECONDS);
            }
        }
    }

    private static int peakOverlap(String firstPassenger, String secondPassenger, String firstSeat, String secondSeat)
            throws Exception {
        PassengerGate gate = new PassengerGate();
        AtomicInteger peak = new AtomicInteger();
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch started = new CountDownLatch(2);
        boolean shareALock = firstPassenger.equals(secondPassenger) || firstSeat.equals(secondSeat);

        Runnable body = () -> {
            peak.accumulateAndGet(1, Integer::sum);
            started.countDown();
            await(release);
            peak.accumulateAndGet(-1, Integer::sum);
        };

        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<?> first = pool.submit(() -> gate.run("HB418", Set.of(firstSeat), Set.of(firstPassenger), body));
            Future<?> second = pool.submit(() -> gate.run("HB418", Set.of(secondSeat), Set.of(secondPassenger), body));
            if (shareALock) {
                pause(80);
                assertEquals(1, peak.get(), "a shared seat or passenger overlapped");
            } else {
                assertTrue(started.await(1, TimeUnit.SECONDS), "disjoint work should both enter");
                assertEquals(2, peak.get());
            }
            release.countDown();
            first.get(2, TimeUnit.SECONDS);
            second.get(2, TimeUnit.SECONDS);
        }
        return shareALock ? 1 : 2;
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(2, TimeUnit.SECONDS)) {
                throw new IllegalStateException("lock wait timed out");
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(interrupted);
        }
    }

    private static void pause(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(interrupted);
        }
    }
}
