package com.example.ducocontrol.client;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * The real implementation of DucoHttpGateway, built on java.net.http (built
 * into the JDK since Java 11 -- no extra dependency like OkHttp/Apache
 * HttpClient needed).
 *
 * Three core classes from that package:
 *  - HttpClient: the reusable "connection factory". Deliberately one
 *    instance per gateway (created in the constructor, not per request) --
 *    it manages its own connection pool and is thread-safe, so recreating
 *    it per call would only add overhead.
 *  - HttpRequest: describes a single request (method, URI, headers, body).
 *  - HttpResponse: the response; via BodyHandlers.ofString() we ask for the
 *    body to arrive as a String (variants also exist for bytes, files,
 *    streaming, ...).
 */
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
                // Without this, HttpClient defaults to sending an HTTP/2
                // upgrade attempt (Connection: Upgrade, HTTP2-Settings) with
                // every plaintext http:// request. Discovered by testing
                // this live against the DucoBox: its minimal embedded web
                // server doesn't understand those upgrade headers and
                // responds with a bare HTTP 400 "unable to understand
                // request", while the exact same URL with curl (which
                // doesn't attempt an HTTP/2 upgrade for plain http://) just
                // returned 200. Explicitly forcing HTTP/1.1 fixes that.
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
            // send() throws InterruptedException if the thread gets
            // interrupted while waiting for the response. We first restore
            // the interrupt status (Java convention: if you catch an
            // InterruptedException without re-signaling it, you "lose" the
            // signal that something else in the code may be waiting on) and
            // then translate it to IOException, so DucoHttpGateway callers
            // only have to handle one exception type.
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
