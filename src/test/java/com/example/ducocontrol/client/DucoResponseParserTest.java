package com.example.ducocontrol.client;

import com.example.ducocontrol.exception.DucoInvalidStateException;
import com.example.ducocontrol.model.SystemStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

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

    @Test
    void parse_withoutModelField_deviceModelIsNull() {
        SystemStatus status = DucoResponseParser.parse(INFO_JSON_WITHOUT_MODEL, NODES_JSON, 1);
        assertNull(status.deviceModel());
    }

    @Test
    void parse_withUnknownNodeId_throwsDucoInvalidStateException() {
        assertThrows(DucoInvalidStateException.class,
                () -> DucoResponseParser.parse(INFO_JSON, NODES_JSON, 999));
    }

    @Test
    void parse_withMalformedJson_throwsDucoInvalidStateException() {
        assertThrows(DucoInvalidStateException.class,
                () -> DucoResponseParser.parse("{ this is not valid JSON", NODES_JSON, 1));
    }
}
