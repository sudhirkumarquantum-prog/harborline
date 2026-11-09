package dev.harborline;

/** One passenger in a plan the examiner has already admitted. */
public record PartyMember(String passenger, String seat, boolean documentOk, int bags) {

    public PartyMember {
        if (passenger == null || passenger.isBlank()) {
            throw new IllegalArgumentException("passenger is required");
        }
        if (seat == null || seat.isBlank()) {
            throw new IllegalArgumentException("seat is required");
        }
        if (bags < 0) {
            throw new IllegalArgumentException("bags");
        }
    }
}
