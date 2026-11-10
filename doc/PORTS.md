# The ports of the org.jtools programs

Every program serves its page on `127.0.0.1:<port>` by default (its `.ini`, `jtools.app.port` in its root pom). Two
programs on the same port cannot run side by side: the second one says the port is in use. This registry keeps them
apart — `scripts/new-project.sh` gives a new project the next free port and enters it here; it refuses one already
taken.

| Port | Program | Where |
|---|---|---|
| 8080 | MythoStory | git/MythoStory (Ktor; `--port` only, no .ini) |
| 8091 | XsdViewer | github/XsdViewer |
| 8092 | DCSBuilder | git/DCSBuilder |
| 8093 | WhichPaint | git/WhichPaint |
| 8093 | SensorSimulator | git/SensorSimulator — **collides with WhichPaint**: to move (8094 is free) |

Tests start their servers on port 0 (any free one) or on 18000 and above, so that a program in use is left alone.
