package org.jtools.greeter;

import java.util.Locale;
import java.util.Map;

import org.jtools.framework.app.SettingsFields;
import org.jtools.framework.app.UserSettings;
import org.jtools.framework.app.WebApp;
import org.jtools.framework.server.Exchange;
import org.jtools.framework.server.WebServer;
import org.jtools.framework.support.Catalogue;
import org.jtools.framework.support.I18n;

/**
 * What a program brings of its own: its texts in two languages — answered in the page's ({@code Accept-Language},
 * {@code ex.i18n()}) —, an option of its own on the command line ({@code --greeting Howdy}), and a setting of its own,
 * the name to greet, kept from one run to the next beside the framework's ({@code /api/settings}, {@link SettingsFields}).
 */
public final class Greeter {

    static final String ID = "greeter", GREETING = WebServer.API + "/greeting", J_NAME = "name", J_TEXT = "text";
    /** The program's texts: i18n/greeter.properties, i18n/greeter_fr.properties. */
    static final Catalogue TEXTS = I18n.catalogue(Greeter.class, "i18n.greeter", Locale.ENGLISH, Locale.FRENCH);

    private String word;   // --greeting's, else the language's

    public static WebApp program() {
        Greeter greeter = new Greeter();
        return WebApp.named("Greeter").defaultPort(8087).pageRoot("web").texts(TEXTS)
                .option("--greeting", "WORD", "option.greeting", word -> greeter.word = word)
                .settingsFields(new NameSetting())
                .routes(server -> server.get(GREETING, greeter::greet));
    }

    /** {@code GET /api/greeting}: {@code {"text"}}, the greeting in the page's language, to the name kept. */
    private Map<String, Object> greet(Exchange ex) {
        I18n say = ex.i18n();
        String name = UserSettings.of(ID).node().get(J_NAME, "");
        if (name.isBlank()) name = say.t("nobody");
        return Map.of(J_TEXT, say.t("greeting", word != null ? word : say.t("greeting.word"), name));
    }

    /** The name to greet: the program's own field of /api/settings, validated before it is kept. */
    static final class NameSetting implements SettingsFields {
        @Override
        public void read(Map<String, Object> answer) {
            answer.put(J_NAME, UserSettings.of(ID).node().get(J_NAME, ""));
        }

        @Override
        public void write(Map<String, Object> asked, Exchange ex) {
            if (!asked.containsKey(J_NAME)) return;
            if (!(asked.get(J_NAME) instanceof String name)) throw new WebServer.Refusal(400, ex.i18n().t("settings.name"));
            UserSettings.of(ID).node().put(J_NAME, name.strip());
        }
    }

    public static void main(String[] args) {
        program().launch(args);
    }
}
