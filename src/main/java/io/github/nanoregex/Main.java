package io.github.nanoregex;

import java.io.PrintStream;
import java.util.regex.Pattern;

/**
 * Usage:
 *   Main                       run the showcase
 *   Main match REGEX TEXT      full match test
 *   Main dot REGEX             print the automaton as Graphviz DOT
 *   Main redos [N]             race against java.util.regex on a pathological pattern
 */
public final class Main {
    public static void main(String[] args) {
        System.setOut(new PrintStream(System.out, true, java.nio.charset.StandardCharsets.UTF_8)); // for ε and ✓ in the DOT output
        if (args.length >= 3 && args[0].equals("match")) {
            System.out.println(NanoRegex.compile(args[1]).matches(args[2]));
        } else if (args.length >= 2 && args[0].equals("dot")) {
            System.out.print(NanoRegex.compile(args[1]).toDot());
        } else if (args.length >= 1 && args[0].equals("redos")) {
            redos(args.length > 1 ? Integer.parseInt(args[1]) : 32);
        } else {
            showcase();
            redos(32);
        }
    }

    private static void showcase() {
        String[][] cases = {
                {"(a|b)*abb", "babaabb"},
                {"\\d+(\\.\\d+)?", "3.14159"},
                {"[a-z]+@[a-z]+\\.(com|org|it)", "valerio@example.it"},
                {"[^aeiou ]+", "rhythm"},
                {"colou?r", "colour"},
                {"(ab)+", "ababa"},
        };
        System.out.println("== Matching ==");
        for (String[] c : cases) {
            System.out.printf("  %-32s %-22s -> %b%n", c[0], c[1], NanoRegex.compile(c[0]).matches(c[1]));
        }
        System.out.println("  contains('b+c', 'aaabbbcd') -> " + NanoRegex.compile("b+c").contains("aaabbbcd"));

        System.out.println("\n== Automaton for (a|b)*abb (Graphviz DOT) ==");
        System.out.print(NanoRegex.compile("(a|b)*abb").toDot());
        System.out.println();
    }

    /**
     * Pattern (.*a){10}b against a string of a's that never contains a 'b'.
     * A backtracking engine has to try every way of splitting the input between the ten ".*a" groups:
     * roughly n^10 combinations. NanoRegex just walks the input once.
     */
    private static void redos(int maxN) {
        String pattern = "(.*a){10}b";
        NanoRegex nano = NanoRegex.compile(pattern);
        Pattern jdk = Pattern.compile(pattern);
        System.out.println("== Backtracking race: pattern " + pattern + " on 'aaa...a' (no 'b' => must fail) ==");
        System.out.printf("  %-5s %-18s %-18s%n", "n", "java.util.regex", "NanoRegex");
        for (int n = Math.max(16, maxN - 12); n <= maxN; n += 4) {
            String input = "a".repeat(n);
            long t0 = System.nanoTime();
            jdk.matcher(input).matches();
            long jdkMs = (System.nanoTime() - t0) / 1_000_000;
            t0 = System.nanoTime();
            nano.matches(input);
            double nanoMs = (System.nanoTime() - t0) / 1_000_000.0;
            System.out.printf("  %-5d %-18s %-18s%n", n, jdkMs + " ms", "%.3f ms".formatted(nanoMs));
        }
        long t0 = System.nanoTime();
        boolean r = nano.matches("a".repeat(100_000));
        System.out.printf("  NanoRegex on 100,000 characters: %b in %d ms%n", r, (System.nanoTime() - t0) / 1_000_000);
    }
}
