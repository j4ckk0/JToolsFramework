package org.jtools.framework.support;

/*-
 * #%L
 * JToolsFramework support
 * %%
 * Copyright (C) 2026 jtools.org
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal JSON: writes and parses Map / List / String / Number / Boolean / null trees. No dependency. A {@link Raw} in a
 * tree is JSON another writer already wrote: it is written as it is.
 */
public final class Json {

    /** JSON already written — by a library's own writer — sent as it is, alone or inside a tree. Not checked. */
    public record Raw(String json) {}

    /** JSON already written, to be sent as it is: {@code Json.raw(graph.toJson())}. */
    public static Raw raw(String json) { return new Raw(json); }

    /** What a malformed text is told: where it stopped, and what was expected there. */
    private static final String JSON_ERROR = "JSON: ", AT = " at ", TRAILING = "trailing characters", UNEXPECTED_END = "unexpected end",
            QUOTE = "'", EXPECTED = " expected", OBJECT_SEPARATOR_EXPECTED = "',' or '}' expected", ARRAY_SEPARATOR_EXPECTED = "',' or ']' expected",
            STRING_EXPECTED = "string expected", BAD_UNICODE_ESCAPE = "bad \\u escape", BAD_ESCAPE = "bad escape", VALUE_EXPECTED = "value expected",
            BAD_NUMBER = "bad number ";

    private Json() {}

    // ---- writing ----------------------------------------------------------------------------

    public static String write(Object value) {
        StringBuilder sb = new StringBuilder(1024);
        write(sb, value);
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    public static void write(StringBuilder sb, Object v) {
        if (v == null) sb.append("null");
        else if (v instanceof Raw raw) sb.append(raw.json());
        else if (v instanceof String s) string(sb, s);
        else if (v instanceof Boolean || v instanceof Integer || v instanceof Long) sb.append(v);
        else if (v instanceof Number n) {
            double d = n.doubleValue();
            if (d == Math.rint(d) && !Double.isInfinite(d)) sb.append((long) d); else sb.append(d);
        } else if (v instanceof Map<?, ?> m) {
            sb.append('{');
            boolean first = true;
            for (Map.Entry<String, Object> e : ((Map<String, Object>) m).entrySet()) {
                if (!first) sb.append(',');
                first = false;
                string(sb, e.getKey());
                sb.append(':');
                write(sb, e.getValue());
            }
            sb.append('}');
        } else if (v instanceof Iterable<?> it) {
            sb.append('[');
            boolean first = true;
            for (Object o : it) {
                if (!first) sb.append(',');
                first = false;
                write(sb, o);
            }
            sb.append(']');
        } else string(sb, v.toString());
    }

    public static String string(String s) {
        StringBuilder sb = new StringBuilder(s.length() + 2);
        string(sb, s);
        return sb.toString();
    }

    public static void string(StringBuilder sb, String s) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c)); else sb.append(c);
                }
            }
        }
        sb.append('"');
    }

    /** Convenience: an insertion-ordered map. */
    public static Map<String, Object> obj() { return new LinkedHashMap<>(); }

    // ---- parsing ----------------------------------------------------------------------------

    public static Object parse(String text) {
        Parser p = new Parser(text);
        Object v = p.value();
        p.ws();
        if (p.i != text.length()) throw p.error(TRAILING);
        return v;
    }

    private static final class Parser {
        private final String s;
        private int i;

        Parser(String s) { this.s = s; }

        IllegalArgumentException error(String msg) {
            return new IllegalArgumentException(JSON_ERROR + msg + AT + i);
        }

        void ws() { while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++; }

        char peek() { if (i >= s.length()) throw error(UNEXPECTED_END); return s.charAt(i); }

        void expect(char c) { ws(); if (peek() != c) throw error(QUOTE + c + QUOTE + EXPECTED); i++; }

        Object value() {
            ws();
            char c = peek();
            switch (c) {
                case '{' -> { return object(); }
                case '[' -> { return array(); }
                case '"' -> { return string(); }
                case 't' -> { literal("true"); return Boolean.TRUE; }
                case 'f' -> { literal("false"); return Boolean.FALSE; }
                case 'n' -> { literal("null"); return null; }
                default -> { return number(); }
            }
        }

        void literal(String lit) {
            if (!s.startsWith(lit, i)) throw error(lit + EXPECTED);
            i += lit.length();
        }

        Map<String, Object> object() {
            Map<String, Object> m = new LinkedHashMap<>();
            expect('{');
            ws();
            if (peek() == '}') { i++; return m; }
            for (;;) {
                ws();
                String k = string();
                expect(':');
                m.put(k, value());
                ws();
                char c = peek();
                i++;
                if (c == '}') return m;
                if (c != ',') throw error(OBJECT_SEPARATOR_EXPECTED);
            }
        }

        List<Object> array() {
            List<Object> l = new ArrayList<>();
            expect('[');
            ws();
            if (peek() == ']') { i++; return l; }
            for (;;) {
                l.add(value());
                ws();
                char c = peek();
                i++;
                if (c == ']') return l;
                if (c != ',') throw error(ARRAY_SEPARATOR_EXPECTED);
            }
        }

        String string() {
            if (peek() != '"') throw error(STRING_EXPECTED);
            i++;
            StringBuilder sb = new StringBuilder();
            for (;;) {
                char c = peek();
                i++;
                if (c == '"') return sb.toString();
                if (c != '\\') { sb.append(c); continue; }
                char e = peek();
                i++;
                switch (e) {
                    case '"', '\\', '/' -> sb.append(e);
                    case 'n' -> sb.append('\n');
                    case 'r' -> sb.append('\r');
                    case 't' -> sb.append('\t');
                    case 'b' -> sb.append('\b');
                    case 'f' -> sb.append('\f');
                    case 'u' -> {
                        if (i + 4 > s.length()) throw error(BAD_UNICODE_ESCAPE);
                        sb.append((char) Integer.parseInt(s.substring(i, i + 4), 16));
                        i += 4;
                    }
                    default -> throw error(BAD_ESCAPE);
                }
            }
        }

        Number number() {
            int start = i;
            while (i < s.length() && "+-0123456789.eE".indexOf(s.charAt(i)) >= 0) i++;
            String t = s.substring(start, i);
            if (t.isEmpty()) throw error(VALUE_EXPECTED);
            try {
                if (t.matches("-?\\d+")) return Long.parseLong(t);
                return Double.parseDouble(t);
            } catch (NumberFormatException ex) {
                throw error(BAD_NUMBER + t);
            }
        }
    }
}
