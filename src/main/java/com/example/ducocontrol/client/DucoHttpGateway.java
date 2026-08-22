package com.example.ducocontrol.client;

import java.io.IOException;

/**
 * Transport seam between DucoClient and the network, so tests can substitute
 * a fake implementation instead of making real HTTP calls.
 */
public interface DucoHttpGateway {

    String get(String path) throws IOException;

    String post(String path, String jsonBody) throws IOException;
}
