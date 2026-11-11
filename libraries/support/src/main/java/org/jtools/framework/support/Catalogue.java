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

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A program's texts in each of its languages: {@code <bundle>.properties} for the first language, the default, and
 * {@code <bundle>_<lang>.properties} for each other, in UTF-8, beside the classes of the anchor class. A key missing
 * from a language falls back on the default's. Read by hand rather than through {@code ResourceBundle}, whose
 * fallback to the machine's language would answer an English page in French on a French computer.
 */
public final class Catalogue {

    private static final String EXTENSION = ".properties", SEPARATOR = "_";

    private final ClassLoader loader;
    private final String path;
    private final List<Locale> languages;
    private final Map<String, Texts> byLanguage = new ConcurrentHashMap<>();
    private volatile Properties defaults;

    /**
     * A program's texts: {@code <bundle>.properties} beside the anchor's classes for the first language, the default,
     * {@code <bundle>_<lang>.properties} for the others ({@code Catalogue.load(App.class, "i18n.messages", ENGLISH, FRENCH)}).
     */
    public static Catalogue load(Class<?> anchor, String bundle, Locale... languages) {
        return new Catalogue(anchor, bundle, List.of(languages));
    }

    private Catalogue(Class<?> anchor, String bundle, List<Locale> languages) {
        if (languages.isEmpty()) throw new IllegalArgumentException("a catalogue needs at least one language");
        this.loader = anchor.getClassLoader();
        this.path = bundle.replace('.', '/');
        this.languages = List.copyOf(languages);
    }

    /** The languages, the default first. */
    public List<Locale> languages() {
        return languages;
    }

    public Locale defaultLanguage() {
        return languages.get(0);
    }

    /** The texts in that language, or in the default one when the catalogue does not have it. */
    public Texts of(Locale locale) {
        Locale language = supported(locale == null ? null : locale.getLanguage());
        return byLanguage.computeIfAbsent(language.getLanguage(), lang -> new Texts(language, texts(language)));
    }

    /**
     * The texts in the language an {@code Accept-Language} header prefers among the catalogue's: its q-values
     * honoured, a region ignored ({@code fr-CA} is {@code fr}); the default when it names none of them, or is absent.
     */
    public Texts of(String acceptLanguage) {
        return of(preferred(acceptLanguage));
    }

    /** The catalogue's language an {@code Accept-Language} header prefers; the default when it names none. */
    public Locale preferred(String acceptLanguage) {
        if (acceptLanguage == null || acceptLanguage.isBlank()) return defaultLanguage();
        record Preference(String language, double q) {}
        List<Preference> preferences = new ArrayList<>();
        for (String part : acceptLanguage.split(",")) {
            String[] fields = part.trim().split(";");
            double q = 1;
            for (int i = 1; i < fields.length; i++) {
                String field = fields[i].trim();
                if (field.startsWith("q=")) {
                    try { q = Double.parseDouble(field.substring(2)); } catch (NumberFormatException e) { q = 0; }
                }
            }
            if (!fields[0].isBlank() && q > 0) preferences.add(new Preference(fields[0].trim(), q));
        }
        preferences.sort((a, b) -> Double.compare(b.q(), a.q()));   // stable: equal q-values keep their order
        for (Preference preference : preferences) {
            String language = preference.language().toLowerCase(Locale.ROOT).split("[-_]")[0];
            for (Locale supported : languages) if (supported.getLanguage().equals(language)) return supported;
        }
        return defaultLanguage();
    }

    private Locale supported(String language) {
        if (language != null) for (Locale supported : languages) if (supported.getLanguage().equals(language)) return supported;
        return defaultLanguage();
    }

    /** The language's texts over the default's, so that a key missing from it falls back on the default. */
    private Properties texts(Locale language) {
        Properties base = defaults();
        if (language.equals(defaultLanguage())) return base;
        Properties texts = new Properties(base);
        load(texts, path + SEPARATOR + language.getLanguage() + EXTENSION);
        return texts;
    }

    private Properties defaults() {
        Properties base = defaults;
        if (base == null) {
            base = new Properties();
            if (!load(base, path + EXTENSION)) throw new IllegalStateException("no " + path + EXTENSION + " beside " + loader);
            defaults = base;
        }
        return base;
    }

    private boolean load(Properties into, String resource) {
        try (InputStream in = loader.getResourceAsStream(resource)) {
            if (in == null) return false;
            into.load(new InputStreamReader(in, StandardCharsets.UTF_8));
            return true;
        } catch (IOException e) {
            throw new UncheckedIOException(resource, e);
        }
    }
}
