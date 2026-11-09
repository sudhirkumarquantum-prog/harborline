package dev.harborline;

import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashSet;
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
        List<Intent> ordered = scheduler.order(batchFor(passenger, bags));
        gate.run(flight, Set.of(seat), Set.of(passenger), () -> apply(passenger, seat, documentOk, ordered));
        return new Receipt(passenger, flight, seat, ordered.stream().map(Intent::id).toList());
    }

    /**
     * Claims every seat in an admitted party under one lock acquisition.
     * A refusal releases any seat this call claimed.
     */
    public List<Receipt> admitAll(List<PartyMember> party) {
        if (party == null || party.size() < 2) {
            throw new IllegalArgumentException("a party needs two passengers");
        }
        Set<String> seats = new LinkedHashSet<>();
        Set<String> passengers = new LinkedHashSet<>();
        List<Intent> intents = new ArrayList<>();
        for (PartyMember member : party) {
            if (!seats.add(member.seat())) {
                throw new IllegalArgumentException("duplicate seat " + member.seat());
            }
            if (!passengers.add(member.passenger())) {
                throw new IllegalArgumentException("duplicate passenger " + member.passenger());
            }
            intents.addAll(batchFor(member.passenger(), member.bags()));
        }
        List<Intent> ordered = scheduler.order(intents);
        List<Receipt> receipts = new ArrayList<>();
        gate.run(flight, seats, passengers, () -> claim(party, ordered, receipts));
        return List.copyOf(receipts);
    }

    public long hostCalls() {
        return memo.hostCalls();
    }

    private static List<Intent> batchFor(String passenger, int bags) {
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
        return intents;
    }

    private void claim(List<PartyMember> party, List<Intent> ordered, List<Receipt> receipts) {
        memo.read(flight + ":seatmap", hostSeatMap);
        for (PartyMember member : party) {
            if (!member.documentOk()) {
                throw new Rejected("document");
            }
        }
        List<String> claimed = new ArrayList<>();
        try {
            for (PartyMember member : party) {
                if (!taken.add(member.seat())) {
                    throw new Rejected("seat");
                }
                claimed.add(member.seat());
            }
        } catch (RuntimeException failure) {
            taken.removeAll(claimed);
            throw failure;
        }
        for (PartyMember member : party) {
            List<String> ids = ordered.stream()
                    .filter(intent -> intent.passengerRef().equals(member.passenger()))
                    .map(Intent::id)
                    .toList();
            receipts.add(new Receipt(member.passenger(), flight, member.seat(), ids));
        }
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
