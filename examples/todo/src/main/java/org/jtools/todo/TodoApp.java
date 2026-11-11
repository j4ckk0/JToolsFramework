package org.jtools.todo;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

import org.jtools.framework.app.WebApp;
import org.jtools.framework.server.WebServer;
import org.jtools.framework.support.Json;

/**
 * The program: {@link Todos} served to its page. An endpoint reads the request, asks the business, and says its answer as
 * JSON — a translation, no rule. A rule broken is the business's exception, which the server answers {@code {"error"}}:
 * an {@code IllegalArgumentException} as 400, a to-do that is not there as 404 ({@code refuse}).
 */
public final class TodoApp {

    public static final String TODOS = WebServer.API + "/todos", DONE = TODOS + "/done";

    private TodoApp() {}

    public static WebApp program() {
        Todos todos = new Todos();
        return WebApp.named("Todo").defaultPort(8088).pageRoot("web").endpoints(server -> endpoints(server, todos));
    }

    /** {@code GET /api/todos}: the list; {@code POST /api/todos} {@code {"title"}}: one added; {@code POST /api/todos/done?id=}. */
    static WebServer endpoints(WebServer server, Todos todos) {
        return server.refuse(NoSuchElementException.class, 404)
                .get(TODOS, ex -> json(todos.list()))
                .post(TODOS, ex -> json(todos.add(ex.jsonObject().get("title") instanceof String title ? title : null)))
                .post(DONE, ex -> json(todos.done(Integer.parseInt(ex.query("id", "0")))));
    }

    private static List<Map<String, Object>> json(List<Todos.Todo> list) {
        return list.stream().map(TodoApp::json).toList();
    }

    private static Map<String, Object> json(Todos.Todo todo) {
        Map<String, Object> out = Json.obj();
        out.put("id", todo.id());
        out.put("title", todo.title());
        out.put("done", todo.done());
        return out;
    }

    public static void main(String[] args) {
        program().launch(args);
    }
}
