package org.jtools.todo;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * The business: a to-do list and its rules — what the page shows, and decides nothing of. Pure Java: no HTTP here, so it
 * is tested, and usable, without a server.
 */
public final class Todos {

    /** One thing to do. */
    public record Todo(int id, String title, boolean done) {}

    private final List<Todo> todos = new ArrayList<>();
    private int lastId;

    public synchronized List<Todo> list() {
        return List.copyOf(todos);
    }

    /** A new to-do; refused when its title is empty — the rule is here, not in the page. */
    public synchronized Todo add(String title) {
        if (title == null || title.isBlank()) throw new IllegalArgumentException("a to-do needs a title");
        Todo todo = new Todo(++lastId, title.strip(), false);
        todos.add(todo);
        return todo;
    }

    /** The to-do marked done; none of that id is a {@link NoSuchElementException}. */
    public synchronized Todo done(int id) {
        for (int i = 0; i < todos.size(); i++) {
            if (todos.get(i).id() == id) {
                Todo done = new Todo(id, todos.get(i).title(), true);
                todos.set(i, done);
                return done;
            }
        }
        throw new NoSuchElementException("no to-do " + id);
    }
}
