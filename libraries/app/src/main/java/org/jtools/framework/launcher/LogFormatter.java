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

import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.MessageFormat;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.logging.Formatter;
import java.util.logging.LogRecord;

/**
 * One line per record — {@code 2026-11-10 21:34:38 INFO    org.jtools.… - message} — the stack trace below when there is
 * one. The level is its English name and the numbers are written alike on every machine: the JDK's {@code SimpleFormatter}
 * speaks the machine's language ({@code INFOS}, {@code AVERTISSEMENT}, {@code 8 093}), and a log is read by whoever
 * helps, and searched.
 */
public final class LogFormatter extends Formatter {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Override
    public String format(LogRecord record) {
        StringBuilder line = new StringBuilder(128)
                .append(TIME.format(LocalDateTime.ofInstant(record.getInstant(), ZoneId.systemDefault()))).append(' ')
                .append(String.format("%-7s", record.getLevel().getName())).append(' ')
                .append(record.getLoggerName()).append(" - ")
                .append(message(record)).append(System.lineSeparator());
        if (record.getThrown() != null) {
            StringWriter trace = new StringWriter();
            record.getThrown().printStackTrace(new PrintWriter(trace));
            line.append(trace);
        }
        return line.toString();
    }

    /**
     * The message, its {0}… filled in alike on every machine and numbers as they are: the JDK's formatMessage writes the
     * port 8093 as "8 093" in French, and MessageFormat as "8,093" in any language.
     */
    private static String message(LogRecord record) {
        Object[] parameters = record.getParameters();
        String message = record.getMessage();
        if (message == null || parameters == null || parameters.length == 0 || !message.contains("{")) return message;
        Object[] plain = new Object[parameters.length];
        for (int i = 0; i < parameters.length; i++) plain[i] = parameters[i] instanceof Number ? String.valueOf(parameters[i]) : parameters[i];
        try {
            return new MessageFormat(message, Locale.ROOT).format(plain);
        } catch (IllegalArgumentException notAPattern) {
            return message;
        }
    }
}
