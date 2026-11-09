package dev.harborline;

import java.util.List;

public record Receipt(String passengerRef, String flight, String seat, List<String> appliedIntentIds) {

    public Receipt {
        appliedIntentIds = List.copyOf(appliedIntentIds);
    }
}
