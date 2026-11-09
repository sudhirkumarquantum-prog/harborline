package dev.harborline;

@FunctionalInterface
public interface TimeSource {
    long millis();

    static TimeSource wall() {
        return System::currentTimeMillis;
    }
}
