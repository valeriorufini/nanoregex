# NanoRegex

A regular-expression engine built on **Thompson's NFA construction** and **lock-step simulation**.
Where `java.util.regex` backtracks (and can take exponential or high-polynomial time on pathological
patterns, the root cause of many real-world "ReDoS" outages), NanoRegex tracks the set of all possible
automaton states and is **always O(input × pattern)**.

```text
== Backtracking race: pattern (.*a){10}b on 'aaa...a' (no 'b' => must fail) ==
  n     java.util.regex    NanoRegex
  20    79 ms              2.7 ms
  24    135 ms             2.2 ms
  28    458 ms             1.4 ms
  32    2154 ms            1.2 ms      <- n^10 growth vs. a flat line
```
(Numbers vary by machine: run it and see.)

## Features
- Literals, `.`, `*`, `+`, `?`, `{n}`, `{n,}`, `{n,m}`, `|`, groups, character classes `[a-z0-9_]`, `[^...]`, escapes `\d \w \s \D \W \S`
- `matches()` (whole input) and `contains()` (any substring)
- **Visualisation**: `toDot()` exports the automaton to Graphviz
- Clean pipeline: `RegexParser` (recursive descent) → `Node` (sealed AST) → `Nfa` (Thompson construction) → simulation

## Run
```bash
mvn -q package
java -jar target/nanoregex-1.0.0.jar                       # showcase + ReDoS race
java -jar target/nanoregex-1.0.0.jar match "(a|b)*abb" babb
java -jar target/nanoregex-1.0.0.jar dot "(a|b)*abb" | dot -Tpng -o nfa.png
# without Maven:
javac -d out src/main/java/io/github/nanoregex/*.java && java -cp out io.github.nanoregex.Main
```

## Ideas to extend it
Capture groups via Pike VM, lazy DFA construction for even faster matching,
leftmost-longest `find()` with match positions.
