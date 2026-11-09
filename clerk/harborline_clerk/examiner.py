"""Admits a proposal, or returns the rule it broke. No model calls live here."""

from __future__ import annotations

import re
from collections import defaultdict

from harborline.graph import Dossier, Roster

from harborline_clerk.proposal import Admitted, Brief, IntentDraft, Plan, Refusal

_ROW = re.compile(r"^(\d+)[A-Z]$")
_FACTS = frozenset({"seatmap", "pnr", "seat", "bag", "document", "checkin"})
_CLERK_WRITES = frozenset({"seat", "bag"})


def examine(proposal: Plan | Brief, roster: Roster) -> Admitted | Refusal:
    if isinstance(proposal, Brief):
        return _examine_brief(proposal)
    return _examine_plan(proposal, roster)


def _examine_brief(proposal: Brief) -> Admitted | Refusal:
    if proposal.intents:
        names = ", ".join(item.name for item in proposal.intents)
        return Refusal("brief_cannot_write", names)
    if not proposal.narrative.strip():
        return Refusal("brief_empty", proposal.reason)
    return Admitted(proposal)


def _examine_plan(proposal: Plan, roster: Roster) -> Admitted | Refusal:
    if not roster.open_for_checkin:
        return Refusal("flight_closed", roster.flight)
    if not proposal.intents:
        return Refusal("plan_empty", roster.flight)

    seen: set[str] = set()
    seats: list[tuple[str, str]] = []
    for intent in proposal.intents:
        identity = f"{intent.passenger}:{intent.name}:{intent.detail}"
        if identity in seen:
            return Refusal("duplicate_intent", identity)
        seen.add(identity)

        broken = _intent_shape(intent)
        if broken is not None:
            return broken
        dossier = roster.passengers.get(intent.passenger)
        if not isinstance(dossier, Dossier):
            return Refusal("unknown_passenger", intent.passenger)
        if dossier.checked_in:
            return Refusal("already_checked_in", intent.passenger)
        if not dossier.document_ok:
            return Refusal("document", intent.passenger)
        if intent.name == "assign_seat":
            offered = {seat.id: seat for seat in roster.seats if seat.id not in roster.taken}
            if intent.detail not in offered:
                return Refusal("seat_not_offered", f"{intent.passenger} seat {intent.detail} was not offered")
            seats.append((intent.passenger, intent.detail))

    stuck = _cycle(proposal.intents)
    if stuck:
        return Refusal("cycle", " ".join(stuck))

    if proposal.party:
        pair = _pair(seats, roster)
        if isinstance(pair, Refusal):
            return pair
    return Admitted(proposal)


def _intent_shape(intent: IntentDraft) -> Refusal | None:
    if not intent.reads or not intent.writes:
        return Refusal("intent_must_declare_facts", f"{intent.passenger}:{intent.name}")
    unknown = [fact for fact in (*intent.reads, *intent.writes) if fact not in _FACTS]
    if unknown:
        return Refusal("unknown_fact", unknown[0])
    illegal = [fact for fact in intent.writes if fact not in _CLERK_WRITES]
    if illegal:
        return Refusal("protocol_owns_stamp", illegal[0])
    if intent.name == "assign_seat" and "seat" not in intent.writes:
        return Refusal("intent_must_declare_facts", intent.passenger)
    return None


def _pair(seats: list[tuple[str, str]], roster: Roster) -> Refusal | None:
    if len(seats) != 2:
        return Refusal("party_needs_two", str(len(seats)))
    kinds = {seat.id: seat.kind for seat in roster.seats}
    rows: list[str] = []
    seat_kinds: list[str] = []
    for _passenger, seat in seats:
        match = _ROW.match(seat)
        if match is None:
            return Refusal("seat_not_offered", seat)
        rows.append(match.group(1))
        seat_kinds.append(kinds[seat])
    if rows[0] != rows[1] or seat_kinds[0] == seat_kinds[1]:
        return Refusal("seat_not_a_pair", f"{seats[0][1]} {seats[1][1]}")
    return None


def _cycle(intents: tuple[IntentDraft, ...]) -> list[str]:
    by_passenger: dict[str, list[IntentDraft]] = defaultdict(list)
    for intent in intents:
        by_passenger[intent.passenger].append(intent)

    stuck: list[str] = []
    for group in by_passenger.values():
        stuck.extend(_kahn(group))
    return stuck


def _kahn(group: list[IntentDraft]) -> list[str]:
    ids = [f"{item.passenger}:{item.name}:{item.detail}" for item in group]
    index = {intent_id: position for position, intent_id in enumerate(ids)}
    outgoing: dict[str, set[str]] = {intent_id: set() for intent_id in ids}
    indegree = {intent_id: 0 for intent_id in ids}

    writers: dict[str, list[int]] = defaultdict(list)
    readers: dict[str, list[int]] = defaultdict(list)
    for position, intent in enumerate(group):
        for fact in intent.writes:
            writers[fact].append(position)
        for fact in intent.reads:
            readers[fact].append(position)

    def edge(left: int, right: int) -> None:
        if left == right:
            return
        source, target = ids[left], ids[right]
        if target not in outgoing[source]:
            outgoing[source].add(target)
            indegree[target] += 1

    for fact in set(writers) | set(readers):
        writing = writers.get(fact, [])
        reading = readers.get(fact, [])
        ordered = sorted(writing, key=lambda position: index[ids[position]])
        for cursor, writer in enumerate(ordered):
            for later in ordered[cursor + 1 :]:
                edge(writer, later)
            for reader in reading:
                edge(writer, reader)

    ready = sorted((intent_id for intent_id, degree in indegree.items() if degree == 0), key=index.get)
    ordered: list[str] = []
    while ready:
        current = ready.pop(0)
        ordered.append(current)
        for nxt in sorted(outgoing[current], key=index.get):
            indegree[nxt] -= 1
            if indegree[nxt] == 0:
                ready.append(nxt)
                ready.sort(key=index.get)
    if len(ordered) == len(ids):
        return []
    return sorted(intent_id for intent_id, degree in indegree.items() if degree > 0)
