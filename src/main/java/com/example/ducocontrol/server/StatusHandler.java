package com.example.ducocontrol.server;

import com.example.ducocontrol.client.DucoClient;
import com.example.ducocontrol.exception.DucoUnreachableException;
import com.example.ducocontrol.model.SystemStatus;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.json.JSONObject;

import java.io.IOException;

/** GET /status: fetches SystemStatus via DucoClient and returns it as JSON. */
public class StatusHandler implements HttpHandler {

    private final DucoClient ducoClient;

    public StatusHandler(DucoClient ducoClient) {
        this.ducoClient = ducoClient;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            HttpExchangeSupport.writeErrorResponse(exchange, 405, "Only GET is allowed on /status");
            return;
        }

        try {
            SystemStatus status = ducoClient.fetchStatus();
            HttpExchangeSupport.writeJsonResponse(exchange, 200, toJson(status));
        } catch (DucoUnreachableException e) {
            HttpExchangeSupport.writeErrorResponse(exchange, 502, "DucoBox unreachable: " + e.getMessage());
        }
    }

    private static String toJson(SystemStatus status) {
        return new JSONObject()
                .put("deviceModel", orJsonNull(status.deviceModel()))
                .put("tempOutsideCelsius", status.tempOutsideCelsius())
                .put("tempSupplyCelsius", status.tempSupplyCelsius())
                .put("tempExtractCelsius", status.tempExtractCelsius())
                .put("tempExhaustCelsius", status.tempExhaustCelsius())
                .put("filterDaysRemaining", status.filterDaysRemaining())
                .put("bypassActive", orJsonNull(status.bypassActive()))
                .put("humidityPercent", status.humidityPercent())
                .put("currentStateVal", status.currentStateVal())
                .put("currentMode", status.currentMode())
                .put("currentPercentage", orJsonNull(status.currentPercentage()))
                .toString();
    }

    /** JSONObject.put(key, null) drops the key entirely; JSONObject.NULL is what actually serializes to JSON null. */
    private static Object orJsonNull(Object value) {
        return value == null ? JSONObject.NULL : value;
    }
}
