# 0005. The graph owns the tools

## Status

Accepted

## Context

A check-in conversation is a small set of legal moves. Look the passenger up. Read the flight. Read the document. Read the seat map. Propose a seat that was actually offered. Quote bags. Commit. A model, or a later editor, that can call any tool from any step will stamp a check-in for a passenger it has not identified, or commit a seat it invented.

## Decision

The concierge is a graph of nodes. Each node has an allow-list. `ToolGuard` throws if the node calls anything else, and the call is not performed. The decider may only choose among seats the seat node already read. A seat that was not offered ends the journey as a handoff, with no commit.

A cassette records the tool name and payload. Replay fails when a later change makes the graph walk a different path. That is how a regression stays attached to the journey instead of to a screenshot.

Nodes:

- identify — already checked in is a finished journey with no write
- eligibility — a closed flight or a missing document hands off before any seat is touched
- seat — proposal must be one of the free seats of the preferred kind
- bags — skipped when the passenger has none
- commit — the only node allowed to write

## Alternatives

- A single prompt with the tools in the tool list. Rejected: the allow-list would be a paragraph, and a paragraph does not fail a test when it is ignored.
- Repair an illegal seat by picking another one silently. Rejected: the trace would claim the decider chose a seat it did not choose.

## Consequences

- Adding a tool means naming the nodes that may call it.
- The scripted decider is the test double. A model adapter would sit behind the same `choose_seat` method and would still be unable to commit from the seat node.
- Cassette drift is a failure, not a warning. Update the cassette in the same change as the journey.
