package br.com.liberdadefamilia.shared;

/** Regular expressions for {@code @Pattern} on the text the API receives. */
public final class TextPatterns {

    /**
     * Any text without U+0000, which PostgreSQL text cannot store. A negated class also matches line breaks, which
     * "." would not.
     */
    public static final String WITHOUT_NUL = "[^\\x00]*";

    private TextPatterns() {}
}
