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
 * POST /set: receives {"level": 1|2|3, "durationMinutes": ...} from the
 * frontend (a discrete level instead of a free percentage -- see
 * VentilationState for why), lets VentilationCommandPlanner decide which
 * Duco command that becomes, sends it, and schedules a revert to AUTO
 * itself if needed (for 1h/2h -- see VentilationCommandPlanner/AutoRevertScheduler).
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
            // Every new /set request invalidates a previously scheduled
            // 1h/2h revert (the user just picked something else) --
            // cancel it, even if this new request doesn't need its own revert.
            revertScheduler.cancelPending();
        }
    }
}
