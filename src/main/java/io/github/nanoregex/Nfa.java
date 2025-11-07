package io.github.nanoregex;

import io.github.nanoregex.Node.*;

import java.util.*;
import java.util.function.IntPredicate;

/**
 * Thompson's construction + lock-step simulation.
 *
 * Instead of trying alternatives one by one and backtracking (exponential in the worst case,
 * the cause of "ReDoS" outages), we track the SET of all states the automaton could be in.
 * Every input character is processed once => O(input length x number of states), always.
 */
final class Nfa {
    enum Kind { CONSUME, SPLIT, MATCH }

    static final class State {
        final Kind kind;
        final String label;           // for CONSUME states
        final IntPredicate test;
        State out;                    // CONSUME: next state | SPLIT: first branch
        State out2;                   // SPLIT: second branch
        int id = -1;

        State(Kind kind, String label, IntPredicate test, State out, State out2) {
            this.kind = kind; this.label = label; this.test = test; this.out = out; this.out2 = out2;
        }
    }

    private final State start;

    private Nfa(State start) {
        this.start = start;
    }

    static Nfa compile(Node root) {
        State match = new State(Kind.MATCH, null, null, null, null);
        return new Nfa(build(root, match));
    }

    /** Builds the automaton "backwards": returns the entry state of {@code node}, given where to go afterwards. */
    private static State build(Node node, State next) {
        return switch (node) {
            case Chars c -> new State(Kind.CONSUME, c.label(), c.test(), next, null);
            case Cat c -> build(c.left(), build(c.right(), next));
            case Alt a -> new State(Kind.SPLIT, null, null, build(a.left(), next), build(a.right(), next));
            case Maybe o -> new State(Kind.SPLIT, null, null, build(o.inner(), next), next);
            case Star s -> {
                State loop = new State(Kind.SPLIT, null, null, null, next);
                loop.out = build(s.inner(), loop);
                yield loop;
            }
            case Plus p -> {
                State loop = new State(Kind.SPLIT, null, null, null, next);
                State entry = build(p.inner(), loop);
                loop.out = entry;
                yield entry;
            }
            case Empty e -> next;
        };
    }

    /** Does the WHOLE input match? */
    boolean matches(String input) {
        Set<State> current = new LinkedHashSet<>();
        addClosure(current, start);
        for (int i = 0; i < input.length() && !current.isEmpty(); i++) {
            current = step(current, input.charAt(i), null);
        }
        return containsMatch(current);
    }

    /** Does ANY substring match? (like Matcher.find()) */
    boolean contains(String input) {
        Set<State> current = new LinkedHashSet<>();
        addClosure(current, start);
        if (containsMatch(current)) return true;
        for (int i = 0; i < input.length(); i++) {
            current = step(current, input.charAt(i), start);  // a new match attempt may begin at every position
            if (containsMatch(current)) return true;
        }
        return false;
    }

    private Set<State> step(Set<State> current, char c, State restart) {
        Set<State> next = new LinkedHashSet<>();
        for (State s : current) {
            if (s.kind == Kind.CONSUME && s.test.test(c)) {
                addClosure(next, s.out);
            }
        }
        if (restart != null) addClosure(next, restart);
        return next;
    }

    /** Follows epsilon transitions (SPLIT states). The visited-set also protects against loops like (a*)*. */
    private static void addClosure(Set<State> set, State s) {
        if (s == null || !set.add(s)) return;
        if (s.kind == Kind.SPLIT) {
            addClosure(set, s.out);
            addClosure(set, s.out2);
        }
    }

    private static boolean containsMatch(Set<State> set) {
        for (State s : set) if (s.kind == Kind.MATCH) return true;
        return false;
    }

    /** Graphviz DOT source: paste into https://dreampuf.github.io/GraphvizOnline or run `dot -Tpng`. */
    String toDot() {
        List<State> order = new ArrayList<>();
        Deque<State> queue = new ArrayDeque<>(List.of(start));
        start.id = 0;
        order.add(start);
        Set<State> seen = new HashSet<>(order);
        while (!queue.isEmpty()) {
            State s = queue.poll();
            for (State t : new State[]{s.out, s.out2}) {
                if (t != null && seen.add(t)) {
                    t.id = order.size();
                    order.add(t);
                    queue.add(t);
                }
            }
        }
        StringBuilder sb = new StringBuilder("digraph NFA {\n  rankdir=LR;\n  node [shape=circle, fontname=\"Helvetica\"];\n");
        sb.append("  __start [shape=point];\n  __start -> s0;\n");
        for (State s : order) {
            sb.append("  s").append(s.id);
            if (s.kind == Kind.MATCH) sb.append(" [shape=doublecircle, label=\"✓\"]");
            else if (s.kind == Kind.SPLIT) sb.append(" [shape=point, width=0.12]");
            sb.append(";\n");
        }
        for (State s : order) {
            if (s.kind == Kind.CONSUME) {
                sb.append("  s%d -> s%d [label=\"%s\"];%n".formatted(s.id, s.out.id, escape(s.label)));
            } else if (s.kind == Kind.SPLIT) {
                sb.append("  s%d -> s%d [label=\"ε\"];%n".formatted(s.id, s.out.id));
                sb.append("  s%d -> s%d [label=\"ε\"];%n".formatted(s.id, s.out2.id));
            }
        }
        return sb.append("}\n").toString();
    }

    int stateCount() {
        Set<State> seen = new HashSet<>();
        Deque<State> stack = new ArrayDeque<>(List.of(start));
        while (!stack.isEmpty()) {
            State s = stack.pop();
            if (s != null && seen.add(s)) { stack.push(s.out == null ? s : s.out); if (s.out2 != null) stack.push(s.out2); }
        }
        return seen.size();
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
