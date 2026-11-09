"""Print the party plan and the document brief the way a reviewer can read them."""

from __future__ import annotations

from pathlib import Path

from harborline.catalog import Catalog
from harborline.graph import GreedyDecider, check_in, load_roster

from harborline_clerk.clerks import HandoffClerk, PartyClerk
from harborline_clerk.loop import Consultation, consult
from harborline_clerk.proposal import Admitted, Brief, Plan, Refusal
from harborline_clerk.session import ClerkSession


def render(root: Path | None = None) -> str:
    root = root or Path(__file__).resolve().parents[2]
    roster = load_roster(root / "roster" / "hb418.json")
    catalog = Catalog.from_directory(root / "docs" / "adr")
    leela = check_in(roster, "H4K3", GreedyDecider())
    lines = [
        "Harborline clerk   HB418",
        "The clerk proposes. The examiner admits. The desk is the only writer.",
        "",
        'Party: "sit Nico in the window beside Asha"',
    ]
    party_session = ClerkSession(roster, catalog, {"H4K3": leela})
    party = consult(PartyClerk(), party_session, roster)
    lines.append("  clerk reads: " + ", ".join(_once(party_session.calls)))
    lines.extend(_consultation(party))
    if isinstance(party.admitted, Admitted) and isinstance(party.admitted.proposal, Plan):
        seats = " ".join(
            f"{item.passenger}={item.detail}"
            for item in party.admitted.proposal.intents
            if item.name == "assign_seat"
        )
        lines.append(f"  desk receives assign_seat {seats}")
    lines.append("")
    lines.append("Handoff: Leela Das, missing document")
    lines.append(f"  concierge trace: {' → '.join(leela.trace)}")
    brief = consult(HandoffClerk(), ClerkSession(roster, catalog, {"H4K3": leela}), roster)
    lines.extend(_consultation(brief))
    if isinstance(brief.admitted, Admitted) and isinstance(brief.admitted.proposal, Brief):
        lines.append("  desk not called")
        lines.append(f"  {brief.admitted.proposal.narrative}")
    return "\n".join(lines)


def _once(calls: list[str]) -> list[str]:
    seen: list[str] = []
    for call in calls:
        if call not in seen:
            seen.append(call)
    return seen


def _consultation(consultation: Consultation) -> list[str]:
    lines = []
    for number, attempt in enumerate(consultation.attempts, start=1):
        lines.append(f"  attempt {number} {_describe(attempt.proposal)}")
        verdict = attempt.verdict
        if isinstance(verdict, Refusal):
            lines.append(f"  refused {verdict.rule} {verdict.detail}".rstrip())
        else:
            lines.append("  admitted")
    return lines


def _describe(proposal: Plan | Brief) -> str:
    if isinstance(proposal, Plan):
        seats = " ".join(
            f"{item.passenger}={item.detail}" for item in proposal.intents if item.name == "assign_seat"
        )
        return f"plan {seats}"
    smuggled = ", ".join(item.name for item in proposal.intents)
    suffix = f" smuggling {smuggled}" if smuggled else ""
    return f"brief {proposal.reason}{suffix}"
