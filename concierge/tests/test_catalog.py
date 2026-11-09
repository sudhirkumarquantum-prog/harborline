import sys
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "concierge"))

from harborline.catalog import Catalog  # noqa: E402


class CatalogTest(unittest.TestCase):
    def setUp(self) -> None:
        self.catalog = Catalog.from_directory(ROOT / "docs" / "adr")

    def test_a_stampede_question_finds_the_host_memo(self) -> None:
        hits = self.catalog.ask("host stampede single-flight seat map")
        self.assertTrue(hits)
        self.assertEqual("0004-one-host-read-per-key.md", hits[0].name)

    def test_a_cycle_question_finds_kahn(self) -> None:
        hits = self.catalog.ask("topological fact cycle kahn")
        self.assertEqual("0002-kahn-orders-facts.md", hits[0].name)

    def test_a_deadlock_question_finds_the_lock_order(self) -> None:
        hits = self.catalog.ask("deadlock seat stripe lock order")
        self.assertEqual("0003-lock-seats-then-passengers.md", hits[0].name)

    def test_an_empty_query_returns_nothing(self) -> None:
        self.assertEqual([], self.catalog.ask("   "))


if __name__ == "__main__":
    unittest.main()
