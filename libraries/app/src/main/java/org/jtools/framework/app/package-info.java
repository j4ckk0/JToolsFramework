/*-
 * #%L
 * JToolsFramework app
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
 * jtools-app: the program around the server — the one dependency a program needs (it brings jtools-server and jtools-support).
 *
 * Start from {@link org.jtools.framework.app.WebApp}: the command line ({@code --port}, {@code --no-browser},
 * {@code --log-level}, {@code --lang}, {@code --help}…), the installation's {@code <id>.ini}, the Settings menu's port
 * and automatic stop ({@link org.jtools.framework.app.UserSettings}, a program's own fields through
 * {@link org.jtools.framework.app.SettingsFields}), the log, the browser, {@code /api/settings}, {@code /api/about},
 * {@code /api/quit}, {@code /api/log/open}, and the stop once the last page has closed.
 * <pre>{@code
 * public static void main(String[] args) {
 *     WebApp.named("Hello").defaultPort(8089).pageRoot("web")
 *           .endpoints(server -> server.get("/api/hello", ex -> Map.of("message", "Hello")))
 *           .launch(args);
 * }
 * }</pre>
 */
package org.jtools.framework.app;

/*-
 * #%L
 * JToolsFramework app
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
