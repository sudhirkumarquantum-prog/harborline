# 0002. Kahn orders facts; arrival order only breaks ties

## Status

Accepted

## Context

Two intents on the same passenger are safe to swap only when they do not touch the same fact. A bag quote does not care which seat was chosen. A check-in stamp does. Encoding that as hand-written `dependsOn` lists drifts the moment someone adds a fact and forgets an edge.

## Decision

Edges are derived.

- If A writes a fact and B reads it, A precedes B.
- If two intents write the same fact, the one that arrived earlier precedes the other.
- Intents for different passengers share no fact edge.
- Among intents that are both ready, the earlier arrival runs first, so a test can name the exact order.
- If a node is still waiting when the ready set is empty, the batch has a fact cycle and nothing runs.

This is the same ordering idea as a topological pass over rule dependencies. The algorithm is Kahn's, in the open, with the cycle reported as the ids that could not be scheduled.

## Alternatives

- Declare `dependsOn` on each intent. Rejected: the interesting edges are already in the read and write sets. A second list will disagree with the first.
- Sort by intent name. Rejected: names are not dependencies.
- Run everything in parallel and repair conflicts. Rejected: a stamp that ran before its seat exists is not a conflict you repair. It is a bad batch.

## Consequences

- A stamp that must observe a bag has to read `BAG`. Otherwise the scheduler is free to stamp first.
- Cycles fail the batch. There is no "best effort" partial apply.
- Arrival order is a tie-break, not a dependency. Tests that need a specific business order must express it as a fact edge.
