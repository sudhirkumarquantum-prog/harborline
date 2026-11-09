# 0001. A write is an intent, not a procedure

## Status

Accepted

## Context

Passenger updates arrive as a pile of procedures: assign a seat, add a bag, stamp the check-in. Each procedure reaches into the same record. When the procedures call each other, the order becomes whatever the last editor happened to type, and a failure halfway through leaves a seat taken with no check-in stamp.

## Decision

The only write the desk accepts is an `Intent`. An intent names the passenger, the facts it reads, and the facts it writes. It does not call another intent. A scheduler orders the batch. A gate runs it. The concierge is not allowed to update a dossier by any other door.

Facts that are already true before the batch (a document that was checked at the counter) have no producer inside the batch. The desk still tests them, but they do not invent a fake upstream intent.

## Alternatives

- One service method per use case, with the order buried in the method body. Rejected: the order is not reviewable, and a new use case copies it.
- A rules engine that both decides and performs I/O. Rejected: a rule that reads a host and writes a seat cannot be reordered safely.

## Consequences

- New passenger updates are new intents plus their fact sets.
- The scheduler can refuse a batch before any host is called.
- Callers must say what they read and write. A vague "update passenger" intent cannot be ordered, so it is not representable here.
