package com.example.ducocontrol.client;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/** DucoHttpGateway implementation built on java.net.http.HttpClient. */
public class JdkDucoHttpGateway implements DucoHttpGateway {

    private final URI baseUri;
    private final HttpClient httpClient;

    /**
     * @param baseUri e.g. "http://duco.home.arpa" or "http://192.168.x.x" if
     *                DNS doesn't resolve on your network
     */
    public JdkDucoHttpGateway(URI baseUri) {
        this.baseUri = baseUri;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                // The DucoBox's embedded web server returns HTTP 400 if it
                // receives an HTTP/2 upgrade attempt, which HttpClient sends
                // by default.
                .version(HttpClient.Version.HTTP_1_1)
                .build();
    }

    @Override
    public String get(String path) throws IOException {
        HttpRequest request = HttpRequest.newBuilder(baseUri.resolve(path))
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();
        return send(request);
    }

    @Override
    public String post(String path, String jsonBody) throws IOException {
        HttpRequest request = HttpRequest.newBuilder(baseUri.resolve(path))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();
        return send(request);
    }

    private String send(HttpRequest request) throws IOException {
        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while waiting for DucoBox response", e);
        }
        if (response.statusCode() / 100 != 2) {
            throw new IOException("DucoBox returned unexpected HTTP status " + response.statusCode()
                    + " for " + request.uri());
        }
        return response.body();
    }
}
