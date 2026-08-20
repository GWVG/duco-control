package com.example.ducocontrol.server;

import com.example.ducocontrol.client.DucoClient;
import com.example.ducocontrol.exception.DucoUnreachableException;
import com.example.ducocontrol.model.SystemStatus;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.json.JSONObject;

import java.io.IOException;

/**
 * GET /status: fetches SystemStatus via DucoClient and returns it as JSON.
 * This is the replacement for the info spread across two screens in the
 * official DucoHome app.
 *
 * Implements HttpHandler -- the only method that interface requires is
 * handle(HttpExchange), called by com.sun.net.httpserver.HttpServer
 * whenever a request comes in on the path this handler is registered for
 * (see DucoControlApp).
 */
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
            // 502 Bad Gateway: the correct HTTP status when WE are working
            // fine, but the "upstream" server (the DucoBox) is unreachable.
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
                // Note: JSONObject.put(key, null) just removes the key
                // (tested: {"x": null} becomes {}), instead of turning it
                // into JSON null. JSONObject.NULL is the sentinel value
                // org.json expects for that.
                .put("bypassActive", orJsonNull(status.bypassActive()))
                .put("humidityPercent", status.humidityPercent())
                .put("currentStateVal", status.currentStateVal())
                .put("currentMode", status.currentMode())
                .put("currentPercentage", orJsonNull(status.currentPercentage()))
                .toString();
    }

    private static Object orJsonNull(Object value) {
        return value == null ? JSONObject.NULL : value;
    }
}
