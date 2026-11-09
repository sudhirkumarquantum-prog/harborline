package dev.harborline;

import java.util.List;

/**
 * Admits the party the clerk walkthrough settled on: Asha in 12C, Nico in 12A.
 * Both seats are claimed under one gate acquisition, or neither is.
 */
public final class PartyDemo {

    private PartyDemo() {
    }

    public static void main(String[] args) {
        DepartureDesk desk = DepartureDesk.hb418(() -> "HB418-seatmap");
        List<Receipt> receipts = desk.admitAll(List.of(
                new PartyMember("H4K1", "12C", true, 1),
                new PartyMember("H4K4", "12A", true, 2)
        ));

        System.out.println("Harborline desk   admitted party HB418");
        System.out.println("One gate acquisition, seats then passengers, both sorted.");
        System.out.println();
        for (Receipt receipt : receipts) {
            System.out.printf("%s seat %s intents %s%n", receipt.passengerRef(), receipt.seat(), receipt.appliedIntentIds());
        }
        System.out.printf("host reads %d%n", desk.hostCalls());
    }
}
