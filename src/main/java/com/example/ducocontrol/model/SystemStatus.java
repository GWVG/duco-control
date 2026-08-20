package com.example.ducocontrol.model;

/**
 * The combined response of our own GET /status endpoint: merges what the
 * official Duco app spreads across two screens (system info + per-node
 * sensors) into one overview.
 *
 * @param deviceModel         the device's model identifier (General.Board.BoxSubTypeName
 *                             in /v2/info, e.g. "COMFORT_D400_R"), or NULL if that field
 *                             is absent on this device/firmware version. Read dynamically
 *                             instead of hardcoded so the same build works across Duco
 *                             models -- the frontend falls back to a generic "Duco
 *                             Control" title when null.
 * @param tempOutsideCelsius  outdoor air temperature (TempOda in /v2/info, divided by 10 -- Duco reports temperatures in tenths of a degree)
 * @param tempSupplyCelsius   air supplied to the house (TempSup)
 * @param tempExtractCelsius  air extracted from the house (TempEta)
 * @param tempExhaustCelsius  air exhausted outside (TempEha)
 * @param filterDaysRemaining days until filter replacement (HeatRecovery.General.TimeFilterRemain)
 * @param bypassActive        whether the summer bypass is active. NULL: exhaustively
 *                             re-verified by grepping full raw dumps of /v2/info AND
 *                             /v2/info/nodes for "bypass" (case-insensitive) -- zero
 *                             matches on this device (COMFORT_D400_R). Bypass support
 *                             appears to depend on Duco model/firmware; the frontend
 *                             shows "Not available on this device" when null. If your
 *                             device DOES expose a bypass field, extend
 *                             DucoResponseParser to read it.
 * @param humidityPercent     relative humidity, read from the sensor on the controlled node
 * @param currentStateVal     the raw "Val" the box currently reports (e.g. "CNT1" or "AUTO")
 * @param currentMode         "MANU" (manually set) or "AUTO" (system controls itself)
 * @param currentPercentage   the percentage for currentStateVal, or NULL if the box is in a
 *                             state that isn't in VentilationState (typically: AUTO)
 */
public record SystemStatus(
        String deviceModel,
        double tempOutsideCelsius,
        double tempSupplyCelsius,
        double tempExtractCelsius,
        double tempExhaustCelsius,
        int filterDaysRemaining,
        Boolean bypassActive,
        int humidityPercent,
        String currentStateVal,
        String currentMode,
        Integer currentPercentage) {
}
