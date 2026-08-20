package com.example.ducocontrol.server;

import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Small, shared helper methods for our com.sun.net.httpserver.HttpHandler
 * implementations. HttpExchange is the class representing a single incoming
 * request+response: you read the request via exchange.getRequestBody(), and
 * write the response via exchange.sendResponseHeaders(...) followed by
 * exchange.getResponseBody(). That's a fairly low-level API (no automatic
 * JSON (de)serialization like a framework), so this class exists to avoid
 * repeating those two steps in every handler.
 */
final class HttpExchangeSupport {

    private HttpExchangeSupport() {
    }

    static String readRequestBodyAsString(HttpExchange exchange) throws IOException {
        // readAllBytes() (since Java 9) reads the whole InputStream in one
        // go -- fine for the small JSON bodies we expect here.
        byte[] bytes = exchange.getRequestBody().readAllBytes();
        return new String(bytes, StandardCharsets.UTF_8);
    }

    static void writeJsonResponse(HttpExchange exchange, int statusCode, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        // try-with-resources closes the OutputStream automatically -- that
        // also closes out the whole exchange, so no separate exchange.close() needed.
        try (OutputStream responseBody = exchange.getResponseBody()) {
            responseBody.write(bytes);
        }
    }

    static void writeErrorResponse(HttpExchange exchange, int statusCode, String message) throws IOException {
        String json = "{\"error\":\"" + message.replace("\"", "'") + "\"}";
        writeJsonResponse(exchange, statusCode, json);
    }
}
