import json
import sys
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "concierge"))

from harborline.graph import (  # noqa: E402
    Cassette,
    Dossier,
    Drift,
    FixedDecider,
    GreedyDecider,
    Roster,
    Seat,
    ToolGuard,
    ToolNotAllowed,
    check_in,
    load_roster,
)


def hb418() -> Roster:
    return load_roster(ROOT / "roster" / "hb418.json")


class CheckInTest(unittest.TestCase):
    def test_aisle_preference_commits_the_offered_seat(self) -> None:
        outcome = check_in(hb418(), "H4K1", GreedyDecider())
        self.assertEqual("checked_in", outcome.status)
        self.assertEqual("12C", outcome.seat)
        self.assertEqual(
            ["lookup_dossier", "read_flight", "read_document", "read_seatmap", "propose_seat", "quote_bag", "commit_intents"],
            outcome.trace,
        )
        self.assertEqual(["assign_seat", "add_bag"], [item.name for item in outcome.intents])

    def test_already_checked_in_does_not_write(self) -> None:
        outcome = check_in(hb418(), "H4K2", GreedyDecider())
        self.assertEqual("already_checked_in", outcome.status)
        self.assertEqual(["lookup_dossier"], outcome.trace)
        self.assertEqual([], outcome.intents)
        self.assertFalse(outcome.committed)

    def test_missing_document_never_reads_the_seat_map(self) -> None:
        outcome = check_in(hb418(), "H4K3", GreedyDecider())
        self.assertEqual("handoff", outcome.status)
        self.assertEqual("document", outcome.reason)
        self.assertNotIn("read_seatmap", outcome.trace)
        self.assertNotIn("commit_intents", outcome.trace)

    def test_closed_flight_stops_before_the_document(self) -> None:
        roster = hb418()
        roster.open_for_checkin = False
        outcome = check_in(roster, "H4K1", GreedyDecider())
        self.assertEqual("flight_closed", outcome.reason)
        self.assertEqual(["lookup_dossier", "read_flight"], outcome.trace)

    def test_a_seat_that_was_not_offered_is_not_committed(self) -> None:
        outcome = check_in(hb418(), "H4K4", FixedDecider("1A"))
        self.assertEqual("seat_not_offered", outcome.reason)
        self.assertNotIn("commit_intents", outcome.trace)
        self.assertIn("seat:unoffered:1A", outcome.violations)

    def test_identify_cannot_commit(self) -> None:
        guard = ToolGuard()
        guard.node = "identify"
        with self.assertRaises(ToolNotAllowed):
            guard.call("commit_intents", "H4K1", lambda: "nope")
        self.assertEqual([], guard.trace)

    def test_replay_detects_a_different_seat_choice(self) -> None:
        roster = hb418()
        recorded = Cassette()
        first = check_in(roster, "H4K4", GreedyDecider(), recorded)
        self.assertEqual("12A", first.seat)

        fresh = hb418()
        replay = Cassette(frames=json.loads(json.dumps(recorded.frames)), recording=False)
        with self.assertRaises(Drift):
            check_in(fresh, "H4K4", FixedDecider("16A"), replay)

    def test_replay_accepts_the_same_journey(self) -> None:
        recorded = Cassette()
        check_in(hb418(), "H4K4", GreedyDecider(), recorded)
        replay = Cassette(frames=list(recorded.frames), recording=False)
        again = check_in(hb418(), "H4K4", GreedyDecider(), replay)
        self.assertEqual("checked_in", again.status)
        self.assertEqual(len(recorded.frames), replay._cursor)

    def test_unknown_locator_hands_off(self) -> None:
        outcome = check_in(hb418(), "NOPE", GreedyDecider())
        self.assertEqual("unknown_passenger", outcome.reason)


class RosterShapeTest(unittest.TestCase):
    def test_window_passenger_is_offered_a_window(self) -> None:
        roster = Roster(
            "HB418",
            True,
            [Seat("12A", "window"), Seat("12C", "aisle")],
            {
                "H4K9": Dossier("H4K9", "Test Pax", "HB418", True, False, 0, "window"),
            },
        )
        outcome = check_in(roster, "H4K9", GreedyDecider())
        self.assertEqual("12A", outcome.seat)
        self.assertEqual(["assign_seat"], [item.name for item in outcome.intents])


if __name__ == "__main__":
    unittest.main()
