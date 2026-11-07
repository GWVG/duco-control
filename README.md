# Duco Control

A self-hosted web app to control a DucoBox Energy Comfort ventilation unit,
replacing the DucoHome cloud portal. A Java learning project (backend), with
a standalone vanilla HTML/CSS/JS frontend that can be installed as a PWA on
a phone.

## Requirements

- **A Duco system with the DUCO Connectivity Board installed** (Duco article
  0000-4810), exposing public API **v2.1 or newer** over your local network.
  Older systems using the Communication Board V1 are not supported --
  confirmed against the [official Home Assistant Duco integration
  docs](https://www.home-assistant.io/integrations/duco/), which target the
  same board and API. Check your device's version at `GET /v2/info` &rarr;
  `General.Board.PublicApiVersion`.
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

The node ID to control is set in `DucoControlApp` (`MAIN_NODE_ID`, default
`1`). Run `GET /v2/info/nodes` against your own system to find the right ID
-- node layouts vary between installations.

## Checking your device's supported actions

`GET /v2/action/nodes/<your-node-id>` returns the exact actions and `Val`
values your node supports. On the device this project was built against,
that returned:

```json
{"Node":1,"Actions":[{"Action":"SetVentilationState","ValType":"Enum","Enum":["AUTO","AUT1","AUT2","AUT3","MAN1","MAN2","MAN3","EMPT","CNT1","CNT2","CNT3","MAN1x2","MAN2x2","MAN3x2","MAN1x3","MAN2x3","MAN3x3"]},{"Action":"SetIdentify","ValType":"Boolean"}]}
```

Worth checking on your own device before assuming this project's findings
(e.g. `VentilationState`'s Val list) apply unchanged.

## Installing as a PWA

The frontend has a `manifest.webmanifest`, so the browser itself will offer
to install it as an app.

**iPhone (Safari):** open the URL &rarr; share button (square with an arrow
pointing up) &rarr; "Add to Home Screen".

**Android (Chrome):** open the URL &rarr; menu (three dots) &rarr;
"Install app" or "Add to Home screen".

After installation the app opens in standalone mode (no browser chrome),
with the generated fan icon.

## Architecture

**Layered structure:**
- `model/` -- pure data and logic with no I/O: `VentilationState` (level
  &harr; Val string), `VentilationCommand`/`VentilationCommandPlanner`
  (duration decisions), `SystemStatus` (the `/status` DTO).
- `client/` -- everything that talks to the DucoBox: `DucoHttpGateway`
  (interface, the testability seam), `JdkDucoHttpGateway` (real
  implementation), `DucoResponseParser` (JSON &rarr; domain objects),
  `DucoClient` (orchestrates the two), `AutoRevertScheduler` (background
  revert-to-AUTO tasks).
- `exception/` -- `DucoException` (base), `DucoUnreachableException`
  (network failures), `DucoInvalidStateException` (invalid/unexpected
  states), letting the HTTP layer pick a status code by type.
- `server/` -- the HTTP layer: `StatusHandler`, `SetVentilationHandler`,
  `SetAutoHandler` (`POST /auto`, sends the box back to its own automatic
  control loop), `StaticFileHandler` (serves the frontend on the same
  origin, so no CORS needed), `HttpExchangeSupport` (shared JSON/response
  helpers).

**Discrete levels instead of a free percentage.** The UI offers three
buttons (Low/Medium/High = 15%/50%/100%) instead of a slider: the DucoBox
protocol has no freely settable percentage, only three fixed levels
(confirmed via `FlowLvlTgt` in `GET /v2/info/nodes`, and by Home Assistant's
own Duco integration).

**Duration: freely chosen minutes, not a fixed list.** Only 15/30/45-minute
durations map to a native DucoBox timer (`MAN1`/`MAN1x2`/`MAN1x3`, via `GET
/v2/action/nodes/<id>`). Every other duration uses a permanent CNT state
plus `AutoRevertScheduler`, which accepts an arbitrary delay -- so the
duration dial lets you pick any whole minute, capped defensively at
`VentilationCommandPlanner.MAX_DURATION_MINUTES` (24h). If the server
restarts while a revert is pending, it's lost and ventilation stays at that
setting until corrected manually.

**Duration dial.** `frontend/app.js` implements a radial drag picker with
plain SVG and the Pointer Events API. Center = unlimited; dragging onto the
ring maps the angle to a duration, continuously. One full 360° turn = 60
minutes, and turning further keeps counting instead of wrapping (720° =
120 min), shown as stacked "lap" rings, each a hue-shifted color -- capped
at 4 laps (4h) as a practical UI limit. The angle math lives in
`advanceDialState()`, a pure function so the multi-lap accumulation can be
sanity-checked by simulating a drag as a sequence of small steps by hand.

**`bypassActive` can be `null`.** Not found in `GET /v2/info` or `GET
/v2/info/nodes` on the device this was built against -- bypass support
appears to depend on Duco model/firmware. See "Known limitations".

**Device model read dynamically.** The UI subtitle and
`SystemStatus.deviceModel` come from `General.Board.BoxSubTypeName`,
falling back to `null` (hiding the subtitle) if that field is absent.

**HTTP/1.1 explicitly forced.** `java.net.http.HttpClient` defaults to
attempting an HTTP/2 upgrade, which the DucoBox's embedded web server
doesn't understand (returns HTTP 400). See `JdkDucoHttpGateway`.

## Tests

```bash
mvn test
```

35 JUnit 5 tests, all without any network:
- `VentilationStateTest`, `VentilationCommandPlannerTest` -- pure mapping logic.
- `DucoResponseParserTest` -- JSON &rarr; domain object, using real payloads as fixtures.
- `DucoClientTest` -- exception translation, via a hand-written `FakeDucoHttpGateway` test double.
- `AutoRevertSchedulerTest` -- concurrency behavior with a real `ScheduledExecutorService`.

`server/` and `JdkDucoHttpGateway` have no unit tests -- they're thin
adapters over `com.sun.net.httpserver`/`java.net.http`, tested manually
against a real DucoBox instead.

## Known limitations

- `bypassActive` is `null` on the device this was built against, and may be
  `null` on yours too. If your device exposes it, `DucoResponseParser`/
  `SystemStatus` can be extended to read it.
- Any scheduled duration outside the native 15/30/45-minute timers doesn't
  survive a server restart.
- CNT2/CNT3 percentages (50%/100%) were verified for the specific device
  this was built against; a different Duco model may use different levels.
- The duration dial has no keyboard support (arrow keys don't move it).
  Mouse and touch dragging both work.
