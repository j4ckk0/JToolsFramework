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

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.LogRecord;

import org.junit.jupiter.api.Test;

class LogFormatterTest {

    @Test
    void writesTheLevelInEnglishWhateverTheMachinesLanguage() {
        Locale machine = Locale.getDefault();
        try {
            Locale.setDefault(Locale.FRENCH);
            LogRecord record = new LogRecord(Level.WARNING, "port {0} refused");
            record.setLoggerName("org.jtools.demo");
            record.setParameters(new Object[] {8093});
            String line = new LogFormatter().format(record);
            assertTrue(line.matches("\\d{4}-\\d\\d-\\d\\d \\d\\d:\\d\\d:\\d\\d WARNING org\\.jtools\\.demo - port 8093 refused\\R"), line);
        } finally {
            Locale.setDefault(machine);
        }
    }

    @Test
    void writesTheTraceBelow() {
        LogRecord record = new LogRecord(Level.SEVERE, "failed");
        record.setThrown(new IllegalStateException("disk on fire"));
        String text = new LogFormatter().format(record);
        assertTrue(text.contains("SEVERE ") && text.contains("java.lang.IllegalStateException: disk on fire"), text);
    }
}
