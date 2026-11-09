package dev.harborline;

/** The desk refused the check-in before a receipt was issued. */
public final class Rejected extends RuntimeException {

    private final String reason;

    public Rejected(String reason) {
        super(reason);
        this.reason = reason;
    }

    public String reason() {
        return reason;
    }
}
