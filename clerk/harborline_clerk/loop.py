"""One proposal, one refusal, one revision. Then the clerk stops."""

from __future__ import annotations

from dataclasses import dataclass
from typing import Protocol

from harborline.graph import Roster

from harborline_clerk.examiner import examine
from harborline_clerk.proposal import Admitted, Brief, Plan, Refusal
from harborline_clerk.session import ClerkSession


class Clerk(Protocol):
    def propose(self, session: ClerkSession, refusal: Refusal | None) -> Plan | Brief:
        """Return the next proposal. The refusal is None on the first attempt."""


@dataclass(frozen=True)
class Attempt:
    proposal: Plan | Brief
    verdict: Admitted | Refusal


@dataclass
class Consultation:
    attempts: list[Attempt]

    @property
    def admitted(self) -> Admitted | None:
        verdict = self.attempts[-1].verdict
        return verdict if isinstance(verdict, Admitted) else None


def consult(clerk: Clerk, session: ClerkSession, roster: Roster) -> Consultation:
    refusal: Refusal | None = None
    attempts: list[Attempt] = []
    for _ in range(2):
        proposal = clerk.propose(session, refusal)
        verdict = examine(proposal, roster)
        attempts.append(Attempt(proposal, verdict))
        if isinstance(verdict, Admitted):
            break
        refusal = verdict
    return Consultation(attempts)
