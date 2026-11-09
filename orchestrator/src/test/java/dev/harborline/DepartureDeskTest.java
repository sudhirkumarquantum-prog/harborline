package dev.harborline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class DepartureDeskTest {

    @Test
    void aBagMakesTheStampWait() {
        DepartureDesk desk = DepartureDesk.hb418(() -> "map");

        Receipt receipt = desk.checkIn("H4K1", "12C", true, 1);

        assertEquals(List.of("H4K1:seat", "H4K1:bag", "H4K1:stamp"), receipt.appliedIntentIds());
        assertEquals(1, desk.hostCalls());
    }

    @Test
    void noBagOmitsTheBagIntent() {
        DepartureDesk desk = DepartureDesk.hb418(() -> "map");

        Receipt receipt = desk.checkIn("H4K4", "14A", true, 0);

        assertEquals(List.of("H4K4:seat", "H4K4:stamp"), receipt.appliedIntentIds());
    }

    @Test
    void oneSeatMapReadServesTheCabin() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        DepartureDesk desk = DepartureDesk.hb418(() -> "map-" + calls.incrementAndGet());

        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<Receipt>> futures = new java.util.ArrayList<>();
            for (int i = 0; i < 20; i++) {
                String passenger = "P" + i;
                String seat = (10 + i) + "A";
                futures.add(pool.submit(() -> desk.checkIn(passenger, seat, true, 0)));
            }
            for (Future<Receipt> future : futures) {
                future.get(3, TimeUnit.SECONDS);
            }
        }
        assertEquals(1, calls.get());
        assertEquals(1, desk.hostCalls());
    }

    @Test
    void oneSeatHasOneWinner() throws Exception {
        DepartureDesk desk = DepartureDesk.hb418(() -> "map");
        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<Receipt> first = pool.submit(() -> desk.checkIn("H4K1", "12C", true, 0));
            Future<Receipt> second = pool.submit(() -> desk.checkIn("H4K4", "12C", true, 0));
            int wins = 0;
            int losses = 0;
            for (Future<Receipt> future : List.of(first, second)) {
                try {
                    future.get(2, TimeUnit.SECONDS);
                    wins++;
                } catch (Exception failure) {
                    assertEquals(Rejected.class, failure.getCause().getClass());
                    losses++;
                }
            }
            assertEquals(1, wins);
            assertEquals(1, losses);
        }
    }

    @Test
    void aPartyClaimsBothSeatsOrNeither() {
        DepartureDesk desk = DepartureDesk.hb418(() -> "map");

        List<Receipt> receipts = desk.admitAll(List.of(
                new PartyMember("H4K1", "12C", true, 1),
                new PartyMember("H4K4", "12A", true, 2)
        ));

        assertEquals(List.of("H4K1:seat", "H4K1:bag", "H4K1:stamp"), receipts.get(0).appliedIntentIds());
        assertEquals(List.of("H4K4:seat", "H4K4:bag", "H4K4:stamp"), receipts.get(1).appliedIntentIds());
        assertEquals(1, desk.hostCalls());
    }

    @Test
    void aTakenSeatReleasesTheRestOfTheParty() {
        DepartureDesk desk = DepartureDesk.hb418(() -> "map");
        desk.checkIn("H4K5", "12C", true, 0);

        Rejected rejected = assertThrows(Rejected.class, () -> desk.admitAll(List.of(
                new PartyMember("H4K1", "12C", true, 0),
                new PartyMember("H4K4", "14A", true, 0)
        )));

        assertEquals("seat", rejected.reason());
        Receipt later = desk.checkIn("H4K4", "14A", true, 0);
        assertEquals("14A", later.seat());
    }

    @Test
    void aMissingDocumentDoesNotKeepTheSeat() {
        DepartureDesk desk = DepartureDesk.hb418(() -> "map");

        Rejected rejected = assertThrows(Rejected.class, () -> desk.checkIn("H4K3", "16A", false, 0));

        assertEquals("document", rejected.reason());
        Receipt later = desk.checkIn("H4K1", "16A", true, 0);
        assertEquals("16A", later.seat());
    }
}
