# 0007. The clerk proposes; the examiner admits; the desk writes

## Status

Accepted

## Context

The check-in graph already knows the legal journey for one passenger with a stored preference. A model that re-walks identify, eligibility, seat, bags, and commit adds a failure mode the cassette would then have to absorb. The sentences that are not a stored preference are different. "Sit my child in the window beside me" is a party plan. A missing document is a handoff, and the useful work there is a brief a person can read, not another attempt to stamp the check-in.

## Decision

A clerk may emit only a proposal. A plan names seat and bag intents, the facts each one reads and writes, and whether the seats must be an adjacent pair. A brief is a handoff note and must carry no intents. The clerk's tools are read-only: dossier, seat map, the concierge trace, and the ADR catalog. `commit_intents` is not on that list.

An examiner with no model admits the proposal or returns one rule. It refuses a seat that was not offered, a pair that does not share a row, a missing document, a fact cycle, a stamp of `checkin`, and a brief that smuggles a write. The clerk may revise once, and the refusal is the only new context. After that the consultation stops.

The desk receives an admitted plan only. It appends the check-in stamp, orders the batch with Kahn, and acquires every seat lock and then every passenger lock before claiming any seat. If one seat is already taken, the seats claimed in that acquisition are released. A brief never reaches the desk.

## Alternatives

- Let the model call the desk's tools, with the allow-list written in the prompt. Rejected: 0005 already refuses that shape. A prompt does not fail a test when it is ignored.
- Let the clerk commit the first passenger and then the second. Rejected: a failure on the second seat would leave the first passenger seated inside a party that did not fit.
- Skip the examiner and trust the scripted clerk. Rejected: the scripted clerk is the stand-in for a model, and the first proposal is wrong on purpose.

## Consequences

- A model adapter implements `propose` and still cannot stamp `checkin` or commit from the clerk session.
- New refusal rules are examiner tests. They are not paragraphs in a prompt.
- The happy path for one passenger with a stored preference stays on the graph and `GreedyDecider`.
