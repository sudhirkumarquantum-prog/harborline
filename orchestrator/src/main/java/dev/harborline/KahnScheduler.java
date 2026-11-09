package dev.harborline;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

/**
 * Orders a batch from the facts each intent reads and writes.
 * Arrival order is only the tie-break among intents that are both ready.
 */
public final class KahnScheduler {

    public List<Intent> order(List<Intent> intents) {
        if (intents.isEmpty()) {
            return List.of();
        }

        Map<String, Intent> byId = new LinkedHashMap<>();
        Map<String, Integer> arrival = new HashMap<>();
        for (int i = 0; i < intents.size(); i++) {
            Intent intent = intents.get(i);
            if (byId.put(intent.id(), intent) != null) {
                throw new IllegalArgumentException("duplicate intent id " + intent.id());
            }
            arrival.put(intent.id(), i);
        }

        Map<String, Set<String>> outgoing = new HashMap<>();
        Map<String, Integer> indegree = new HashMap<>();
        for (Intent intent : intents) {
            outgoing.put(intent.id(), new LinkedHashSet<>());
            indegree.put(intent.id(), 0);
        }

        Map<String, List<Intent>> byPassenger = new LinkedHashMap<>();
        for (Intent intent : intents) {
            byPassenger.computeIfAbsent(intent.passengerRef(), key -> new ArrayList<>()).add(intent);
        }
        for (List<Intent> group : byPassenger.values()) {
            linkFacts(group, arrival, outgoing, indegree);
        }

        PriorityQueue<String> ready = new PriorityQueue<>(Comparator.comparingInt(arrival::get));
        for (Map.Entry<String, Integer> entry : indegree.entrySet()) {
            if (entry.getValue() == 0) {
                ready.add(entry.getKey());
            }
        }

        List<Intent> ordered = new ArrayList<>();
        while (!ready.isEmpty()) {
            String id = ready.poll();
            ordered.add(byId.get(id));
            for (String next : outgoing.get(id)) {
                int left = indegree.merge(next, -1, Integer::sum);
                if (left == 0) {
                    ready.add(next);
                }
            }
        }

        if (ordered.size() != intents.size()) {
            List<String> stuck = indegree.entrySet().stream()
                    .filter(entry -> entry.getValue() > 0)
                    .map(Map.Entry::getKey)
                    .sorted()
                    .toList();
            throw new CycleException(stuck);
        }
        return List.copyOf(ordered);
    }

    private static void linkFacts(
            List<Intent> group,
            Map<String, Integer> arrival,
            Map<String, Set<String>> outgoing,
            Map<String, Integer> indegree) {
        Map<Fact, List<Intent>> writers = new EnumMap<>(Fact.class);
        Map<Fact, List<Intent>> readers = new EnumMap<>(Fact.class);
        for (Intent intent : group) {
            for (Fact fact : intent.writes()) {
                writers.computeIfAbsent(fact, key -> new ArrayList<>()).add(intent);
            }
            for (Fact fact : intent.reads()) {
                readers.computeIfAbsent(fact, key -> new ArrayList<>()).add(intent);
            }
        }
        for (Fact fact : Fact.values()) {
            List<Intent> writing = writers.getOrDefault(fact, List.of());
            List<Intent> reading = readers.getOrDefault(fact, List.of());
            for (int i = 0; i < writing.size(); i++) {
                for (int j = i + 1; j < writing.size(); j++) {
                    Intent left = writing.get(i);
                    Intent right = writing.get(j);
                    if (arrival.get(left.id()) < arrival.get(right.id())) {
                        edge(outgoing, indegree, left.id(), right.id());
                    } else {
                        edge(outgoing, indegree, right.id(), left.id());
                    }
                }
                for (Intent reader : reading) {
                    edge(outgoing, indegree, writing.get(i).id(), reader.id());
                }
            }
        }
    }

    private static void edge(
            Map<String, Set<String>> outgoing,
            Map<String, Integer> indegree,
            String from,
            String to) {
        if (from.equals(to)) {
            return;
        }
        if (outgoing.get(from).add(to)) {
            indegree.merge(to, 1, Integer::sum);
        }
    }
}
