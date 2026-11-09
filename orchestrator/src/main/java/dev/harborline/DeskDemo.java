package dev.harborline;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Runs the desk against a cabin and prints the invariants the tests assert.
 */
public final class DeskDemo {

    private DeskDemo() {
    }

    public static void main(String[] args) throws Exception {
        AtomicInteger supplierCalls = new AtomicInteger();
        HostMemo memo = new HostMemo(TimeSource.wall(), Duration.ofSeconds(30));
        DepartureDesk desk = new DepartureDesk("HB418", memo, () -> {
            supplierCalls.incrementAndGet();
            try {
                Thread.sleep(40);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("host read interrupted");
            }
            return "HB418-seatmap";
        });

        List<Booking> cabin = List.of(
                new Booking("Asha Menon", "H4K1", "12C", 1),
                new Booking("Nico Park", "H4K4", "14A", 0),
                new Booking("Meera Iyer", "H4K5", "16A", 0),
                new Booking("Omar Shah", "H4K6", "12A", 1),
                new Booking("Priya Nair", "H4K7", "14C", 0),
                new Booking("Dev Patel", "H4K8", "16C", 2)
        );

        System.out.println("Harborline desk   HB418");
        System.out.println("Six check-ins, six seats, one host seat-map read.");
        System.out.println();

        long started = System.nanoTime();
        List<String> lines = new ArrayList<>();
        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<String>> inflight = new ArrayList<>();
            for (Booking booking : cabin) {
                inflight.add(pool.submit(() -> admit(desk, booking)));
            }
            for (Future<String> future : inflight) {
                lines.add(future.get(5, TimeUnit.SECONDS));
            }
        }
        long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);

        for (String line : lines) {
            System.out.println(line);
        }
        System.out.println();
        System.out.printf("host reads %d, passengers who shared that read %d, wall %d ms%n",
                memo.hostCalls(), memo.waits(), elapsedMillis);
        if (supplierCalls.get() != 1 || memo.hostCalls() != 1) {
            throw new IllegalStateException("expected a single host read, saw " + supplierCalls.get());
        }

        System.out.println();
        System.out.println("Same seat, two passengers");
        int wins = 0;
        int losses = 0;
        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<String>> race = List.of(
                    pool.submit(() -> admit(desk, new Booking("Asha Menon", "H4K1B", "18C", 0))),
                    pool.submit(() -> admit(desk, new Booking("Leela Das", "H4K3", "18C", 0)))
            );
            for (Future<String> future : race) {
                String line = future.get(5, TimeUnit.SECONDS);
                System.out.println(line);
                if (line.contains("checked_in")) {
                    wins++;
                } else {
                    losses++;
                }
            }
        }
        if (wins != 1 || losses != 1) {
            throw new IllegalStateException("expected one winner and one refusal for 18C");
        }
    }

    private static String admit(DepartureDesk desk, Booking booking) {
        try {
            Receipt receipt = desk.checkIn(booking.ref, booking.seat, true, booking.bags);
            return String.format("%-14s %-8s checked_in  seat %s  intents %s",
                    booking.name, booking.ref, receipt.seat(), receipt.appliedIntentIds());
        } catch (Rejected rejected) {
            return String.format("%-14s %-8s refused     seat %s  reason %s",
                    booking.name, booking.ref, booking.seat, rejected.reason());
        }
    }

    private record Booking(String name, String ref, String seat, int bags) {
    }
}
