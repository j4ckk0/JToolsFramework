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
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Where the program is installed — the folder of the jar it runs from, where its {@code .ini} is —, else the
 * working directory; and its version, from the jar's manifest. Generic: the name of the settings file is the caller's.
 */
public final class Installation {

    /** The version of a run from its classes, not from a jar. */
    public static final String DEVELOPMENT_VERSION = "dev";
    private static final String JAR_EXTENSION = ".jar";

    private Installation() {}

    /** The version of the jar holding {@code anchor}: its manifest's {@code Implementation-Version}, else {@link #DEVELOPMENT_VERSION}. */
    public static String version(Class<?> anchor) {
        Package pkg = anchor.getPackage();
        String version = pkg == null ? null : pkg.getImplementationVersion();
        return version == null || version.isBlank() ? DEVELOPMENT_VERSION : version;
    }

    /** The folder of the jar holding {@code anchor}, or the working directory when the program runs from its classes. */
    public static Path home(Class<?> anchor) {
        try {
            Path code = Path.of(anchor.getProtectionDomain().getCodeSource().getLocation().toURI());
            if (code.getFileName() != null && code.getFileName().toString().endsWith(JAR_EXTENSION) && code.getParent() != null) return code.getParent();
        } catch (Exception notAFile) { /* the classes come from somewhere else: the working directory below */ }
        return Path.of("");
    }

    /**
     * The settings of an {@code .ini} file — {@code name=value} lines, {@code #} comments —, empty when there is no
     * such file: an installation without one runs on the defaults.
     */
    public static Properties ini(Path file) throws IOException {
        Properties settings = new Properties();
        if (Files.isRegularFile(file)) {
            try (Reader in = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                settings.load(in);
            }
        }
        return settings;
    }
}
