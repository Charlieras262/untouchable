package io.qzz.toshiro.untouchable.internal;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Minimal JSON with no dependencies.
 * <ul>
 *   <li>{@link #write}: canonical JSON (a subset of RFC 8785): keys sorted by UTF-16 code units, no
 *   whitespace. Only String, Integer/Long, Boolean, null, Map and List: decimal numbers are stored as
 *   text so that the serialization is exact.</li>
 *   <li>{@link #parse}: objects → LinkedHashMap, arrays → ArrayList, integers → Long.</li>
 * </ul>
 * Internal use only: not part of the public API.
 */
public final class Json {
    private static final int MAX_DEPTH = 64;

    private Json() {}

    // ---------------------------------------------------------------- writing

    public static String write(Object value) {
        StringBuilder sb = new StringBuilder();
        write(value, sb, 0);
        return sb.toString();
    }

    private static void write(Object v, StringBuilder sb, int depth) {
        if (depth > MAX_DEPTH) throw new IllegalArgumentException("JSON demasiado anidado");
        if (v == null) {
            sb.append("null");
        } else if (v instanceof String) {
            string((String) v, sb);
        } else if (v instanceof Integer || v instanceof Long) {
            sb.append(v);
        } else if (v instanceof Boolean) {
            sb.append(v);
        } else if (v instanceof Map) {
            TreeMap<String, Object> sorted = new TreeMap<String, Object>();
            for (Map.Entry<?, ?> e : ((Map<?, ?>) v).entrySet()) {
                if (!(e.getKey() instanceof String)) throw new IllegalArgumentException("Non-String key: " + e.getKey());
                sorted.put((String) e.getKey(), e.getValue());
            }
            sb.append('{');
            boolean first = true;
            for (Map.Entry<String, Object> e : sorted.entrySet()) {
                if (!first) sb.append(',');
                first = false;
                string(e.getKey(), sb);
                sb.append(':');
                write(e.getValue(), sb, depth + 1);
            }
            sb.append('}');
        } else if (v instanceof List) {
            List<?> l = (List<?>) v;
            sb.append('[');
            for (int i = 0; i < l.size(); i++) {
                if (i > 0) sb.append(',');
                write(l.get(i), sb, depth + 1);
            }
            sb.append(']');
        } else {
            throw new IllegalArgumentException("Type not supported in canonical JSON: " + v.getClass().getName());
        }
    }

    private static void string(String s, StringBuilder sb) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\b': sb.append("\\b"); break;
                case '\f': sb.append("\\f"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
            }
        }
        sb.append('"');
    }

    // ---------------------------------------------------------------- reading

    /** Parses a JSON document. Throws IllegalArgumentException if it is invalid. */
    public static Object parse(String text) {
        Parser p = new Parser(text);
        p.ws();
        Object v = p.value(0);
        p.ws();
        if (p.pos != text.length()) throw p.error("content after the end");
        return v;
    }

    private static final class Parser {
        private final String s;
        private int pos;

        Parser(String s) { this.s = s; }

        IllegalArgumentException error(String msg) {
            return new IllegalArgumentException("Invalid JSON at position " + pos + ": " + msg);
        }

        void ws() {
            while (pos < s.length()) {
                char c = s.charAt(pos);
                if (c == ' ' || c == '\t' || c == '\n' || c == '\r') pos++;
                else break;
            }
        }

        char peek() {
            if (pos >= s.length()) throw error("fin inesperado");
            return s.charAt(pos);
        }

        void expect(char c) {
            if (peek() != c) throw error("expected '" + c + "'");
            pos++;
        }

        Object value(int depth) {
            if (depth > MAX_DEPTH) throw error("demasiado anidado");
            char c = peek();
            switch (c) {
                case '{': return object(depth);
                case '[': return array(depth);
                case '"': return string();
                case 't': literal("true"); return Boolean.TRUE;
                case 'f': literal("false"); return Boolean.FALSE;
                case 'n': literal("null"); return null;
                default:
                    if (c == '-' || (c >= '0' && c <= '9')) return number();
                    throw error("valor inesperado '" + c + "'");
            }
        }

        void literal(String word) {
            if (!s.startsWith(word, pos)) throw error("expected " + word);
            pos += word.length();
        }

        Map<String, Object> object(int depth) {
            Map<String, Object> m = new LinkedHashMap<String, Object>();
            expect('{');
            ws();
            if (peek() == '}') { pos++; return m; }
            while (true) {
                ws();
                String key = string();
                ws();
                expect(':');
                ws();
                if (m.containsKey(key)) throw error("duplicate key: " + key);
                m.put(key, value(depth + 1));
                ws();
                if (peek() == ',') { pos++; continue; }
                expect('}');
                return m;
            }
        }

        List<Object> array(int depth) {
            List<Object> l = new ArrayList<Object>();
            expect('[');
            ws();
            if (peek() == ']') { pos++; return l; }
            while (true) {
                ws();
                l.add(value(depth + 1));
                ws();
                if (peek() == ',') { pos++; continue; }
                expect(']');
                return l;
            }
        }

        String string() {
            expect('"');
            StringBuilder sb = new StringBuilder();
            while (true) {
                char c = peek();
                pos++;
                if (c == '"') return sb.toString();
                if (c < 0x20) throw error("unescaped control character");
                if (c != '\\') { sb.append(c); continue; }
                char e = peek();
                pos++;
                switch (e) {
                    case '"': sb.append('"'); break;
                    case '\\': sb.append('\\'); break;
                    case '/': sb.append('/'); break;
                    case 'b': sb.append('\b'); break;
                    case 'f': sb.append('\f'); break;
                    case 'n': sb.append('\n'); break;
                    case 'r': sb.append('\r'); break;
                    case 't': sb.append('\t'); break;
                    case 'u':
                        if (pos + 4 > s.length()) throw error("escape \\u incompleto");
                        try {
                            sb.append((char) Integer.parseInt(s.substring(pos, pos + 4), 16));
                        } catch (NumberFormatException ex) {
                            throw error("invalid \\u escape");
                        }
                        pos += 4;
                        break;
                    default:
                        throw error("invalid escape \\" + e);
                }
            }
        }

        Object number() {
            int start = pos;
            if (peek() == '-') pos++;
            while (pos < s.length() && "0123456789.eE+-".indexOf(s.charAt(pos)) >= 0) pos++;
            String n = s.substring(start, pos);
            try {
                if (n.indexOf('.') < 0 && n.indexOf('e') < 0 && n.indexOf('E') < 0) return Long.parseLong(n);
                return Double.parseDouble(n);
            } catch (NumberFormatException e) {
                throw error("invalid number: " + n);
            }
        }
    }
}
