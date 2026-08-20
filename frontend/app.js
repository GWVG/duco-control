// Talks exclusively to our own Java backend (same origin, so plain relative
// paths -- never directly to the DucoBox).

const levelButtons = document.querySelectorAll(".level-button");
const setButton = document.getElementById("set-button");
const setFeedback = document.getElementById("set-feedback");
const statusError = document.getElementById("status-error");

// No free percentage anymore (the DucoBox doesn't know that either, see
// backend) -- just remember which of the three buttons is active.
let selectedLevel = 1;
selectLevelButton(selectedLevel);

levelButtons.forEach((button) => {
  button.addEventListener("click", () => {
    selectedLevel = Number(button.dataset.level);
    selectLevelButton(selectedLevel);
  });
});

function selectLevelButton(level) {
  levelButtons.forEach((button) => {
    button.classList.toggle("selected", Number(button.dataset.level) === level);
  });
}

// --- Duration dial -----------------------------------------------------
//
// A freely draggable radial picker: the center is "unlimited" (matches the
// backend's durationMinutes=0 sentinel), and dragging out onto the ring
// picks an exact whole-minute duration from the angle. Unlike the level
// buttons, there's no snapping here on purpose -- the backend now accepts
// any whole minute for durations that don't match a native box timer (see
// VentilationCommandPlanner), so there's nothing to snap TO except at the
// very center.

const dial = document.getElementById("duration-dial");
const dialHandle = document.getElementById("dial-handle");
const dialProgress = document.getElementById("dial-progress");
const dialLabel = document.getElementById("dial-label");

const DIAL_VIEWBOX_SIZE = 200; // must match the SVG viewBox in index.html
const DIAL_RING_RADIUS = 80; // must match the <circle r="80"> in index.html
const DIAL_DEAD_ZONE_RADIUS = 20; // viewBox units around the center that count as "unlimited"
// One full turn of the dial = this many minutes. A UI-only ceiling, chosen
// as a practical range for a temporary ventilation boost -- independent of
// VentilationCommandPlanner.MAX_DURATION_MINUTES (1440), which is a
// separate, more generous safety cap on the backend. Change this one
// constant to offer a longer or shorter single-turn range.
const DIAL_MAX_MINUTES = 180;

let selectedDurationMinutes = 0; // 0 = unlimited, same sentinel the backend uses

/**
 * Converts a pointer position -- given as (dx, dy) offsets from the dial's
 * center, in the SVG's own 200x200 viewBox units -- into a duration: either
 * null (unlimited, inside the dead zone) or a whole number of minutes from
 * 1 to DIAL_MAX_MINUTES.
 *
 * Deliberately a pure function (no DOM reads/writes) so the angle math can
 * be sanity-checked by hand, e.g. in a plain `node` REPL, without needing a
 * browser to click through -- this project has no headless-browser testing
 * set up, so that's the practical alternative here.
 */
function durationForOffset(dx, dy) {
  const distance = Math.hypot(dx, dy);
  if (distance < DIAL_DEAD_ZONE_RADIUS) {
    return null;
  }
  // Math.atan2(y, x) is the usual signature and measures the angle
  // counterclockwise from 3 o'clock -- the standard math convention.
  // Swapping the arguments to atan2(dx, -dy) instead re-measures it
  // clockwise from 12 o'clock, i.e. the way an actual clock (and this
  // dial) reads. Adding 360 and taking `% 360` normalizes the result to
  // [0, 360), since atan2 alone returns negative angles for the left half.
  const angleDegrees = (Math.atan2(dx, -dy) * (180 / Math.PI) + 360) % 360;
  const minutes = Math.round((angleDegrees / 360) * DIAL_MAX_MINUTES);
  // Once you're past the dead zone you're committed to a real duration --
  // never show/send 0 minutes for a point that isn't the true center.
  return Math.max(1, minutes);
}

function handleDialPointerEvent(event) {
  const rect = dial.getBoundingClientRect();
  const centerX = rect.left + rect.width / 2;
  const centerY = rect.top + rect.height / 2;
  // Scale on-screen pixels to the SVG's own viewBox units, since the dial
  // can be rendered at any CSS size (rect.width) while the viewBox (and
  // therefore our DIAL_RING_RADIUS/DEAD_ZONE constants) stays fixed at 200.
  const scale = DIAL_VIEWBOX_SIZE / rect.width;
  const dx = (event.clientX - centerX) * scale;
  const dy = (event.clientY - centerY) * scale;

  const minutes = durationForOffset(dx, dy);
  selectedDurationMinutes = minutes === null ? 0 : minutes;
  renderDial(selectedDurationMinutes);
}

dial.addEventListener("pointerdown", (event) => {
  // setPointerCapture redirects all further pointer events to `dial`
  // regardless of where the pointer physically moves -- without it, moving
  // the mouse/finger past the edge of the widget mid-drag would just stop
  // firing events on it. Using Pointer Events (not separate mouse/touch
  // handlers) means this same code drives both mouse-drag on desktop and
  // finger-drag on a phone.
  dial.setPointerCapture(event.pointerId);
  handleDialPointerEvent(event);
});

dial.addEventListener("pointermove", (event) => {
  if (dial.hasPointerCapture(event.pointerId)) {
    handleDialPointerEvent(event);
  }
});

dial.addEventListener("pointerup", (event) => {
  dial.releasePointerCapture(event.pointerId);
});

