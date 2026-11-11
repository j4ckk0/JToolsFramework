/*-
 * #%L
 * JToolsFramework desktop
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
 * jtools-desktop: the native file dialogs a local program opens on its own desktop.
 *
 * {@link org.jtools.framework.desktop.FileDialogs}: open, save and folder dialogs — kdialog or zenity on Linux, the system's
 * on Windows and macOS, Swing's otherwise —, {@code available()} false when the program runs without a display (a route
 * then answers 409 and the page offers the browser's own). A module of its own: choosing files is a feature a program
 * opts into.
 * <pre>{@code
 * List<Path> chosen = FileDialogs.chooseFilesToOpen("Open a book list", true, new FileDialogs.Filter("Book lists", List.of("*.csv")));
 * }</pre>
 */
package org.jtools.framework.desktop;

/*-
 * #%L
 * JToolsFramework desktop
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
