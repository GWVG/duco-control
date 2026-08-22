package com.example.ducocontrol.client;

import com.example.ducocontrol.exception.DucoInvalidStateException;
import com.example.ducocontrol.model.SystemStatus;
import com.example.ducocontrol.model.VentilationState;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Translates the raw JSON from GET /v2/info and GET /v2/info/nodes into a SystemStatus. */
public final class DucoResponseParser {

    private DucoResponseParser() {
    }

    /**
     * @param infoJson  raw body of GET /v2/info
     * @param nodesJson raw body of GET /v2/info/nodes
     * @param nodeId    the node ID to treat as the ventilation zone to control
     */
    public static SystemStatus parse(String infoJson, String nodesJson, int nodeId) {
        try {
            JSONObject info = new JSONObject(infoJson);
            String deviceModel = extractDeviceModel(info);
            JSONObject sensor = info.getJSONObject("Ventilation").getJSONObject("Sensor");
            double tempOutside = tenthsToCelsius(sensor.getJSONObject("TempOda").getInt("Val"));
            double tempSupply = tenthsToCelsius(sensor.getJSONObject("TempSup").getInt("Val"));
            double tempExtract = tenthsToCelsius(sensor.getJSONObject("TempEta").getInt("Val"));
            double tempExhaust = tenthsToCelsius(sensor.getJSONObject("TempEha").getInt("Val"));
            int filterDaysRemaining = info.getJSONObject("HeatRecovery")
                    .getJSONObject("General")
                    .getJSONObject("TimeFilterRemain")
                    .getInt("Val");

            JSONObject node = findNode(new JSONObject(nodesJson).getJSONArray("Nodes"), nodeId);
            JSONObject ventilation = node.getJSONObject("Ventilation");
            String currentStateVal = ventilation.getJSONObject("State").getString("Val");
            String currentMode = ventilation.getJSONObject("Mode").getString("Val");
            int humidityPercent = node.getJSONObject("Sensor").getJSONObject("Rh").getInt("Val");

            return new SystemStatus(
                    deviceModel,
                    tempOutside,
                    tempSupply,
                    tempExtract,
                    tempExhaust,
                    filterDaysRemaining,
                    null, // bypassActive: not exposed by this device
                    humidityPercent,
                    currentStateVal,
                    currentMode,
                    currentPercentageFor(currentStateVal));
        } catch (JSONException e) {
            throw new DucoInvalidStateException("Unexpected response format from the DucoBox", e);
        }
    }

    /** Reads General.Board.BoxSubTypeName; null if absent so a missing field doesn't fail the whole response. */
    private static String extractDeviceModel(JSONObject info) {
        try {
            return info.getJSONObject("General").getJSONObject("Board")
                    .getJSONObject("BoxSubTypeName").getString("Val");
        } catch (JSONException notFound) {
            return null;
        }
    }

    private static JSONObject findNode(JSONArray nodes, int nodeId) {
        for (int i = 0; i < nodes.length(); i++) {
            JSONObject node = nodes.getJSONObject(i);
            if (node.getInt("Node") == nodeId) {
                return node;
            }
        }
        throw new DucoInvalidStateException(
                "Node " + nodeId + " not found in the /v2/info/nodes response");
    }

    /** Percentage for the current state, or null if it isn't manually settable (typically AUTO). */
    private static Integer currentPercentageFor(String stateVal) {
        try {
            return VentilationState.fromVal(stateVal).percentage();
        } catch (DucoInvalidStateException notAManualState) {
            return null;
        }
    }

    private static double tenthsToCelsius(int tenthsOfDegree) {
        return tenthsOfDegree / 10.0;
    }
}
