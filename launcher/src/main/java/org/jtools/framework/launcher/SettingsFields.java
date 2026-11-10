package org.jtools.framework.launcher;

/*-
 * #%L
 * JToolsFramework launcher
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

import java.util.Map;

import org.jtools.framework.server.Exchange;

/**
 * The program's own settings in {@code /api/settings}, beside the framework's {@code autoStop} and {@code port}
 * ({@link WebApp#settingsFields}): DCSBuilder's chat model and marking orders.
 */
public interface SettingsFields {

    /** GET: the program's fields, put into the answer. */
    void read(Map<String, Object> answer);

    /**
     * POST: the fields the page asked to change — all of them, the framework's included: the program picks its own,
     * validates them (a {@code WebServer.Refusal} for a bad one) and keeps them.
     */
    void write(Map<String, Object> asked, Exchange exchange) throws Exception;
}
