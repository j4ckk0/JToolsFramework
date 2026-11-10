package org.jtools.framework.support;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class JsonTest {

    @Test
    void writesWhatItParses() {
        String text = "{\"name\":\"a \\\"quoted\\\" é\",\"count\":3,\"ratio\":0.5,\"on\":true,\"none\":null,\"list\":[1,\"two\"]}";
        assertEquals(text, Json.write(Json.parse(text)));
    }

    @Test
    void keepsTheOrderOfTheKeys() {
        Map<String, Object> out = Json.obj();
        out.put("z", 1);
        out.put("a", List.of());
        assertEquals("{\"z\":1,\"a\":[]}", Json.write(out));
    }

    @Test
    void refusesWhatIsNotJson() {
        assertThrows(IllegalArgumentException.class, () -> Json.parse("{\"a\":1"));
        assertThrows(IllegalArgumentException.class, () -> Json.parse("[1] trailing"));
    }
}
