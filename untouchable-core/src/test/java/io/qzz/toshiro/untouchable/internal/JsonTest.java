package io.qzz.toshiro.untouchable.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class JsonTest {

    @Test
    void writesCanonicalJsonWithSortedKeys() {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("b", 2L);
        m.put("a", Arrays.asList("x", Boolean.TRUE, null));
        m.put("é", "ü\"\\\n\u0001");
        assertEquals("{\"a\":[\"x\",true,null],\"b\":2,\"é\":\"ü\\\"\\\\\\n\\u0001\"}", Json.write(m));
    }

    @Test
    void roundTripsThroughParse() {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("n", 42L);
        m.put("s", "line\ttab €");
        List<Object> l = new ArrayList<Object>();
        l.add(new LinkedHashMap<String, Object>());
        l.add(Boolean.FALSE);
        m.put("l", l);
        String json = Json.write(m);
        assertEquals(json, Json.write(Json.parse(json)));
    }

    @Test
    void rejectsInvalidJson() {
        assertThrows(IllegalArgumentException.class, () -> Json.parse("{\"a\":1,}"));
        assertThrows(IllegalArgumentException.class, () -> Json.parse("{\"a\":1,\"a\":2}"));
        assertThrows(IllegalArgumentException.class, () -> Json.parse("[1] x"));
        assertThrows(IllegalArgumentException.class, () -> Json.parse("\"unterminated"));
    }

    @Test
    void rejectsUnsupportedTypesWhenWriting() {
        assertThrows(IllegalArgumentException.class, () -> Json.write(1.5d));
    }
}
