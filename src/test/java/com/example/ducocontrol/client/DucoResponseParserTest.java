package com.example.ducocontrol.client;

import com.example.ducocontrol.exception.DucoInvalidStateException;
import com.example.ducocontrol.model.SystemStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

// Written before DucoResponseParser existed (TDD red phase). The JSON below
// isn't a made-up example -- it's literally what GET /v2/info and GET
// /v2/info/nodes returned when we tested this live against the DucoBox
// (2026-08-20), trimmed down to what the parser needs. That's more valuable
// as a test fixture than hand-written JSON: if Duco ever ships a firmware
// update that changes the structure, you want this test to still be
// grounded in real data.
class DucoResponseParserTest {

    private static final String INFO_JSON = """
            {
              "General": {
                "Board": {
                  "BoxSubTypeName": {"Val": "COMFORT_D400_R"}
                }
              },
              "Ventilation": {
                "Sensor": {
                  "TempOda": {"Val": 173},
                  "TempSup": {"Val": 212},
                  "TempEta": {"Val": 219},
                  "TempEha": {"Val": 186}
                }
              },
              "HeatRecovery": {
                "General": {
                  "TimeFilterRemain": {"Val": 105}
                }
              }
            }
            """;

    private static final String INFO_JSON_WITHOUT_MODEL = """
            {
              "Ventilation": {
                "Sensor": {
                  "TempOda": {"Val": 173},
                  "TempSup": {"Val": 212},
                  "TempEta": {"Val": 219},
                  "TempEha": {"Val": 186}
                }
              },
              "HeatRecovery": {
                "General": {
                  "TimeFilterRemain": {"Val": 105}
                }
              }
            }
            """;

    private static final String NODES_JSON = """
            {
              "Nodes": [
                {
                  "Node": 1,
                  "Ventilation": {
                    "State": {"Val": "CNT1"},
                    "Mode": {"Val": "MANU"},
                    "FlowLvlTgt": {"Val": 15}
                  },
                  "Sensor": {"Rh": {"Val": 72}, "IaqRh": {"Val": 55}}
                },
                {
                  "Node": 58,
                  "Ventilation": {
                    "State": {"Val": "CNT1"},
                    "Mode": {"Val": "-"}
                  },
                  "Sensor": {"Rh": {"Val": 72}, "IaqRh": {"Val": 55}}
                }
              ]
            }
            """;

    @Test
    void parse_combinesInfoAndNodes_intoSystemStatus() {
        SystemStatus status = DucoResponseParser.parse(INFO_JSON, NODES_JSON, 1);

        // Temperatures come in as tenths of a degree (173 -> 17.3 degrees).
        assertEquals(17.3, status.tempOutsideCelsius(), 0.001);
        assertEquals(21.2, status.tempSupplyCelsius(), 0.001);
        assertEquals(21.9, status.tempExtractCelsius(), 0.001);
        assertEquals(18.6, status.tempExhaustCelsius(), 0.001);
        assertEquals(105, status.filterDaysRemaining());
        assertEquals(72, status.humidityPercent());
        assertEquals("CNT1", status.currentStateVal());
        assertEquals("MANU", status.currentMode());
        assertEquals(15, status.currentPercentage());
        assertNull(status.bypassActive());
        assertEquals("COMFORT_D400_R", status.deviceModel());
    }

    // BoxSubTypeName's presence/format isn't guaranteed across every Duco
    // model or firmware version -- a missing model name should degrade to
    // null, not break the whole /status response (see extractDeviceModel).
    @Test
    void parse_withoutModelField_deviceModelIsNull() {
        SystemStatus status = DucoResponseParser.parse(INFO_JSON_WITHOUT_MODEL, NODES_JSON, 1);
        assertNull(status.deviceModel());
    }

    // If the given node ID doesn't appear in the /v2/info/nodes response,
    // that's an error the caller should see immediately -- not a silently
    // returned empty/half-filled SystemStatus.
    @Test
    void parse_withUnknownNodeId_throwsDucoInvalidStateException() {
        assertThrows(DucoInvalidStateException.class,
                () -> DucoResponseParser.parse(INFO_JSON, NODES_JSON, 999));
    }

    // Broken/unexpected JSON (e.g. after a firmware update renames a field)
    // should also produce a DucoInvalidStateException, not a raw
    // org.json.JSONException the HTTP handler doesn't know about.
    @Test
    void parse_withMalformedJson_throwsDucoInvalidStateException() {
        assertThrows(DucoInvalidStateException.class,
                () -> DucoResponseParser.parse("{ this is not valid JSON", NODES_JSON, 1));
    }
}
