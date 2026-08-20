package com.example.ducocontrol.client;

import com.example.ducocontrol.exception.DucoInvalidStateException;
import com.example.ducocontrol.exception.DucoUnreachableException;
import com.example.ducocontrol.model.SystemStatus;
import com.example.ducocontrol.model.VentilationCommand;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Written before DucoClient existed (TDD red phase).
class DucoClientTest {

    private static final int NODE_ID = 1;

    private static final String INFO_JSON = """
            {
              "Ventilation": {"Sensor": {
                "TempOda": {"Val": 173}, "TempSup": {"Val": 212},
                "TempEta": {"Val": 219}, "TempEha": {"Val": 186}
              }},
              "HeatRecovery": {"General": {"TimeFilterRemain": {"Val": 105}}}
            }
            """;

    private static final String NODES_JSON = """
            {
              "Nodes": [
                {"Node": 1, "Ventilation": {
                    "State": {"Val": "CNT1"}, "Mode": {"Val": "MANU"}, "FlowLvlTgt": {"Val": 15}
                  }, "Sensor": {"Rh": {"Val": 72}, "IaqRh": {"Val": 55}}}
              ]
            }
            """;

    @Test
    void fetchStatus_withValidResponses_returnsParsedSystemStatus() {
        FakeDucoHttpGateway gateway = new FakeDucoHttpGateway();
        gateway.respondToGet("/v2/info", INFO_JSON);
        gateway.respondToGet("/v2/info/nodes", NODES_JSON);
        DucoClient client = new DucoClient(gateway, NODE_ID);

        SystemStatus status = client.fetchStatus();

        assertEquals(17.3, status.tempOutsideCelsius(), 0.001);
        assertEquals(15, status.currentPercentage());
    }

    // The core of this exercise: the gateway throws a "bare" IOException
    // (this is what a real timeout or connection refusal would look like
    // too), and DucoClient must translate that into our own, meaningful type.
    @Test
    void fetchStatus_whenGatewayThrowsIOException_throwsDucoUnreachableException() {
        FakeDucoHttpGateway gateway = new FakeDucoHttpGateway();
        gateway.failWith(new IOException("Connection refused"));
        DucoClient client = new DucoClient(gateway, NODE_ID);

        assertThrows(DucoUnreachableException.class, client::fetchStatus);
    }

    @Test
    void setVentilationState_postsActionAndValToCorrectNodePath() {
        FakeDucoHttpGateway gateway = new FakeDucoHttpGateway();
        gateway.respondToPost("/v2/action/nodes/1", "{\"Code\":0,\"Result\":\"SUCCESS\"}");
        DucoClient client = new DucoClient(gateway, NODE_ID);

        client.setVentilationState(new VentilationCommand("CNT2", false, 0));

        assertEquals("/v2/action/nodes/1", gateway.lastPostPath());
        assertTrue(gateway.lastPostBody().contains("\"Val\":\"CNT2\""));
        assertTrue(gateway.lastPostBody().contains("\"Action\":\"SetVentilationState\""));
    }

    @Test
    void setVentilationState_whenGatewayThrowsIOException_throwsDucoUnreachableException() {
        FakeDucoHttpGateway gateway = new FakeDucoHttpGateway();
        gateway.failWith(new IOException("timeout"));
        DucoClient client = new DucoClient(gateway, NODE_ID);

        assertThrows(DucoUnreachableException.class,
                () -> client.setVentilationState(new VentilationCommand("CNT2", false, 0)));
    }

    // Duco's own API responds with {"Code":4,"Result":"FAILED"} on a
    // rejected command (this is exactly what we saw live when we sent an
    // unsupported "Time" field). DucoClient must recognize that instead of
    // silently assuming "success".
    @Test
    void setVentilationState_whenDucoReportsFailure_throwsDucoInvalidStateException() {
        FakeDucoHttpGateway gateway = new FakeDucoHttpGateway();
        gateway.respondToPost("/v2/action/nodes/1", "{\"Code\":4,\"Result\":\"FAILED\"}");
        DucoClient client = new DucoClient(gateway, NODE_ID);

        assertThrows(DucoInvalidStateException.class,
                () -> client.setVentilationState(new VentilationCommand("CNT2", false, 0)));
    }
}
