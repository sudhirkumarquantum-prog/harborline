"""Read-only tools a clerk may call. Commit is not one of them."""

from __future__ import annotations

from harborline.catalog import Catalog
from harborline.graph import Outcome, Roster, ToolNotAllowed

_ALLOWED = frozenset({"lookup_dossier", "read_seatmap", "read_trace", "ask_catalog"})


class ClerkSession:
    def __init__(self, roster: Roster, catalog: Catalog, outcomes: dict[str, Outcome]) -> None:
        self.roster = roster
        self.catalog = catalog
        self.outcomes = outcomes
        self.calls: list[str] = []

    def lookup_dossier(self, locator: str) -> object:
        self._allow("lookup_dossier", locator)
        return self.roster.passengers.get(locator)

    def read_seatmap(self, flight: str) -> list[str]:
        self._allow("read_seatmap", flight)
        if flight != self.roster.flight:
            return []
        return [seat.id for seat in self.roster.seats if seat.id not in self.roster.taken]

    def read_trace(self, locator: str) -> Outcome:
        self._allow("read_trace", locator)
        return self.outcomes[locator]

    def ask_catalog(self, query: str) -> str:
        self._allow("ask_catalog", query)
        hits = self.catalog.ask(query, limit=1)
        return hits[0].name if hits else ""

    def commit_intents(self) -> None:
        self._allow("commit_intents", "")

    def _allow(self, tool: str, payload: str) -> None:
        if tool not in _ALLOWED:
            raise ToolNotAllowed("clerk", tool)
        self.calls.append(f"{tool} {payload}".strip())
