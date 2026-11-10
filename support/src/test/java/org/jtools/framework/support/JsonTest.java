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
