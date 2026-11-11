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

/**
 * jtools-server: a local web application's server — the page, the routes, the pages' presence, the checks.
 *
 * Start from {@link org.jtools.framework.server.WebServer}: a route answers an
 * {@link org.jtools.framework.server.Exchange} with what it returns — a JSON tree, {@code Json.raw(...)}, an
 * {@link org.jtools.framework.server.Answer}, or null for 204 —, a refusal is a
 * {@link org.jtools.framework.server.WebServer.Refusal} answered {@code {"error": ...}}. A program built with jtools-app
 * gets its server from {@code WebApp.routes}, and registers its routes there.
 * <pre>{@code
 * server.get("/api/hello", ex -> Map.of("message", "Hello, " + ex.query("name", "world")))
 *       .post("/api/echo", ex -> ex.jsonObject())
 *       .refuse(MyDomainException.class, 400);
 * }</pre>
 */
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
