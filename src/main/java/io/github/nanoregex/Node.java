package io.github.nanoregex;

import java.util.function.IntPredicate;

/** Regex syntax tree. */
sealed interface Node {
    /** A set of characters, e.g. 'a', '.', '\d' or '[a-z0-9_]'. {@code label} is used when drawing the automaton. */
    record Chars(String label, IntPredicate test) implements Node {}
    record Cat(Node left, Node right) implements Node {}
    record Alt(Node left, Node right) implements Node {}
    record Star(Node inner) implements Node {}
    record Plus(Node inner) implements Node {}
    record Maybe(Node inner) implements Node {}
    record Empty() implements Node {}
}
