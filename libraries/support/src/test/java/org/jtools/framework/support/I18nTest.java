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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Locale;

import org.junit.jupiter.api.Test;

class I18nTest {

    private final Catalogue texts = I18n.catalogue(I18nTest.class, "org.jtools.framework.support.test-texts", Locale.ENGLISH, Locale.FRENCH);

    @Test
    void replacesTheArgumentsAsTheyAre() {
        assertEquals("Hello Ann, from Bob", texts.of(Locale.ENGLISH).t("greeting", "Ann", "Bob"));
        assertEquals("Bonjour Ann, de la part de Bob", texts.of(Locale.FRENCH).t("greeting", "Ann", "Bob"));
        assertEquals("la racine de l'arbre : x", texts.of(Locale.FRENCH).t("apostrophe", "x"), "no MessageFormat quoting");
    }

    @Test
    void fallsBackOnTheDefaultLanguageThenOnTheKey() {
        I18n french = texts.of(Locale.FRENCH);
        assertEquals("only in English", french.t("only.english"));
        assertEquals("no.such.key", french.t("no.such.key"));
        assertFalse(french.has("no.such.key"));
        assertEquals(Locale.ENGLISH, texts.of(Locale.GERMAN).locale(), "a language it has not: the default");
    }

    @Test
    void neverFollowsTheMachinesLanguage() {
        Locale machine = Locale.getDefault();
        try {
            Locale.setDefault(Locale.FRENCH);
            assertEquals("Hello Ann, from Bob", texts.of(Locale.ENGLISH).t("greeting", "Ann", "Bob"));
        } finally {
            Locale.setDefault(machine);
        }
    }

    @Test
    void countsByEachLanguagesRule() {
        assertEquals("1 file", texts.of(Locale.ENGLISH).tn("file", 1));
        assertEquals("0 files", texts.of(Locale.ENGLISH).tn("file", 0));
        assertEquals("0 fichier", texts.of(Locale.FRENCH).tn("file", 0), "French says one for 0");
        assertEquals("2 fichiers", texts.of(Locale.FRENCH).tn("file", 2));
    }

    @Test
    void readsTheAcceptLanguageHeader() {
        assertEquals(Locale.FRENCH, texts.preferred("fr-CA,fr;q=0.9,en;q=0.8"));
        assertEquals(Locale.ENGLISH, texts.preferred("de-DE,en;q=0.5,fr;q=0.3"), "the q-values decide");
        assertEquals(Locale.FRENCH, texts.preferred("de, fr;q=0.1"));
        assertEquals(Locale.ENGLISH, texts.preferred("de"), "none of its languages: the default");
        assertEquals(Locale.ENGLISH, texts.preferred(null));
        assertEquals(Locale.ENGLISH, texts.preferred("fr;q=0"), "q=0 means not this one");
        assertSame(texts.of("fr"), texts.of(Locale.FRENCH), "one I18n per language");
    }

    @Test
    void saysWhatAnExceptionSays() {
        assertEquals("bad", I18n.message(new IllegalStateException("bad")));
        assertTrue(I18n.message(new NullPointerException()).contains("NullPointerException"));
    }
}
