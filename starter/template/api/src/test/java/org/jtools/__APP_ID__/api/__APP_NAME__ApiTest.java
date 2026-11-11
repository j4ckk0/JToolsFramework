package org.jtools.__APP_ID__.api;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

import org.jtools.__APP_ID__.core.__APP_NAME__;
import org.jtools.framework.server.WebServer;
import org.jtools.framework.support.Json;
import org.junit.jupiter.api.Test;

class __APP_NAME__ApiTest {

    @Test
    void answersTheBusinessStatus() throws Exception {
        __APP_NAME__ application = new __APP_NAME__();
        try (WebServer server = __APP_NAME__Api.endpoints(new WebServer(), application).start("127.0.0.1", 0)) {
            HttpResponse<String> status = HttpClient.newHttpClient().send(
                    HttpRequest.newBuilder(URI.create(server.url() + __APP_NAME__Api.STATUS.substring(1))).build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(200, status.statusCode());
            Map<?, ?> json = (Map<?, ?>) Json.parse(status.body());
            assertEquals(application.status().summary(), json.get("summary"));
        }
    }
}
