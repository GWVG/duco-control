const levelButtons = document.querySelectorAll(".level-button");
const setButton = document.getElementById("set-button");
const setFeedback = document.getElementById("set-feedback");
const statusError = document.getElementById("status-error");

// Mirrors VentilationState's level-to-percentage mapping, for display only.
const LEVEL_PERCENTAGES = { 1: 15, 2: 50, 3: 100 };

// null until the user picks a level; Set stays disabled until then.
let selectedLevel = null;
selectLevelButton(selectedLevel);
setButton.disabled = true;

levelButtons.forEach((button) => {
  button.addEventListener("click", () => {
    selectedLevel = Number(button.dataset.level);
    selectLevelButton(selectedLevel);
    setButton.disabled = false;
  });
});

function selectLevelButton(level) {
  levelButtons.forEach((button) => {
    button.classList.toggle("selected", Number(button.dataset.level) === level);
  });
}

// --- Duration dial -----------------------------------------------------
// Freely draggable radial picker. Center = unlimited (durationMinutes=0).
// Dragging onto the ring picks a whole-minute duration from the angle,
// counting past 360° instead of wrapping, shown as stacked "lap" rings.

const dial = document.getElementById("duration-dial");
const dialHandle = document.getElementById("dial-handle");
const dialLapCircles = [
  document.getElementById("dial-lap-0"),
  document.getElementById("dial-lap-1"),
  document.getElementById("dial-lap-2"),
  document.getElementById("dial-lap-3"),
];
const dialValue = document.getElementById("dial-value");
const dialEndTime = document.getElementById("dial-end-time");

const DIAL_VIEWBOX_SIZE = 200; // must match the SVG viewBox in index.html
const DIAL_RING_RADIUS = 80; // must match the lap circles' r="" in index.html
const DIAL_DEAD_ZONE_RADIUS = 20; // viewBox units around the center that count as "unlimited"
const DIAL_MINUTES_PER_LAP = 60; // one full 360° turn = 1 hour
const DIAL_MAX_LAPS = dialLapCircles.length; // UI ceiling; backend cap is VentilationCommandPlanner.MAX_DURATION_MINUTES
const DIAL_MAX_DEGREES = DIAL_MAX_LAPS * 360;
const DIAL_MAX_MINUTES = DIAL_MAX_LAPS * DIAL_MINUTES_PER_LAP;

let dialState = { minutes: 0, cumulativeDegrees: 0, rawAngleDegrees: null };
let selectedDurationMinutes = 0; // kept in sync with dialState.minutes

/**
 * Advances the dial state by one pointer sample (dx, dy: offsets from the
 * dial's center). Tracks a cumulative angle -- advancing by the shortest
 * signed delta from the previous sample -- instead of a single instantaneous
 * angle, so turning past 360° keeps counting instead of wrapping back to a
 * low value. Pure function, so this can be sanity-checked with a plain
 * `node` script simulating a drag.
 */
function advanceDialState(previousState, dx, dy) {
  const distance = Math.hypot(dx, dy);
  if (distance < DIAL_DEAD_ZONE_RADIUS) {
    return { minutes: 0, cumulativeDegrees: 0, rawAngleDegrees: null };
  }

  // atan2(dx, -dy) measures degrees clockwise from 12 o'clock (vs. the
  // standard atan2(y, x), counterclockwise from 3 o'clock).
  const rawAngleDegrees = (Math.atan2(dx, -dy) * (180 / Math.PI) + 360) % 360;

  let cumulativeDegrees;
  if (previousState.rawAngleDegrees === null) {
    cumulativeDegrees = rawAngleDegrees;
  } else {
    let delta = rawAngleDegrees - previousState.rawAngleDegrees;
    if (delta > 180) delta -= 360;
    if (delta <= -180) delta += 360;
    cumulativeDegrees = previousState.cumulativeDegrees + delta;
  }
  cumulativeDegrees = Math.min(Math.max(cumulativeDegrees, 0), DIAL_MAX_DEGREES);

  const minutes = Math.max(1, Math.round((cumulativeDegrees / 360) * DIAL_MINUTES_PER_LAP));
  return { minutes, cumulativeDegrees, rawAngleDegrees };
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

  dialState = advanceDialState(dialState, dx, dy);
  selectedDurationMinutes = dialState.minutes;
  renderDial(dialState);
}

