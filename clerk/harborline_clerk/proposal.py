"""What a clerk is allowed to hand to the examiner."""

from __future__ import annotations

from dataclasses import dataclass


@dataclass(frozen=True)
class IntentDraft:
    passenger: str
    name: str
    reads: tuple[str, ...]
    writes: tuple[str, ...]
    detail: str


@dataclass(frozen=True)
class Plan:
    """Seat and bag intents. The desk appends the check-in stamp itself."""

    intents: tuple[IntentDraft, ...]
    party: bool = False


@dataclass(frozen=True)
class Brief:
    """A note for a person. It is not allowed to carry a write."""

    reason: str
    narrative: str
    intents: tuple[IntentDraft, ...] = ()


@dataclass(frozen=True)
class Refusal:
    rule: str
    detail: str


@dataclass(frozen=True)
class Admitted:
    proposal: Plan | Brief


def seat_draft(passenger: str, seat: str) -> IntentDraft:
    return IntentDraft(passenger, "assign_seat", ("seatmap",), ("seat",), seat)
