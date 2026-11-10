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

import java.util.OptionalInt;
import java.util.prefs.Preferences;

/**
 * What the user changes from the page's Settings menu, kept from one run to the next in their {@link Preferences}
 * (the registry on Windows, {@code ~/.java/.userPrefs} on Linux): whether the program stops once its last page is
 * closed, and the port it listens on — the one before the {@code .ini}'s, after the command line's. The program keeps
 * its own choices beside them, in {@link #node()}.
 */
public final class UserSettings {

    private static final String AUTO_STOP = "autoStop", PORT = "port", NODE_PROPERTY_SUFFIX = ".preferences", NODE_PREFIX = "org/jtools/";
    /** A port not chosen: the {@code .ini}'s, else the program's default. */
    public static final int PORT_UNSET = 0;

    private final Preferences node;

    private UserSettings(Preferences node) {
        this.node = node;
    }

    /**
     * The settings of the program of that id: the node {@code org/jtools/<id>} of the user's preferences, or the one the
     * system property {@code <id>.preferences} names — what the tests use, so as never to touch the user's.
     */
    public static UserSettings of(String programId) {
        return new UserSettings(Preferences.userRoot().node(System.getProperty(programId + NODE_PROPERTY_SUFFIX, NODE_PREFIX + programId)));
    }

    /** Whether the program stops once its last page is closed (it does unless the user said otherwise). */
    public boolean autoStop() {
        return node.getBoolean(AUTO_STOP, true);
    }

    public void setAutoStop(boolean on) {
        node.putBoolean(AUTO_STOP, on);
    }

    /** The port the user chose from the Settings menu; empty when they chose none. */
    public OptionalInt port() {
        int port = node.getInt(PORT, PORT_UNSET);
        return port == PORT_UNSET ? OptionalInt.empty() : OptionalInt.of(port);
    }

    /** Keeps the port for the next runs; {@link #PORT_UNSET} forgets it. */
    public void setPort(int port) {
        if (port == PORT_UNSET) node.remove(PORT); else node.putInt(PORT, port);
    }

    /** The node of the program's own settings, beside these. */
    public Preferences node() {
        return node;
    }
}
