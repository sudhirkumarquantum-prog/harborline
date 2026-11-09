# 0003. Lock the seat, then the passenger, in sorted order

## Status

Accepted

## Context

Two check-ins can ask for the same seat. Two updates can target the same passenger. A single lock for the whole flight makes the second case safe and makes the first case a queue, which throws away the reason for moving this work off a host. Locking in whatever order the threads arrive deadlocks: one thread holds passenger A and wants seat 12C, the other holds 12C and wants passenger A.

## Decision

Every exclusive run names the seats and the passengers it will touch. The gate builds lock keys `seat:{flight}:{seat}` and `pax:{passenger}`, sorts seats ahead of passengers, and sorts each group. Locks are acquired in that order and released in reverse. Threads that name disjoint seats and passengers do not share a lock. Threads that name the same seat, or the same passenger, cannot be inside the body together.

A flight-wide lock was tried on paper and rejected. It is an easy correctness story and a hard capacity story. Seat stripes keep the exclusion as narrow as the thing being mutated.

## Alternatives

- Synchronize on the flight. Rejected: one slow passenger blocks the aircraft.
- Synchronize on the passenger only. Rejected: two passengers can take 12C.
- Optimistic version columns with retry. Acceptable later. The lab keeps the lock because the failure mode is visible in a test without a database.

## Consequences

- Callers must know the seat before they enter the gate. A flow that discovers the seat while holding an unrelated lock does not belong here.
- The sorted key order is part of the contract. A new key prefix has to be placed in that total order, not pushed onto the front by a new caller.
