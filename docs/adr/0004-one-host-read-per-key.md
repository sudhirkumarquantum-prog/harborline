# 0004. One host read in flight per key

## Status

Accepted

## Context

The seat map is the expensive read. A check-in desk that asks the host once per passenger pays for the same bytes for the whole cabin. A naive cache then has a stampede: the first wave of a departure all miss together and all call the host.

## Decision

`HostMemo` keeps a value until its time-to-live, and it keeps one in-flight future per key. A second caller for that key waits for the first future. It does not start a second host call. When the value expires, the next caller may miss. Failures are not cached. The memo counts host calls and waits separately, so a test can show that twenty check-ins on HB418 produced one seat-map read.

The clock is injected. Tests move it. Production passes wall time.

## Alternatives

- Cache without single-flight. Rejected: the stampede is the bug this component exists to remove.
- Cache failures. Rejected: a down host would pin the outage for the whole TTL.
- A distributed lock around the host. Heavier than this desk needs. The in-process future is the local form of the same idea; a shared cache would sit behind the same interface.

## Consequences

- Values can be stale for the TTL. Seat inventory is not read from the memo. The memo holds the host's published map. The take happens on the local board under the seat lock.
- Callers share a future, so a host exception reaches every waiter.
- Keys must include the flight and the resource. A key of "seatmap" would mix aircraft.
