package io.github.nanoregex;

public final class RegexSyntaxException extends RuntimeException {
    public RegexSyntaxException(String message, int position) {
        super(message + " (at position " + position + ")");
    }
}
