package com.example.ducocontrol.client;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Hand-written test double for DucoHttpGateway: no real network traffic,
 * just pre-configured responses per path, or an exception to simulate a
 * network failure.
 *
 * Why write this by hand instead of using Mockito? DucoHttpGateway only has
 * two methods, and the behavior we want to mimic is simple ("return this
 * string" / "throw this IOException"). A hand-written fake is then about as
 * little code as a Mockito `when(...).thenReturn(...)` setup, but reads as
 * plain Java instead of a mocking DSL -- a good exercise in seeing what a
 * mocking framework actually automates for you. Mockito would earn its
 * keep more on interfaces with many methods/calls, or when you need to
 * precisely verify how often something was called.
 */
class FakeDucoHttpGateway implements DucoHttpGateway {

    private final Map<String, String> getResponses = new HashMap<>();
    private final Map<String, String> postResponses = new HashMap<>();
    private IOException failureToThrow;

    private String lastPostPath;
    private String lastPostBody;

    void respondToGet(String path, String responseBody) {
        getResponses.put(path, responseBody);
    }

    void respondToPost(String path, String responseBody) {
        postResponses.put(path, responseBody);
    }

    /** Every subsequent get()/post() call throws this exception instead of returning a response. */
    void failWith(IOException exception) {
        this.failureToThrow = exception;
    }

    String lastPostPath() {
        return lastPostPath;
    }

    String lastPostBody() {
        return lastPostBody;
    }

    @Override
    public String get(String path) throws IOException {
        if (failureToThrow != null) {
            throw failureToThrow;
        }
        String response = getResponses.get(path);
        if (response == null) {
            throw new IllegalStateException("Test did not configure a GET response for path: " + path);
        }
        return response;
    }

    @Override
    public String post(String path, String jsonBody) throws IOException {
        if (failureToThrow != null) {
            throw failureToThrow;
        }
        this.lastPostPath = path;
        this.lastPostBody = jsonBody;
        String response = postResponses.get(path);
        if (response == null) {
            throw new IllegalStateException("Test did not configure a POST response for path: " + path);
        }
        return response;
    }
}
