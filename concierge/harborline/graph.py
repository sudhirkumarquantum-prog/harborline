"""Check-in as a graph. Each node may call only the tools listed for it."""

from __future__ import annotations

import json
from dataclasses import dataclass, field
from pathlib import Path
from typing import Callable


class ToolNotAllowed(Exception):
    def __init__(self, node: str, tool: str) -> None:
        super().__init__(f"{node} cannot call {tool}")
        self.node = node
        self.tool = tool


class Drift(Exception):
    """Replay saw a different tool call than the cassette recorded."""


class SeatTaken(Exception):
    """The offered seat was claimed between the proposal and the commit."""


ALLOWED: dict[str, frozenset[str]] = {
    "identify": frozenset({"lookup_dossier"}),
    "eligibility": frozenset({"read_flight", "read_document"}),
    "seat": frozenset({"read_seatmap", "propose_seat"}),
    "bags": frozenset({"quote_bag"}),
    "commit": frozenset({"commit_intents"}),
}


@dataclass(frozen=True)
class Seat:
    id: str
    kind: str


@dataclass(frozen=True)
class Dossier:
    locator: str
    name: str
    flight: str
    document_ok: bool
    checked_in: bool
    bags: int
    preference: str


@dataclass
class IntentNote:
    name: str
    reads: tuple[str, ...]
    writes: tuple[str, ...]
    detail: str


@dataclass
class Cassette:
    """Records tool name plus payload, or fails when replay diverges."""

    frames: list[dict[str, str]] = field(default_factory=list)
    recording: bool = True
    _cursor: int = 0

    def see(self, tool: str, payload: str) -> None:
        if self.recording:
            self.frames.append({"tool": tool, "payload": payload})
            return
        if self._cursor >= len(self.frames):
            raise Drift(f"extra call {tool} payload={payload}")
        expected = self.frames[self._cursor]
        self._cursor += 1
        if expected["tool"] != tool or expected["payload"] != payload:
            raise Drift(
                f"expected {expected['tool']} {expected['payload']}, saw {tool} {payload}"
            )


@dataclass
class Outcome:
    status: str
    reason: str
    seat: str | None
    intents: list[IntentNote]
    trace: list[str]
    violations: list[str]

    @property
    def committed(self) -> bool:
        return self.status == "checked_in"


class ToolGuard:
    def __init__(self, cassette: Cassette | None = None) -> None:
        self.node = ""
        self.trace: list[str] = []
        self.violations: list[str] = []
        self.cassette = cassette or Cassette()

    def call(self, tool: str, payload: str, fn: Callable[[], object]) -> object:
        if tool not in ALLOWED.get(self.node, frozenset()):
            self.violations.append(f"{self.node}:{tool}")
            raise ToolNotAllowed(self.node, tool)
        self.cassette.see(tool, payload)
        self.trace.append(tool)
        return fn()


class Roster:
    def __init__(self, flight: str, open_for_checkin: bool, seats: list[Seat], passengers: dict[str, Dossier]) -> None:
        self.flight = flight
        self.open_for_checkin = open_for_checkin
        self.seats = seats
        self.passengers = passengers
        self.taken: set[str] = set()

    def free(self, kind: str) -> list[str]:
        return [seat.id for seat in self.seats if seat.kind == kind and seat.id not in self.taken]


def load_roster(path: Path) -> Roster:
    raw = json.loads(path.read_text())
    passengers = {
        item["locator"]: Dossier(
            locator=item["locator"],
            name=item["name"],
            flight=raw["flight"],
            document_ok=item["documentOk"],
            checked_in=item["checkedIn"],
            bags=item["bags"],
            preference=item["preference"],
        )
        for item in raw["passengers"]
    }
    seats = [Seat(item["id"], item["kind"]) for item in raw["seats"]]
    return Roster(raw["flight"], raw["open"], seats, passengers)


class GreedyDecider:
    """Picks the first offered seat of the passenger's preferred kind."""

    def choose_seat(self, dossier: Dossier, offered: list[str]) -> str:
        return offered[0]


class FixedDecider:
    def __init__(self, seat: str) -> None:
        self.seat = seat

    def choose_seat(self, dossier: Dossier, offered: list[str]) -> str:
        return self.seat


def check_in(roster: Roster, locator: str, decider: object, cassette: Cassette | None = None) -> Outcome:
    guard = ToolGuard(cassette)
    intents: list[IntentNote] = []
    chosen: dict[str, str] = {}

    def finish(status: str, reason: str, seat: str | None = None) -> Outcome:
        return Outcome(status, reason, seat, intents, list(guard.trace), list(guard.violations))

    guard.node = "identify"
    dossier = guard.call("lookup_dossier", locator, lambda: roster.passengers.get(locator))
    if not isinstance(dossier, Dossier):
        return finish("handoff", "unknown_passenger")
    if dossier.checked_in:
        return finish("already_checked_in", "already_checked_in")

    guard.node = "eligibility"
    flight_open = guard.call("read_flight", dossier.flight, lambda: roster.open_for_checkin and dossier.flight == roster.flight)
    if flight_open is not True:
        return finish("handoff", "flight_closed")
    document_ok = guard.call("read_document", dossier.locator, lambda: dossier.document_ok)
    if document_ok is not True:
        return finish("handoff", "document")

    guard.node = "seat"
    offered = guard.call(
        "read_seatmap",
        f"{dossier.flight}:{dossier.preference}",
        lambda: roster.free(dossier.preference),
    )
    if not isinstance(offered, list) or not offered:
        return finish("handoff", "no_seat")
    proposal = guard.call(
        "propose_seat",
        ",".join(offered),
        lambda: decider.choose_seat(dossier, offered),  # type: ignore[attr-defined]
    )
    if proposal not in offered:
        guard.violations.append(f"seat:unoffered:{proposal}")
        return finish("handoff", "seat_not_offered")
    chosen["seat"] = str(proposal)
    intents.append(IntentNote("assign_seat", ("seatmap",), ("seat",), str(proposal)))

    if dossier.bags > 0:
        guard.node = "bags"
        quote = guard.call("quote_bag", f"{dossier.locator}:{dossier.bags}", lambda: dossier.bags)
        intents.append(IntentNote("add_bag", ("pnr",), ("bag",), str(quote)))

    guard.node = "commit"
    seat = chosen["seat"]

    def _commit() -> str:
        if seat in roster.taken:
            raise SeatTaken(seat)
        roster.taken.add(seat)
        return seat

    try:
        guard.call("commit_intents", f"{dossier.locator}:{seat}:{dossier.bags}", _commit)
    except SeatTaken:
        return finish("handoff", "seat_taken", seat)
    return finish("checked_in", "checked_in", seat)
