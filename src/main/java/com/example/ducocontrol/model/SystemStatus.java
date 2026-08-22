package com.example.ducocontrol.model;

/**
 * Combined response for GET /status: system info plus per-node sensors in
 * one overview.
 *
 * @param deviceModel         model identifier (General.Board.BoxSubTypeName), or null if absent
 * @param tempOutsideCelsius  outdoor air temperature
 * @param tempSupplyCelsius   air supplied to the house
 * @param tempExtractCelsius  air extracted from the house
 * @param tempExhaustCelsius  air exhausted outside
 * @param filterDaysRemaining days until filter replacement
 * @param bypassActive        whether the summer bypass is active, or null if this device doesn't expose that field
 * @param humidityPercent     relative humidity at the controlled node
 * @param currentStateVal     the raw "Val" the box currently reports (e.g. "CNT1" or "AUTO")
 * @param currentMode         "MANU" (manually set) or "AUTO"
 * @param currentPercentage   percentage for currentStateVal, or null if not a manually settable state
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
