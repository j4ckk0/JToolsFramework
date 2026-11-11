package org.jtools.__APP_ID__.core;

/**
 * __APP_NAME__: the one entry point to its business, for the server as for any other Java program. Every rule, every
 * computation and every state lives behind it — the page only shows what this class answers and sends what the
 * user asks; it decides nothing.
 */
public final class __APP_NAME__ {

    /** The application's name, as the page and the console show it. */
    public static final String NAME = "__APP_NAME__";

    /** What the business says of its state: the first answer of its own, before the real ones replace it. */
    public record Status(String summary) {}

    public Status status() {
        return new Status(NAME + " is ready.");
    }
}
