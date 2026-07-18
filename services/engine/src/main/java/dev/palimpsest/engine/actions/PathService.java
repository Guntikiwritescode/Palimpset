package dev.palimpsest.engine.actions;

import dev.palimpsest.engine.store.ActionStore;
import dev.palimpsest.engine.store.ActionStore.Neighbor;
import dev.palimpsest.engine.store.SummaryStore;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Bounded connection-finding (Flow H). Breadth-first over asserted, entity-ranged
 * claims passing the confidence + temporal predicate, under a hard depth cap,
 * path cap, and 2-second budget — returning partial results with budgetExceeded
 * rather than hanging. Each path reports its weakest link (min edge confidence).
 */
@Service
public class PathService {

    private static final long BUDGET_NANOS = 2_000_000_000L;

    private final ActionStore store;
    private final SummaryStore summaries;

    public PathService(ActionStore store, SummaryStore summaries) {
        this.store = store;
        this.summaries = summaries;
    }

    public record PathStep(Long entityId, String displayName, Long claimId, Double confidence) {
    }

    public record PathResult(List<List<PathStep>> paths, boolean budgetExceeded, boolean truncated) {
    }

    private record Frontier(long entityId, List<long[]> viaClaims, List<Double> confs, double weakest) {
    }

    public PathResult find(long from, long to, double minConfidence, String w0, String w1,
                           int maxDepth, int maxPaths) {
        long start = System.nanoTime();
        List<List<PathStep>> results = new ArrayList<>();
        boolean budgetExceeded = false;

        Deque<Frontier> queue = new ArrayDeque<>();
        queue.add(new Frontier(from, new ArrayList<>(), new ArrayList<>(), 1.0));
        java.util.Set<Long> visited = new java.util.HashSet<>();
        visited.add(from);
        int depth = 0;

        outer:
        while (!queue.isEmpty() && depth < maxDepth) {
            int levelSize = queue.size();
            depth++;
            for (int i = 0; i < levelSize; i++) {
                if (System.nanoTime() - start > BUDGET_NANOS) {
                    budgetExceeded = true;
                    break outer;
                }
                Frontier f = queue.poll();
                for (Neighbor nb : store.neighbors(f.entityId(), minConfidence, w0, w1)) {
                    List<long[]> via = new ArrayList<>(f.viaClaims());
                    via.add(new long[]{nb.claimId(), nb.counterpartId()});
                    List<Double> confs = new ArrayList<>(f.confs());
                    confs.add(nb.confidence());
                    double weakest = Math.min(f.weakest(), nb.confidence());
                    if (nb.counterpartId() == to) {
                        results.add(buildPath(from, via, confs));
                        if (results.size() >= maxPaths) {
                            break outer;
                        }
                    } else if (!visited.contains(nb.counterpartId())) {
                        visited.add(nb.counterpartId());
                        queue.add(new Frontier(nb.counterpartId(), via, confs, weakest));
                    }
                }
            }
        }
        return new PathResult(results, budgetExceeded, results.size() >= maxPaths);
    }

    private List<PathStep> buildPath(long from, List<long[]> via, List<Double> confs) {
        List<PathStep> steps = new ArrayList<>();
        String fromName = summaries.summary(from).map(s -> s.displayName()).orElse("#" + from);
        steps.add(new PathStep(from, fromName, null, null));
        long cursor = from;
        for (int i = 0; i < via.size(); i++) {
            long claimId = via.get(i)[0];
            long next = via.get(i)[1];
            String name = summaries.summary(next).map(s -> s.displayName()).orElse("#" + next);
            steps.add(new PathStep(next, name, claimId, confs.get(i)));
            cursor = next;
        }
        return steps;
    }
}
