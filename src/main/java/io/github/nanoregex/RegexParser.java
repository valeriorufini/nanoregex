package io.github.nanoregex;

import io.github.nanoregex.Node.*;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntPredicate;

/**
 * Recursive-descent parser.
 * <pre>
 *   alt    := cat ('|' cat)*
 *   cat    := repeat*
 *   repeat := atom ('*' | '+' | '?' | '{n}' | '{n,}' | '{n,m}')*
 *   atom   := '(' alt ')' | '[' '^'? class ']' | '.' | '\' escape | literal
 * </pre>
 */
final class RegexParser {
    private final String src;
    private int pos;

    private RegexParser(String src) {
        this.src = src;
    }

    static Node parse(String regex) {
        RegexParser p = new RegexParser(regex);
        Node n = p.alt();
        if (p.pos < regex.length()) {
            throw new RegexSyntaxException("Unmatched ')'", p.pos);
        }
        return n;
    }

    private Node alt() {
        Node left = cat();
        while (peek() == '|') {
            pos++;
            left = new Alt(left, cat());
        }
        return left;
    }

    private Node cat() {
        Node result = null;
        while (pos < src.length() && peek() != '|' && peek() != ')') {
            Node next = repeat();
            result = result == null ? next : new Cat(result, next);
        }
        return result == null ? new Empty() : result;
    }

    private Node repeat() {
        Node atom = atom();
        while (pos < src.length()) {
            char c = peek();
            if (c == '*') atom = new Star(atom);
            else if (c == '+') atom = new Plus(atom);
            else if (c == '?') atom = new Maybe(atom);
            else if (c == '{') { atom = counted(atom); continue; }
            else break;
            pos++;
        }
        return atom;
    }

    /** Counted repetition: {n}, {n,} and {n,m}. Expanded into plain concatenation, so the engine itself stays tiny. */
    private Node counted(Node atom) {
        int start = pos++;                                   // '{'
        int min = number();
        int max = min;
        if (peek() == ',') {
            pos++;
            max = peek() == '}' ? -1 : number();
        }
        if (peek() != '}') throw new RegexSyntaxException("Malformed repetition", start);
        pos++;
        if (max != -1 && max < min) throw new RegexSyntaxException("Bad repetition range", start);
        if (min > 1000 || max > 1000) throw new RegexSyntaxException("Repetition count too large (max 1000)", start);
        Node result = new Empty();
        for (int i = 0; i < min; i++) result = seq(result, atom);
        if (max == -1) {
            result = seq(result, new Star(atom));
        } else {
            for (int i = min; i < max; i++) result = seq(result, new Maybe(atom));
        }
        return result;
    }

    private static Node seq(Node left, Node right) {
        return left instanceof Empty ? right : new Cat(left, right);
    }

    private int number() {
        int start = pos;
        while (pos < src.length() && Character.isDigit(peek())) pos++;
        if (start == pos) throw new RegexSyntaxException("Number expected", pos);
        return Integer.parseInt(src.substring(start, pos));
    }

    private Node atom() {
        char c = src.charAt(pos);
        switch (c) {
            case '(': {
                pos++;
                Node inner = alt();
                if (peek() != ')') throw new RegexSyntaxException("Missing ')'", pos);
                pos++;
                return inner;
            }
            case '[': return charClass();
            case '.': pos++; return new Chars(".", ch -> ch != '\n');
            case '\\': return escape();
            case '*': case '+': case '?':
                throw new RegexSyntaxException("Nothing to repeat", pos);
            default:
                pos++;
                return literal(c);
        }
    }

    private Node escape() {
        pos++; // backslash
        if (pos >= src.length()) throw new RegexSyntaxException("Dangling backslash", pos);
        char c = src.charAt(pos++);
        IntPredicate p = classEscape(c);
        if (p != null) return new Chars("\\" + c, p);
        return literal(unescape(c));
    }

    private Node charClass() {
        int start = pos++;                                   // '['
        boolean negate = false;
        if (peek() == '^') { negate = true; pos++; }
        List<IntPredicate> parts = new ArrayList<>();
        boolean first = true;
        while (pos < src.length() && (peek() != ']' || first)) {
            first = false;
            char c = src.charAt(pos++);
            if (c == '\\') {
                if (pos >= src.length()) throw new RegexSyntaxException("Dangling backslash", pos);
                char e = src.charAt(pos++);
                IntPredicate p = classEscape(e);
                if (p != null) { parts.add(p); continue; }
                c = unescape(e);
            }
            if (peek() == '-' && pos + 1 < src.length() && src.charAt(pos + 1) != ']') {
                pos++;                                       // '-'
                char hi = src.charAt(pos++);
                if (hi == '\\' && pos < src.length()) hi = unescape(src.charAt(pos++));
                if (hi < c) throw new RegexSyntaxException("Bad range " + c + "-" + hi, pos);
                final char lo = c, up = hi;
                parts.add(ch -> ch >= lo && ch <= up);
            } else {
                final char single = c;
                parts.add(ch -> ch == single);
            }
        }
        if (pos >= src.length()) throw new RegexSyntaxException("Missing ']'", start);
        pos++;                                               // ']'
        final boolean neg = negate;
        IntPredicate test = ch -> parts.stream().anyMatch(p -> p.test(ch)) != neg;
        return new Chars(src.substring(start, pos), test);
    }

    private static IntPredicate classEscape(char c) {
        return switch (c) {
            case 'd' -> ch -> ch >= '0' && ch <= '9';
            case 'D' -> ch -> !(ch >= '0' && ch <= '9');
            case 'w' -> ch -> Character.isLetterOrDigit(ch) || ch == '_';
            case 'W' -> ch -> !(Character.isLetterOrDigit(ch) || ch == '_');
            case 's' -> Character::isWhitespace;
            case 'S' -> ch -> !Character.isWhitespace(ch);
            default -> null;
        };
    }

    private static char unescape(char c) {
        return switch (c) { case 'n' -> '\n'; case 't' -> '\t'; case 'r' -> '\r'; default -> c; };
    }

    private static Node literal(char c) {
        return new Chars(c == '\n' ? "\\n" : String.valueOf(c), ch -> ch == c);
    }

    private char peek() {
        return pos < src.length() ? src.charAt(pos) : '\0';
    }
}
