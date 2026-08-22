package com.example.ducocontrol.client;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/** Hand-written test double for DucoHttpGateway: canned responses per path, or a thrown exception. */
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
