package com.example.ducocontrol.client;

import com.example.ducocontrol.exception.DucoInvalidStateException;
import com.example.ducocontrol.exception.DucoUnreachableException;
import com.example.ducocontrol.model.SystemStatus;
import com.example.ducocontrol.model.VentilationCommand;
import org.json.JSONObject;

import java.io.IOException;

/**
 * The main entry point to the DucoBox from the rest of the application:
 * combines DucoHttpGateway (transport) and DucoResponseParser (JSON ->
 * domain object), and is the only place that turns IOException into our own
 * DucoUnreachableException. Everything above this level (the HTTP handlers)
 * never has to deal with java.io.IOException.
 */
public class DucoClient {

    private final DucoHttpGateway gateway;
    private final int nodeId;

    /**
     * @param nodeId the node ID we treat as THE ventilation zone to control
     *               (for this system: 1, the DucoBox itself -- see the node
     *               discovery we did upfront with GET /v2/info/nodes)
     */
    public DucoClient(DucoHttpGateway gateway, int nodeId) {
        this.gateway = gateway;
        this.nodeId = nodeId;
    }

    public SystemStatus fetchStatus() {
        String infoJson = get("/v2/info");
        String nodesJson = get("/v2/info/nodes");
        return DucoResponseParser.parse(infoJson, nodesJson, nodeId);
    }

    public void setVentilationState(VentilationCommand command) {
        String requestBody = new JSONObject()
                .put("Action", "SetVentilationState")
                .put("Val", command.val())
                .toString();

        String responseBody = post("/v2/action/nodes/" + nodeId, requestBody);

        JSONObject response = new JSONObject(responseBody);
        if (!"SUCCESS".equals(response.optString("Result"))) {
            // This is exactly how the DucoBox responded when we sent an
            // unsupported field: {"Code":4,"Result":"FAILED"}. We don't
            // blindly trust "the POST succeeded" (HTTP 200) -- Duco can
            // return 200 with a substantive rejection.
            throw new DucoInvalidStateException(
                    "DucoBox rejected the command: " + responseBody);
        }
    }

    private String get(String path) {
        try {
            return gateway.get(path);
        } catch (IOException e) {
            throw new DucoUnreachableException("Could not reach DucoBox via " + path, e);
        }
    }

    private String post(String path, String jsonBody) {
        try {
            return gateway.post(path, jsonBody);
        } catch (IOException e) {
            throw new DucoUnreachableException("Could not reach DucoBox via " + path, e);
        }
    }
}
