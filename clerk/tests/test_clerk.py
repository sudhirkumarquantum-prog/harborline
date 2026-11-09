import sys
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "clerk"))
sys.path.insert(0, str(ROOT / "concierge"))

from harborline.catalog import Catalog  # noqa: E402
from harborline.graph import GreedyDecider, Roster, Seat, ToolNotAllowed, check_in, load_roster  # noqa: E402
from harborline.graph import Dossier  # noqa: E402

from harborline_clerk.clerks import HandoffClerk, PartyClerk  # noqa: E402
from harborline_clerk.demo import render  # noqa: E402
from harborline_clerk.examiner import examine  # noqa: E402
from harborline_clerk.loop import consult  # noqa: E402
from harborline_clerk.proposal import Brief, IntentDraft, Plan, Refusal, seat_draft  # noqa: E402
from harborline_clerk.session import ClerkSession  # noqa: E402


def hb418() -> Roster:
    return load_roster(ROOT / "roster" / "hb418.json")


class ExaminerTest(unittest.TestCase):
    def test_an_unoffered_seat_is_refused_before_the_pair_is_considered(self) -> None:
        verdict = examine(
            Plan((seat_draft("H4K1", "12C"), seat_draft("H4K4", "1A")), party=True),
            hb418(),
        )
        self.assertIsInstance(verdict, Refusal)
        self.assertEqual("seat_not_offered", verdict.rule)

    def test_a_window_and_aisle_on_different_rows_is_not_a_pair(self) -> None:
        verdict = examine(
            Plan((seat_draft("H4K1", "12C"), seat_draft("H4K4", "14A")), party=True),
            hb418(),
        )
        self.assertEqual("seat_not_a_pair", verdict.rule)

    def test_the_adjacent_pair_is_admitted(self) -> None:
        verdict = examine(
            Plan((seat_draft("H4K1", "12C"), seat_draft("H4K4", "12A")), party=True),
            hb418(),
        )
        self.assertEqual("Admitted", type(verdict).__name__)

    def test_a_brief_that_carries_a_seat_is_refused(self) -> None:
        verdict = examine(Brief("document", "stop", (seat_draft("H4K3", "12C"),)), hb418())
        self.assertEqual("brief_cannot_write", verdict.rule)

    def test_a_clerk_cannot_write_the_check_in_stamp(self) -> None:
        stamp = IntentDraft("H4K1", "stamp", ("seat",), ("checkin",), "12C")
        verdict = examine(Plan((stamp,)), hb418())
        self.assertEqual("protocol_owns_stamp", verdict.rule)

    def test_a_fact_cycle_on_one_passenger_is_refused(self) -> None:
        plan = Plan(
            (
                IntentDraft("H4K1", "hold_seat", ("bag",), ("seat",), "left"),
                IntentDraft("H4K1", "hold_bag", ("seat",), ("bag",), "right"),
            )
        )
        verdict = examine(plan, hb418())
        self.assertEqual("cycle", verdict.rule)

    def test_a_missing_document_cannot_be_proposed_around(self) -> None:
        verdict = examine(Plan((seat_draft("H4K3", "16A"),)), hb418())
        self.assertEqual("document", verdict.rule)


class LoopTest(unittest.TestCase):
    def test_the_party_clerk_revises_once_and_stops(self) -> None:
        roster = hb418()
        session = ClerkSession(roster, Catalog.from_directory(ROOT / "docs" / "adr"), {})
        consultation = consult(PartyClerk(), session, roster)
        self.assertEqual(2, len(consultation.attempts))
        self.assertEqual("seat_not_offered", consultation.attempts[0].verdict.rule)
        self.assertIsNotNone(consultation.admitted)
        seats = [item.detail for item in consultation.admitted.proposal.intents]
        self.assertEqual(["12C", "12A"], seats)

    def test_the_handoff_clerk_drops_the_smuggled_seat(self) -> None:
        roster = hb418()
        leela = check_in(roster, "H4K3", GreedyDecider())
        session = ClerkSession(roster, Catalog.from_directory(ROOT / "docs" / "adr"), {"H4K3": leela})
        consultation = consult(HandoffClerk(), session, roster)
        self.assertEqual("brief_cannot_write", consultation.attempts[0].verdict.rule)
        brief = consultation.admitted.proposal
        self.assertIsInstance(brief, Brief)
        self.assertEqual((), brief.intents)
        self.assertIn("0005-the-graph-owns-the-tools.md", brief.narrative)
        self.assertIn("read_document", brief.narrative)
        self.assertNotIn("commit_intents", leela.trace)

    def test_commit_is_not_a_clerk_tool(self) -> None:
        session = ClerkSession(hb418(), Catalog.from_directory(ROOT / "docs" / "adr"), {})
        with self.assertRaises(ToolNotAllowed):
            session.commit_intents()
        self.assertEqual([], session.calls)


class CaptureTest(unittest.TestCase):
    def test_the_saved_transcript_matches_the_walkthrough(self) -> None:
        saved = (ROOT / "captures" / "clerk.txt").read_text()
        walkthrough = render(ROOT)
        self.assertIn(walkthrough, saved)
        self.assertIn("desk admits the party", saved)
        self.assertIn("host reads 1", saved)
        self.assertNotIn("commit_intents", walkthrough)


class ShapeTest(unittest.TestCase):
    def test_a_single_seat_party_is_not_a_pair(self) -> None:
        roster = Roster(
            "HB418",
            True,
            [Seat("12A", "window"), Seat("12C", "aisle")],
            {"H4K9": Dossier("H4K9", "Test Pax", "HB418", True, False, 0, "window")},
        )
        verdict = examine(Plan((seat_draft("H4K9", "12A"),), party=True), roster)
        self.assertEqual("party_needs_two", verdict.rule)


if __name__ == "__main__":
    unittest.main()
