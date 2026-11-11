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

import java.util.Locale;
import java.util.Properties;

/**
 * A program's texts in one language ({@link Catalogue#of}). {@code {0}}, {@code {1}}… are replaced by the arguments as
 * they are — not {@code MessageFormat}, whose quoting would make every French apostrophe a trap ({@code l''arbre}).
 * A key the catalogue does not have is answered as itself, so that a missing text shows, rather than breaks.
 */
public final class Texts {

    private static final String ONE = ".one", OTHER = ".other";

    private final Locale locale;
    private final Properties texts;

    Texts(Locale locale, Properties texts) {
        this.locale = locale;
        this.texts = texts;
    }

    public Locale locale() {
        return locale;
    }

    /** The language's code: {@code en}, {@code fr}. */
    public String lang() {
        return locale.getLanguage();
    }

    public boolean has(String key) {
        return texts.getProperty(key) != null;
    }

    /** The text of a key, its {@code {0}}, {@code {1}}… replaced by the arguments; the key itself when there is none. */
    public String t(String key, Object... args) {
        String text = texts.getProperty(key);
        if (text == null) return key;
        for (int i = 0; i < args.length; i++) text = text.replace("{" + i + "}", String.valueOf(args[i]));
        return text;
    }

    /**
     * The text of a count: {@code key.one} or {@code key.other} by the language's rule (French says one for 0 and 1),
     * the count as {@code {0}} and the arguments after it.
     */
    public String tn(String key, int n, Object... args) {
        Object[] all = new Object[args.length + 1];
        all[0] = n;
        System.arraycopy(args, 0, all, 1, args.length);
        return t(key + (isOne(n) ? ONE : OTHER), all);
    }

    private boolean isOne(int n) {
        return switch (lang()) {
            case "fr", "pt" -> n == 0 || n == 1;
            default -> n == 1;
        };
    }
}
