package dev.harborline;

import java.util.List;

/** The batch reads and writes facts in a circle, so nothing was applied. */
public final class CycleException extends RuntimeException {

    private final List<String> stuck;

    public CycleException(List<String> stuck) {
        super("fact cycle among " + stuck);
        this.stuck = List.copyOf(stuck);
    }

    public List<String> stuck() {
        return stuck;
    }
}