function renderDial(durationMinutes) {
  dial.setAttribute("aria-valuenow", String(durationMinutes));

  if (durationMinutes === 0) {
    dialHandle.setAttribute("cx", "100");
    dialHandle.setAttribute("cy", "100");
    dialLabel.textContent = "∞"; // infinity symbol
    setDialProgress(0);
    return;
  }

  // Clamp only for drawing the handle/progress -- e.g. a duration loaded
  // from a previous session could exceed DIAL_MAX_MINUTES (the backend
  // allows up to 1440), but selectedDurationMinutes itself stays exact.
  const fraction = Math.min(durationMinutes / DIAL_MAX_MINUTES, 1);
  const angleRadians = fraction * 2 * Math.PI; // 0 at the top, growing clockwise
  const handleX = 100 + DIAL_RING_RADIUS * Math.sin(angleRadians);
  const handleY = 100 - DIAL_RING_RADIUS * Math.cos(angleRadians);
  dialHandle.setAttribute("cx", String(handleX));
  dialHandle.setAttribute("cy", String(handleY));
  dialLabel.textContent = formatDuration(durationMinutes);
  setDialProgress(fraction);
}

// Classic SVG "radial progress ring" technique: stroke-dasharray splits the
// stroke into one dash the length of the whole circle's circumference (so,
// effectively, one unbroken dash) and one gap of length 0; stroke-dashoffset
// then shifts where that dash starts, which -- for a single full-length
// dash -- has the visual effect of "hiding" a fraction of the circle.
function setDialProgress(fraction) {
  const circumference = 2 * Math.PI * DIAL_RING_RADIUS;
  dialProgress.style.strokeDasharray = `${circumference}`;
  dialProgress.style.strokeDashoffset = `${circumference * (1 - fraction)}`;
}

function formatDuration(minutes) {
  if (minutes < 60) {
    return `${minutes}m`;
  }
  const hours = Math.floor(minutes / 60);
  const remainderMinutes = minutes % 60;
  return remainderMinutes === 0 ? `${hours}h` : `${hours}h ${remainderMinutes}m`;
}

renderDial(selectedDurationMinutes);

// --- /set ----------------------------------------------------------------

setButton.addEventListener("click", async () => {
  setButton.disabled = true;
  hide(setFeedback);
  try {
    const response = await fetch("/set", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        level: selectedLevel,
        durationMinutes: selectedDurationMinutes,
      }),
    });
    const body = await response.json();
    if (!response.ok) {
      throw new Error(body.error || `Unexpected error (HTTP ${response.status})`);
    }
    showFeedback(`Set to: ${body.val}`, "success");
    await refreshStatus();
  } catch (err) {
    showFeedback(err.message, "error");
  } finally {
    setButton.disabled = false;
  }
});

async function refreshStatus() {
  try {
    const response = await fetch("/status");
    const status = await response.json();
    if (!response.ok) {
      throw new Error(status.error || `Unexpected error (HTTP ${response.status})`);
    }
    renderStatus(status);
    hide(statusError);
  } catch (err) {
    statusError.textContent = err.message;
    show(statusError);
  }
}

function renderStatus(status) {
  updateDeviceSubtitle(status.deviceModel);

  // Only show Outside/Inside (Inside = tempExtractCelsius) -- Supply/Exhaust
  // are hard to interpret for an end user without a ventilation diagram.
  // They remain available in the /status JSON itself (SystemStatus).
  setText("temp-outside", formatTemp(status.tempOutsideCelsius));
  setText("temp-extract", formatTemp(status.tempExtractCelsius));
  setText("humidity", `${status.humidityPercent}%`);
  setText("filter-days", `${status.filterDaysRemaining} days`);
  setText("bypass", formatBypass(status.bypassActive));
  setText("current-state", formatCurrentState(status));
}

// Shows the device model reported by the backend (e.g. "COMFORT_D400_R"),
// or hides the subtitle entirely if it's unknown -- no model name hardcoded
// in the HTML, so this works unmodified against any Duco device.
function updateDeviceSubtitle(deviceModel) {
  const subtitle = document.getElementById("device-subtitle");
  if (deviceModel) {
    subtitle.textContent = deviceModel;
    subtitle.hidden = false;
  } else {
    subtitle.hidden = true;
  }
}

function formatBypass(bypassActive) {
  if (bypassActive === null) {
    return "Not available on this device";
  }
  return bypassActive ? "Open" : "Closed";
}

// Shows only the percentage ("100%"), not the internal Duco code
// ("MAN3 (100%)") -- the MAN/CNT distinction is an implementation detail,
// not user-facing information. If the system is in AUTO mode (no fixed
// percentage), show "Automatic" instead of the raw Val/Mode code, for the
// same reason.
function formatCurrentState(status) {
  if (status.currentPercentage !== null) {
    return `${status.currentPercentage}%`;
  }
  return "Automatic";
}

function formatTemp(celsius) {
  return `${celsius.toFixed(1)}°C`;
}

function setText(elementId, text) {
  document.getElementById(elementId).textContent = text;
}

function showFeedback(message, kind) {
  setFeedback.textContent = message;
  setFeedback.className = `feedback ${kind}`;
  show(setFeedback);
}

function show(element) {
  element.hidden = false;
}

function hide(element) {
  element.hidden = true;
}

// Load once on start, then refresh every 30s so the status block stays
// current without needing a manual reload.
refreshStatus();
setInterval(refreshStatus, 30_000);