dial.addEventListener("pointerdown", (event) => {
  // setPointerCapture keeps events targeting `dial` even if the pointer moves outside it mid-drag.
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

function renderDial(state) {
  dial.setAttribute("aria-valuenow", String(state.minutes));

  if (state.minutes === 0) {
    // Handle rests on the ring (12 o'clock) rather than the exact center, so it reads as draggable.
    dialHandle.setAttribute("cx", "100");
    dialHandle.setAttribute("cy", String(100 - DIAL_RING_RADIUS));
    dialValue.textContent = "∞";
    hide(dialEndTime);
    dialLapCircles.forEach((circle) => setLapProgress(circle, 0));
    return;
  }

  const totalDegrees = Math.min(
    (state.minutes / DIAL_MINUTES_PER_LAP) * 360,
    DIAL_MAX_DEGREES
  );

  // A completed lap renders as a full circle; the active lap as a partial
  // arc on top of it; later laps stay invisible.
  dialLapCircles.forEach((circle, lapIndex) => {
    const degreesIntoThisLap = Math.min(Math.max(totalDegrees - lapIndex * 360, 0), 360);
    setLapProgress(circle, degreesIntoThisLap / 360);
  });

  const activeLapIndex = Math.min(Math.floor(totalDegrees / 360), DIAL_MAX_LAPS - 1);
  const angleWithinLap = totalDegrees - activeLapIndex * 360;
  const angleRadians = (angleWithinLap / 360) * 2 * Math.PI; // 0 at the top, growing clockwise
  dialHandle.setAttribute("cx", String(100 + DIAL_RING_RADIUS * Math.sin(angleRadians)));
  dialHandle.setAttribute("cy", String(100 - DIAL_RING_RADIUS * Math.cos(angleRadians)));

  dialValue.textContent = formatDuration(state.minutes);
  dialEndTime.textContent = `Ends at ${formatEndTime(state.minutes)}`;
  show(dialEndTime);
}

function formatEndTime(minutes) {
  const endTime = new Date(Date.now() + minutes * 60_000);
  return endTime.toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" });
}

// stroke-dasharray = full circumference, stroke-dashoffset shifts how much of it is hidden.
function setLapProgress(circle, fraction) {
  const circumference = 2 * Math.PI * DIAL_RING_RADIUS;
  circle.style.strokeDasharray = `${circumference}`;
  circle.style.strokeDashoffset = `${circumference * (1 - fraction)}`;
}

function formatDuration(minutes) {
  if (minutes < 60) {
    return `${minutes}m`;
  }
  const hours = Math.floor(minutes / 60);
  const remainderMinutes = minutes % 60;
  return remainderMinutes === 0 ? `${hours}h` : `${hours}h ${remainderMinutes}m`;
}

renderDial(dialState);

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
    showFeedback(`Set to: ${LEVEL_PERCENTAGES[selectedLevel]}%`, "success");
    dialState = { minutes: 0, cumulativeDegrees: 0, rawAngleDegrees: null }; // reset the dial for the next choice
    selectedDurationMinutes = 0;
    renderDial(dialState);
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

  setText("temp-outside", formatTemp(status.tempOutsideCelsius));
  setText("temp-extract", formatTemp(status.tempExtractCelsius));
  setText("humidity", `${status.humidityPercent}%`);
  setText("filter-days", `${status.filterDaysRemaining} days`);
  setText("bypass", formatBypass(status.bypassActive));
  setText("current-state", formatCurrentState(status));
}

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

refreshStatus();
setInterval(refreshStatus, 30_000);
