package com.example.ducocontrol.server;

import com.example.ducocontrol.client.AutoRevertScheduler;
import com.example.ducocontrol.client.DucoClient;
import com.example.ducocontrol.exception.DucoInvalidStateException;
import com.example.ducocontrol.exception.DucoUnreachableException;
import com.example.ducocontrol.model.VentilationCommand;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.json.JSONObject;

import java.io.IOException;

/** POST /auto: sends the box back to its own automatic control loop. No request body. */
public class SetAutoHandler implements HttpHandler {

    private final DucoClient ducoClient;
    private final AutoRevertScheduler revertScheduler;

    public SetAutoHandler(DucoClient ducoClient, AutoRevertScheduler revertScheduler) {
        this.ducoClient = ducoClient;
        this.revertScheduler = revertScheduler;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            HttpExchangeSupport.writeErrorResponse(exchange, 405, "Only POST is allowed on /auto");
            return;
        }

        try {
            ducoClient.setVentilationState(new VentilationCommand("AUTO", false, 0));
            // A pending scheduled revert would just resend "AUTO" later --
            // harmless, but no longer needed now that we've already done it.
            revertScheduler.cancelPending();

            HttpExchangeSupport.writeJsonResponse(exchange, 200, new JSONObject()
                    .put("success", true)
                    .put("val", "AUTO")
                    .toString());
        } catch (DucoInvalidStateException invalidState) {
            HttpExchangeSupport.writeErrorResponse(exchange, 400, invalidState.getMessage());
        } catch (DucoUnreachableException unreachable) {
            HttpExchangeSupport.writeErrorResponse(exchange, 502,
                    "DucoBox unreachable: " + unreachable.getMessage());
        }
    }
}
