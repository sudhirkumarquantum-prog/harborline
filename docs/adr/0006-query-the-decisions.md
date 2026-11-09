# 0006. Query the decisions, not a pile of pages

## Status

Accepted

## Context

An agent that searches a wiki will quote a status page from 2019 with the same confidence as the decision that replaced it. The pages that should win are the ones that record a decision, the alternatives, and the consequence.

## Decision

The catalog indexes the ADRs in this repository with a small TF-IDF ranker. No model and no network. A query about lock order should surface 0003. A query about a host stampede should surface 0004. The ranker is ordinary on purpose: the value is the corpus, which is a set of decisions with a status, not a dump of every document the team has ever written.

## Alternatives

- Embeddings against every page the team can export. Useful later, and easy to poison with stale runbooks. This lab keeps the corpus narrow enough to read.
- Keyword search with no weighting. Rejected: "lock" appears in more than one decision. Inverse document frequency is what makes the seat-stripe decision win.

## Consequences

- A new ADR joins the index by being a file in `docs/adr`. It should use the words a future reader will actually type.
- The score is a ranking, not an answer. The concierge does not execute whatever paragraph came back first.
