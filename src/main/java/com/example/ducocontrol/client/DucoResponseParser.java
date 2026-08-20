package com.example.ducocontrol.client;

import com.example.ducocontrol.exception.DucoInvalidStateException;
import com.example.ducocontrol.model.SystemStatus;
import com.example.ducocontrol.model.VentilationState;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Translates the raw JSON responses from GET /v2/info and GET /v2/info/nodes
 * into our own SystemStatus object. Deliberately a pure function with no
 * HTTP knowledge: takes strings in, returns a domain object, never throws a
 * network error (that's DucoHttpGateway/DucoClient's job). That also makes
 * this the easiest layer to test -- just JSON strings in, no server needed.
 */
public final class DucoResponseParser {

    private DucoResponseParser() {
    }

    /**
     * @param infoJson  raw body of GET /v2/info
     * @param nodesJson raw body of GET /v2/info/nodes
     * @param nodeId    the node ID we treat as "the" ventilation zone (for us: 1, the box itself)
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
                    null, // bypassActive: not found on this device, see SystemStatus javadoc
                    humidityPercent,
                    currentStateVal,
                    currentMode,
                    currentPercentageFor(currentStateVal));
        } catch (JSONException e) {
            // org.json.JSONException is a generic, unchecked exception from
            // the library -- we translate it here to our own
            // DucoInvalidStateException so the HTTP layer further down only
            // has to know one type of Duco error, regardless of whether the
            // problem was "invalid percentage" or "unexpected JSON shape".
            throw new DucoInvalidStateException("Unexpected response format from the DucoBox", e);
        }
    }

    /**
     * The device's model identifier (General.Board.BoxSubTypeName), or null
     * if that field is missing -- read defensively (catch instead of a
     * required getter) because unlike the other fields we parse, we don't
     * yet know this is stable across every Duco model/firmware version, and
     * a missing model name shouldn't take down the whole /status response.
     */
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

    /**
     * The percentage for the current state, or null if the box is in a
     * state that isn't manually settable (typically: AUTO). We deliberately
     * catch DucoInvalidStateException here from VentilationState.fromVal()
     * -- that's not an error in this case, just a sign that there's no
     * fixed percentage to show.
     */
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
