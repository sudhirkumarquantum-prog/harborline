"""Walk HB418 and print the decisions a reviewer can check by eye."""

from __future__ import annotations

from pathlib import Path

from harborline.catalog import Catalog
from harborline.graph import Cassette, Drift, FixedDecider, GreedyDecider, check_in, load_roster


def run() -> None:
    root = Path(__file__).resolve().parents[2]
    roster_path = root / "roster" / "hb418.json"
    roster = load_roster(roster_path)
    passengers = list(roster.passengers.values())

    print("Harborline HB418   BLR → IXE")
    print("Synthetic passengers. The graph may commit only from the commit node.")
    print()
    print(f"{'passenger':<16} {'locator':<8} {'result':<20} seat   path")
    print("-" * 78)
    for dossier in passengers:
        outcome = check_in(roster, dossier.locator, GreedyDecider())
        seat = outcome.seat or "-"
        path = " → ".join(outcome.trace)
        print(f"{dossier.name:<16} {dossier.locator:<8} {outcome.reason:<20} {seat:<6} {path}")
        for intent in outcome.intents:
            reads = ",".join(intent.reads)
            writes = ",".join(intent.writes)
            print(f"{'':<16} {'':<8} intent {intent.name} reads {reads} writes {writes} ({intent.detail})")

    print()
    print("Cassette")
    recorded = Cassette()
    recorded_outcome = check_in(load_roster(roster_path), "H4K4", GreedyDecider(), recorded)
    print(f"  recorded Nico Park on {recorded_outcome.seat}, {len(recorded.frames)} tool frames")
    replay = Cassette(frames=list(recorded.frames), recording=False)
    try:
        check_in(load_roster(roster_path), "H4K4", FixedDecider("16A"), replay)
    except Drift as drift:
        print(f"  replay that commits 16A instead: {drift}")
    else:
        raise SystemExit("expected the cassette to reject a different seat")

    print()
    print("Catalog")
    catalog = Catalog.from_directory(root / "docs" / "adr")
    questions = (
        "host stampede single-flight seat map",
        "topological fact cycle kahn",
        "deadlock seat stripe lock order",
    )
    for question in questions:
        hit = catalog.ask(question, limit=1)[0]
        print(f"  {question}")
        print(f"    → {hit.name}  ({hit.score:.2f})")
