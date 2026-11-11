package org.jtools.__APP_ID__.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

/** The port is said in three places that must agree; the tests run from the root of the repository. */
class LauncherTest {

    @Test
    void theRootPomSaysTheDefaultPort() throws Exception {
        Matcher port = Pattern.compile("<jtools.app.port>(\\d+)</jtools.app.port>").matcher(Files.readString(Path.of("pom.xml")));
        assertTrue(port.find(), "pom.xml: no <jtools.app.port>");
        assertEquals(Launcher.DEFAULT_PORT, Integer.parseInt(port.group(1)), "the distributions' .ini says the pom's port");
    }

    @Test
    void theFrontendsProxySaysTheDefaultPort() throws Exception {
        assertTrue(Files.readString(Path.of("frontend/proxy.conf.json")).contains("localhost:" + Launcher.DEFAULT_PORT),
                "ng serve sends the page's API calls to the program's default port");
    }
}
