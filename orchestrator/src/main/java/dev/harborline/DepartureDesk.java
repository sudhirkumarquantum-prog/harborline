package dev.harborline;

import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Composition of the three mechanisms: order the facts, lock the seat and the
 * passenger, and read the host map once per flight.
 */
public final class DepartureDesk {

    private final String flight;
    private final Set<String> taken = ConcurrentHashMap.newKeySet();
    private final KahnScheduler scheduler = new KahnScheduler();
    private final PassengerGate gate = new PassengerGate();
    private final HostMemo memo;
    private final Supplier<String> hostSeatMap;

    public DepartureDesk(String flight, HostMemo memo, Supplier<String> hostSeatMap) {
        this.flight = flight;
        this.memo = memo;
        this.hostSeatMap = hostSeatMap;
    }

    public static DepartureDesk hb418(Supplier<String> hostSeatMap) {
        return new DepartureDesk("HB418", new HostMemo(TimeSource.wall(), Duration.ofSeconds(30)), hostSeatMap);
    }

    public Receipt checkIn(String passenger, String seat, boolean documentOk, int bags) {
        if (bags < 0) {
            throw new IllegalArgumentException("bags");
        }
        List<Intent> intents = new ArrayList<>();
        intents.add(new Intent(passenger + ":seat", passenger, EnumSet.of(Fact.SEATMAP), EnumSet.of(Fact.SEAT)));
        EnumSet<Fact> stampReads = EnumSet.of(Fact.SEAT, Fact.DOCUMENT);
        if (bags > 0) {
            intents.add(new Intent(passenger + ":bag", passenger, EnumSet.of(Fact.PNR), EnumSet.of(Fact.BAG)));
            stampReads.add(Fact.BAG);
        }
        intents.add(new Intent(passenger + ":stamp", passenger, stampReads, EnumSet.of(Fact.CHECKIN)));

        List<Intent> ordered = scheduler.order(intents);
        gate.run(flight, Set.of(seat), Set.of(passenger), () -> apply(passenger, seat, documentOk, ordered));
        return new Receipt(passenger, flight, seat, ordered.stream().map(Intent::id).toList());
    }

    public long hostCalls() {
        return memo.hostCalls();
    }

    private void apply(String passenger, String seat, boolean documentOk, List<Intent> ordered) {
        memo.read(flight + ":seatmap", hostSeatMap);
        if (!documentOk) {
            throw new Rejected("document");
        }
        if (!taken.add(seat)) {
            throw new Rejected("seat");
        }
        if (ordered.isEmpty()) {
            throw new IllegalStateException("empty batch for " + passenger);
        }
    }
}
