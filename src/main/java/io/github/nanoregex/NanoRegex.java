package io.github.nanoregex;

/**
 * Public API.
 * <pre>
 *   NanoRegex r = NanoRegex.compile("(a|b)*abb");
 *   r.matches("babb");     // true  (whole input)
 *   r.contains("xxabbxx"); // true  (any substring)
 *   r.toDot();             // Graphviz drawing of the automaton
 * </pre>
 * Supported: literals, {@code .}, {@code * + ?}, {@code |}, groups {@code ( )}, classes {@code [a-z0-9_] [^"]},
 * escapes {@code \d \w \s \D \W \S \n \t} and escaped metacharacters. No backreferences by design
 * (they are what makes backtracking engines exponential).
 */
public final class NanoRegex {
    private final String pattern;
    private final Nfa nfa;

    private NanoRegex(String pattern, Nfa nfa) {
        this.pattern = pattern;
        this.nfa = nfa;
    }

    public static NanoRegex compile(String pattern) {
        return new NanoRegex(pattern, Nfa.compile(RegexParser.parse(pattern)));
    }

    public boolean matches(String input) { return nfa.matches(input); }

    public boolean contains(String input) { return nfa.contains(input); }

    public String toDot() { return nfa.toDot(); }

    public String pattern() { return pattern; }
}
