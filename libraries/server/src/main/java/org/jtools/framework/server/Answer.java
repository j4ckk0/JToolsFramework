package org.jtools.framework.server;

/*-
 * #%L
 * JToolsFramework server
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

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import org.jtools.framework.support.Json;

/**
 * An endpoint's answer when it is not a JSON tree sent with 200 (what an endpoint returns otherwise, {@link WebServer.Endpoint}):
 * another status, bytes of another type (a zip, an XML document), headers of its own.
 */
public record Answer(int status, String contentType, byte[] body, Map<String, String> headers) {

    public Answer {
        headers = Map.copyOf(headers);
    }

    /** A JSON tree (or {@link Json#raw}) with a status of its own. */
    public static Answer json(int status, Object tree) {
        return new Answer(status, Http.TYPE_JSON, Json.write(tree).getBytes(StandardCharsets.UTF_8), Map.of());
    }

    /** Bytes of a type: {@code Answer.bytes("application/zip", zip)}. */
    public static Answer bytes(String contentType, byte[] body) {
        return new Answer(200, contentType, body, Map.of());
    }

    /** An XML document, in UTF-8. */
    public static Answer xml(String xml) {
        return new Answer(200, Http.TYPE_XML, xml.getBytes(StandardCharsets.UTF_8), Map.of());
    }

    /** Plain text, in UTF-8. */
    public static Answer text(int status, String text) {
        return new Answer(status, Http.TYPE_TEXT, text.getBytes(StandardCharsets.UTF_8), Map.of());
    }

    /** The same answer with one more header: {@code .withHeader("Content-Disposition", "attachment; filename=w.zip")}. */
    public Answer withHeader(String name, String value) {
        Map<String, String> more = new LinkedHashMap<>(headers);
        more.put(name, value);
        return new Answer(status, contentType, body, more);
    }
}
