package dev.harborline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class HostMemoTest {

    @Test
    void concurrentMissesShareOneHostCall() throws Exception {
        HostMemo memo = new HostMemo(() -> 0L, Duration.ofSeconds(5));
        AtomicInteger calls = new AtomicInteger();
        CountDownLatch insideHost = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        var host = (java.util.function.Supplier<String>) () -> {
            calls.incrementAndGet();
            insideHost.countDown();
            await(release);
            return "HB418-map";
        };

        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<String> first = pool.submit(() -> memo.read("HB418:seatmap", host));
            assertEquals(true, insideHost.await(1, TimeUnit.SECONDS));
            Future<String> second = pool.submit(() -> memo.read("HB418:seatmap", host));
            pause(40);
            assertEquals(1, calls.get());
            release.countDown();
            assertEquals("HB418-map", first.get(1, TimeUnit.SECONDS));
            assertEquals("HB418-map", second.get(1, TimeUnit.SECONDS));
        }
        assertEquals(1, memo.hostCalls());
        assertEquals(1, memo.waits());
    }

    @Test
    void expiryAllowsAFreshHostCall() {
        AtomicLong now = new AtomicLong();
        HostMemo memo = new HostMemo(now::get, Duration.ofMillis(10));
        AtomicInteger calls = new AtomicInteger();

        assertEquals("v1", memo.read("k", () -> "v" + calls.incrementAndGet()));
        assertEquals("v1", memo.read("k", () -> "v" + calls.incrementAndGet()));
        now.set(10);
        assertEquals("v2", memo.read("k", () -> "v" + calls.incrementAndGet()));
        assertEquals(2, memo.hostCalls());
    }

    @Test
    void failuresAreNotPinned() {
        HostMemo memo = new HostMemo(() -> 0L, Duration.ofMinutes(1));
        assertThrows(IllegalStateException.class, () -> memo.read("k", () -> {
            throw new IllegalStateException("host down");
        }));
        assertEquals("back", memo.read("k", () -> "back"));
        assertEquals(2, memo.hostCalls());
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(2, TimeUnit.SECONDS)) {
                throw new IllegalStateException("host wait timed out");
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
