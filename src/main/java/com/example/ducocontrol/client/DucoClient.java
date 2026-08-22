package com.example.ducocontrol.client;

import com.example.ducocontrol.exception.DucoInvalidStateException;
import com.example.ducocontrol.exception.DucoUnreachableException;
import com.example.ducocontrol.model.SystemStatus;
import com.example.ducocontrol.model.VentilationCommand;
import org.json.JSONObject;

import java.io.IOException;

/**
 * Main entry point to the DucoBox: combines DucoHttpGateway (transport) and
 * DucoResponseParser (JSON -> domain object), and translates IOException
 * into DucoUnreachableException.
 */
public class DucoClient {

    private final DucoHttpGateway gateway;
    private final int nodeId;

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
            // HTTP 200 doesn't guarantee Duco accepted the command.
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
