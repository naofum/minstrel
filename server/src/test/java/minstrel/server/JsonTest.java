package minstrel.server;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class JsonTest {

    @Test
    void parseSimpleObject() {
        Object o = Json.parse("{\"a\":\"b\",\"n\":42,\"f\":1.5,\"t\":true,\"x\":null}");
        Map<?, ?> m = (Map<?, ?>) o;
        assertEquals("b", m.get("a"));
        assertEquals(42L, m.get("n"));
        assertEquals(1.5d, m.get("f"));
        assertEquals(Boolean.TRUE, m.get("t"));
        assertNull(m.get("x"));
    }

    @Test
    void parseNested() {
        Object o = Json.parse("{\"obj\":{\"arr\":[1,\"two\",false,null]}}");
        Map<?, ?> obj = (Map<?, ?>) ((Map<?, ?>) o).get("obj");
        List<?> arr = (List<?>) obj.get("arr");
        assertEquals(1L, arr.get(0));
        assertEquals("two", arr.get(1));
        assertEquals(Boolean.FALSE, arr.get(2));
        assertNull(arr.get(3));
    }

    @Test
    void parseUnicodeAndEscapes() {
        Object o = Json.parse("\"\\u0041\\n\\\"\\\\\\/\\b\\f\\r\\t\"");
        assertEquals("A\n\"\\/\b\f\r\t", o);
    }

    @Test
    void stringifyRoundTrip() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", "テスト");
        m.put("num", 3L);
        m.put("flag", Boolean.TRUE);
        m.put("nul", null);
        m.put("list", Arrays.asList("x", 5L));
        Object parsed = Json.parse(Json.stringify(m));
        Map<?, ?> back = (Map<?, ?>) parsed;
        assertEquals("テスト", back.get("name"));
        assertEquals(3L, back.get("num"));
        assertEquals(Boolean.TRUE, back.get("flag"));
        assertNull(back.get("nul"));
        assertEquals(Arrays.asList("x", 5L), back.get("list"));
    }

    @Test
    void stringifyQuotesAndSpecials() {
        assertEquals("\"a\\\"b\"", Json.stringify("a\"b"));
        assertEquals("\"line\\nbreak\"", Json.stringify("line\nbreak"));
        assertEquals("\"back\\\\slash\"", Json.stringify("back\\slash"));
    }

    @Test
    void parseInvalidThrows() {
        assertThrows(IllegalArgumentException.class, () -> Json.parse("{invalid"));
        assertThrows(IllegalArgumentException.class, () -> Json.parse("{\"a\":}"));
        assertThrows(IllegalArgumentException.class, () -> Json.parse("[1,2"));
    }

    @Test
    void parseEmptyAndWhitespace() {
        assertNull(Json.parse("   "));
        Map<?, ?> m = (Map<?, ?>) Json.parse(" { } ");
        assertTrue(m.isEmpty());
    }

    @Test
    void parseNegativeAndExponentNumbers() {
        assertEquals(-7L, Json.parse("-7"));
        assertEquals(1.0e3, (Double) Json.parse("1e3"), 0.0001);
    }
}