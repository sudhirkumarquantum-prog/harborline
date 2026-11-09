package dev.harborline;

/**
 * A fact is something an intent can read or write.
 * Edges in the scheduler are derived from these sets.
 */
public enum Fact {
    SEATMAP,
    PNR,
    SEAT,
    BAG,
    DOCUMENT,
    CHECKIN
}
