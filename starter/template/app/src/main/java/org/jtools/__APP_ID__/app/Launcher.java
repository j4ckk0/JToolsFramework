package org.jtools.__APP_ID__.app;

import org.jtools.__APP_ID__.api.__APP_NAME__Api;
import org.jtools.__APP_ID__.core.__APP_NAME__;
import org.jtools.framework.app.WebApp;

/**
 * The program: JToolsFramework's {@link WebApp} — the command line, __APP_ID__.ini, the browser, the stop once the last
 * page is closed — serving __APP_NAME__'s API. {@code --help} lists the options.
 */
public final class Launcher {

    /** The port when neither the command line, the Settings menu nor __APP_ID__.ini names one: the root pom's jtools.app.port (LauncherTest checks). */
    static final int DEFAULT_PORT = __APP_PORT__;

    private Launcher() {}

    public static void main(String[] args) {
        __APP_NAME__ application = new __APP_NAME__();
        WebApp.named(__APP_NAME__.NAME).id("__APP_ID__").defaultPort(DEFAULT_PORT)
                .routes(server -> __APP_NAME__Api.routes(server, application))
                .launch(args);
    }
}
