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

import java.util.Locale;

import org.jtools.framework.support.Catalogue;
import org.jtools.framework.support.Texts;

/** jtools-app's own texts: the command line's usage and errors, the console's messages, the log's failures. */
final class AppTexts {

    static final Catalogue CATALOGUE = Catalogue.load(AppTexts.class, "org.jtools.framework.app.texts", Locale.ENGLISH, Locale.FRENCH);
    /** What the log says of a failure: English, whoever runs the program (a log is read by whoever helps, and searched). */
    static final Texts ENGLISH = CATALOGUE.of(Locale.ENGLISH);

    private AppTexts() {
    }
}
