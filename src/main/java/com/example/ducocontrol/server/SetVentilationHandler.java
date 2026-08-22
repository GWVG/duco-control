package com.example.ducocontrol.server;

import com.example.ducocontrol.client.AutoRevertScheduler;
import com.example.ducocontrol.client.DucoClient;
import com.example.ducocontrol.exception.DucoInvalidStateException;
import com.example.ducocontrol.exception.DucoUnreachableException;
import com.example.ducocontrol.model.VentilationCommand;
import com.example.ducocontrol.model.VentilationCommandPlanner;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * POST /set: receives {"level": 1|2|3, "durationMinutes": ...}, sends the
 * resulting Duco command, and schedules a revert to AUTO if needed.
 */
public class SetVentilationHandler implements HttpHandler {

    private final DucoClient ducoClient;
    private final AutoRevertScheduler revertScheduler;

    public SetVentilationHandler(DucoClient ducoClient, AutoRevertScheduler revertScheduler) {
        this.ducoClient = ducoClient;
        this.revertScheduler = revertScheduler;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            HttpExchangeSupport.writeErrorResponse(exchange, 405, "Only POST is allowed on /set");
            return;
        }

        try {
            JSONObject requestBody = new JSONObject(HttpExchangeSupport.readRequestBodyAsString(exchange));
            int level = requestBody.getInt("level");
            int durationMinutes = requestBody.optInt(
                    "durationMinutes", VentilationCommandPlanner.UNLIMITED_DURATION_MINUTES);

            VentilationCommand command = VentilationCommandPlanner.plan(level, durationMinutes);
            ducoClient.setVentilationState(command);
            updateScheduledRevert(command);

            HttpExchangeSupport.writeJsonResponse(exchange, 200, new JSONObject()
                    .put("success", true)
                    .put("val", command.val())
                    .toString());
        } catch (JSONException malformedRequest) {
            HttpExchangeSupport.writeErrorResponse(exchange, 400,
                    "Invalid or incomplete request: " + malformedRequest.getMessage());
        } catch (DucoInvalidStateException invalidState) {
            HttpExchangeSupport.writeErrorResponse(exchange, 400, invalidState.getMessage());
        } catch (DucoUnreachableException unreachable) {
            HttpExchangeSupport.writeErrorResponse(exchange, 502,
                    "DucoBox unreachable: " + unreachable.getMessage());
        }
    }

    private void updateScheduledRevert(VentilationCommand command) {
        if (command.requiresScheduledRevert()) {
            revertScheduler.scheduleRevert(
                    () -> ducoClient.setVentilationState(new VentilationCommand("AUTO", false, 0)),
                    command.revertAfterMinutes(),
                    TimeUnit.MINUTES);
        } else {
            // A new request without its own revert still invalidates any previously scheduled one.
            revertScheduler.cancelPending();
        }
    }
}
