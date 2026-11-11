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

/**
 * jtools-support: what every module leans on — JSON, texts in each language, the installation. The JDK and nothing else.
 *
 * <ul>
 *   <li>{@link org.jtools.framework.support.Json}: a {@code Map} / {@code List} / {@code String} / {@code Number} / {@code Boolean}
 *       tree to text and back; {@link org.jtools.framework.support.Json#raw} for JSON another writer already wrote;</li>
 *   <li>{@link org.jtools.framework.support.Texts} and {@link org.jtools.framework.support.Catalogue}: a program's texts in
 *       each of its languages, {@code {0}} placeholders, plurals, an {@code Accept-Language} header honoured;</li>
 *   <li>{@link org.jtools.framework.support.Installation}: where the program's jar is, its version, its {@code .ini}.</li>
 * </ul>
 * <pre>{@code
 * Catalogue catalogue = Catalogue.load(MyApp.class, "i18n.messages", Locale.ENGLISH, Locale.FRENCH);
 * String said = catalogue.of("fr-CA,fr;q=0.9").t("greeting", "Ada");   // i18n/messages_fr.properties: greeting=Bonjour {0}
 * String json = Json.write(Map.of("said", said));
 * }</pre>
 */
package org.jtools.framework.support;
