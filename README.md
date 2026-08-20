# Duco Control

A self-hosted web app to control a DucoBox Energy Comfort ventilation unit,
replacing the DucoHome cloud portal. A Java learning project (backend), with
a standalone vanilla HTML/CSS/JS frontend that can be installed as a PWA on
a phone.

## Requirements

- **A Duco system with the DUCO Connectivity Board installed** (Duco article
  0000-4810), exposing public API **v2.1 or newer** over your local network.
  Without this board there is no local REST API to talk to, and this tool
  will not work. Older systems using the Communication Board V1 are not
  supported for the same reason -- confirmed against the [official Home
  Assistant Duco integration docs](https://www.home-assistant.io/integrations/duco/),
  which target the same board and API. You can check your own device's
  version at `GET /v2/info` &rarr; `General.Board.PublicApiVersion`.
- Java 21+
- Maven 3.9+
- The DucoBox's local REST API reachable from wherever you run this
  (default: `http://duco.home.arpa`)

## Build and run

```bash
# Run tests (no network needed -- everything below uses test doubles)
mvn test

# Build a runnable "fat jar" (bundles org.json)
mvn package

# Start
java -jar target/duco-control.jar
```

By default the server listens on port **8765** and expects the DucoBox at
`http://duco.home.arpa`. Both can be overridden via environment variables
(useful if DNS doesn't resolve on the machine this runs on):

```bash
DUCO_BASE_URL=http://192.0.2.10 PORT=8080 java -jar target/duco-control.jar
```

Then open `http://localhost:8765/` (or the IP of the machine this runs on,
if you want to connect from your phone on the same network).

## Installing as a PWA

The frontend has a `manifest.webmanifest`, so the browser itself will offer
to install it as an app.

**iPhone (Safari):** open the URL &rarr; share button (square with an arrow
pointing up) &rarr; "Add to Home Screen".

**Android (Chrome):** open the URL &rarr; menu (three dots) &rarr;
"Install app" or "Add to Home screen".

After installation the app opens in standalone mode (no browser chrome),
with the generated fan icon.

## Architecture decisions

**No Spring Boot / no framework.** A deliberate choice from the assignment:
the goal is to learn the fundamentals (registering routes, parsing JSON by
hand, writing requests/responses yourself), not to learn how to configure a
framework that abstracts all of that away. The server runs on
`com.sun.net.httpserver.HttpServer`, built into the JDK.

**org.json instead of Jackson.** No reflection/annotations -- you navigate
the JSON structure explicitly (`getJSONObject("Ventilation").getString("Val")`),
which makes what's happening more visible than a framework that maps onto
objects automatically.

**Layered structure:**
- `model/` -- pure data and logic with no I/O: `VentilationState` (level
  &harr; Val string), `VentilationCommand`/`VentilationCommandPlanner`
  (duration decisions), `SystemStatus` (the `/status` DTO). Fully tested
  without any network.
- `client/` -- everything that talks to the DucoBox: `DucoHttpGateway`
  (interface, the testability seam), `JdkDucoHttpGateway` (real
  implementation), `DucoResponseParser` (JSON &rarr; domain objects),
  `DucoClient` (orchestrates the two, translates `IOException` into
  `DucoUnreachableException`), `AutoRevertScheduler` (background tasks for
  the 1h/2h options).
- `exception/` -- `DucoException` (unchecked base class),
  `DucoUnreachableException` (network failures), `DucoInvalidStateException`
  (invalid/unexpected states). Specific types instead of a generic
  `Exception`, so the HTTP layer can decide which status code (400 vs 502)
  a failure deserves based on type alone.
- `server/` -- the HTTP layer: `StatusHandler`, `SetVentilationHandler`,
  `StaticFileHandler` (serves the frontend on the same origin, so no CORS
  needed), `HttpExchangeSupport` (shared JSON/response helpers).

**Node ID: 1, not 5.** Before building anything, we ran `GET
/v2/info/nodes` live against the real system; it only has two nodes (1 =
the DucoBox itself, `Type: BOX`; 58 = a bathroom humidity sensor, `Type:
BSRH`, a child of node 1). Node 1 is the only controllable node and
therefore the target.

**Discrete levels instead of a free percentage.** The UI offers three
buttons (Low/Medium/High = 15%/50%/100%) instead of a slider. Confirmed
both by our own measurements (`FlowLvlTgt` in `GET /v2/info/nodes`) and by
Home Assistant's own Duco integration (which goes deeper into the protocol
than we do): there is no freely settable percentage at the protocol level,
only three fixed levels. See the comments in `VentilationState` for the
full reasoning.

**Duration: freely chosen minutes, not a fixed list.** Via `GET
/v2/action/nodes/1` (a self-documenting endpoint!) and live curl tests it
turned out the DucoBox itself only knows 15/30/45-minute timers (`MAN1`,
`MAN1x2`, `MAN1x3`) -- a genuine hardware limit. `VentilationCommandPlanner`
uses that native timer only for those exact three values (the box manages
the countdown itself -- robust, survives a restart of our server). Every
other duration -- including arbitrary values like 37 minutes -- goes
through a permanent CNT state plus `AutoRevertScheduler`, which (unlike the
box) accepts any delay. Since that path was already fully generic, there
was no technical reason to restrict the frontend to a handful of fixed
options: the duration dial lets you drag to any whole minute (defensively
capped at `VentilationCommandPlanner.MAX_DURATION_MINUTES`, 24h, purely to
stop a stray drag from locking ventilation at one setting for days).
Deliberate trade-off, unchanged from the original fixed-option design: if
our server restarts while a scheduled revert is pending, that revert is
lost and ventilation stays at that setting until manually corrected on the
device itself -- not catastrophic, just something to know. See the
comments in `VentilationCommandPlanner` for the full measurement results.

**Duration dial: freely draggable, no external UI library.** `frontend/app.js`
implements a radial picker with plain SVG and the Pointer Events API
(`setPointerCapture`, so one code path drives both mouse-drag and
touch-drag) -- no charting/slider library added, consistent with the rest
of the frontend. The center is a small dead zone meaning "unlimited";
dragging onto the ring maps the angle (clockwise from 12 o'clock) linearly
to a duration, continuously, with no snapping -- see `durationForOffset()`
for the angle math, deliberately written as a pure function so it could be
sanity-checked by hand (no headless-browser testing is set up in this
project).

**`bypassActive` can be `null`.** We grepped full raw dumps of both `GET
/v2/info` and `GET /v2/info/nodes` for "bypass" (case-insensitive) -- zero
matches on the device this was built against (`COMFORT_D400_R`). Bypass
support appears to depend on Duco model/firmware, so the field stays
present in `SystemStatus` with `null` as an explicit, documented fallback
rather than being removed; see "Known limitations" below.

**Device model read dynamically, not hardcoded.** The UI subtitle and
`SystemStatus.deviceModel` come from `General.Board.BoxSubTypeName` in
`GET /v2/info`, parsed defensively (falls back to `null`, hiding the
subtitle) so the same build works across Duco models instead of assuming
one specific device.

**HTTP/1.1 explicitly forced.** `java.net.http.HttpClient` defaults to
attempting an HTTP/2 upgrade, which the DucoBox's minimal embedded web
server doesn't understand (returned a bare HTTP 400) -- discovered by
testing live against the box. See the comment in `JdkDucoHttpGateway`.

## Tests

```bash
mvn test
```

35 JUnit 5 tests, all without any network:
- `VentilationStateTest`, `VentilationCommandPlannerTest` -- pure mapping
  logic, built with TDD (red phase made visible first, then the
  implementation) for every new addition.
- `DucoResponseParserTest` -- JSON &rarr; domain object, using real payloads
  fetched live from the system as test fixtures.
- `DucoClientTest` -- exception translation (`IOException` &rarr;
  `DucoUnreachableException`, a rejected Duco response &rarr;
  `DucoInvalidStateException`), via a hand-written `FakeDucoHttpGateway`
  test double instead of Mockito (the interface is small enough that a
  hand-rolled fake is just as simple, and it shows what a mocking framework
  actually automates for you).
- `AutoRevertSchedulerTest` -- concurrency behavior (canceling a previously
  scheduled task) using a real `ScheduledExecutorService` and short delays.

The HTTP server layer itself (`server/`) and `JdkDucoHttpGateway`
deliberately have no unit tests: they're thin adapters around
`com.sun.net.httpserver` and `java.net.http` respectively, where the
underlying logic is already tested elsewhere. That layer was instead tested
manually (and live, against the real DucoBox) with curl while building it.

## Known limitations

- `bypassActive` is `null` on the device this was built against, and may be
  `null` on yours too -- bypass support varies by Duco model and firmware.
  If your device exposes it (check a full `GET /v2/info` dump for anything
  resembling a bypass field), `DucoResponseParser`/`SystemStatus` can be
  extended to read it.
- Any scheduled duration outside the native 15/30/45-minute timers doesn't
  survive a server restart (see "Duration: freely chosen minutes" above).
- CNT2/CNT3 percentages (50%/100%) were verified for the specific device
  this was built against; a different Duco model may use different levels.
- The duration dial is a custom drag control (SVG + Pointer Events) with
  only basic `role="slider"`/`aria-value*` attributes; it has no keyboard
  support (arrow keys don't move it). Mouse and touch dragging both work.
