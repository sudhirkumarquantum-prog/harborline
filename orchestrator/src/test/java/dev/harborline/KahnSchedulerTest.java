package dev.harborline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumSet;
import java.util.List;
import org.junit.jupiter.api.Test;

class KahnSchedulerTest {

    private final KahnScheduler scheduler = new KahnScheduler();

    @Test
    void stampWaitsForTheSeatAndTheBag() {
        Intent bag = intent("bag", "H4K1", EnumSet.of(Fact.PNR), EnumSet.of(Fact.BAG));
        Intent seat = intent("seat", "H4K1", EnumSet.of(Fact.SEATMAP), EnumSet.of(Fact.SEAT));
        Intent stamp = intent("stamp", "H4K1", EnumSet.of(Fact.SEAT, Fact.BAG), EnumSet.of(Fact.CHECKIN));

        List<String> ids = scheduler.order(List.of(stamp, bag, seat)).stream().map(Intent::id).toList();

        assertEquals(List.of("bag", "seat", "stamp"), ids);
    }

    @Test
    void arrivalOrderBreaksTiesAndNothingElse() {
        Intent bag = intent("bag", "H4K1", EnumSet.of(Fact.PNR), EnumSet.of(Fact.BAG));
        Intent seat = intent("seat", "H4K1", EnumSet.of(Fact.SEATMAP), EnumSet.of(Fact.SEAT));

        assertEquals(List.of("seat", "bag"), ids(scheduler.order(List.of(seat, bag))));
        assertEquals(List.of("bag", "seat"), ids(scheduler.order(List.of(bag, seat))));
    }

    @Test
    void differentPassengersDoNotWaitOnEachOther() {
        Intent a = intent("a-seat", "A", EnumSet.of(Fact.SEATMAP), EnumSet.of(Fact.SEAT));
        Intent b = intent("b-stamp", "B", EnumSet.of(Fact.SEAT), EnumSet.of(Fact.CHECKIN));

        assertEquals(List.of("a-seat", "b-stamp"), ids(scheduler.order(List.of(a, b))));
    }

    @Test
    void aFactCycleRunsNothing() {
        Intent a = intent("a", "H4K1", EnumSet.of(Fact.BAG), EnumSet.of(Fact.SEAT));
        Intent b = intent("b", "H4K1", EnumSet.of(Fact.SEAT), EnumSet.of(Fact.BAG));

        CycleException cycle = assertThrows(CycleException.class, () -> scheduler.order(List.of(a, b)));

        assertTrue(cycle.stuck().containsAll(List.of("a", "b")));
        assertEquals(2, cycle.stuck().size());
    }

    @Test
    void anEmptyBatchIsAlreadyOrdered() {
        assertEquals(List.of(), scheduler.order(List.of()));
    }

    private static List<String> ids(List<Intent> intents) {
        return intents.stream().map(Intent::id).toList();
    }

    private static Intent intent(String id, String passenger, EnumSet<Fact> reads, EnumSet<Fact> writes) {
        return new Intent(id, passenger, reads, writes);
    }
}
