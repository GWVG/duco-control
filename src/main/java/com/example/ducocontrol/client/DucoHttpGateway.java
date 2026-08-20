package com.example.ducocontrol.client;

import java.io.IOException;

/**
 * The seam between DucoClient and the actual network. DucoClient only knows
 * this interface, not how an HTTP call actually happens. That buys two
 * things:
 *  1. Testability: in tests we pass in a FakeDucoHttpGateway that returns
 *     canned responses (or throws an IOException) without any real network
 *     packet ever being sent.
 *  2. Replaceability: if we ever wanted to move from java.net.http.HttpClient
 *     to something else (e.g. a test tool that records HTTP traffic), only
 *     the implementation changes, not DucoClient.
 *
 * "throws IOException" (checked) is deliberate: this is the layer closest
 * to the raw network I/O, and checked exceptions are actually appropriate
 * here -- they force DucoClient to explicitly decide what a failed call
 * means (in this project: translate it to DucoUnreachableException),
 * instead of a forgotten catch silently swallowing a network error.
 */
public interface DucoHttpGateway {

    String get(String path) throws IOException;

    String post(String path, String jsonBody) throws IOException;
}
