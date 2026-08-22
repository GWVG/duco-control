package com.example.ducocontrol.server;

import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/** Shared JSON request/response helpers for the HttpHandler implementations. */
final class HttpExchangeSupport {

    private HttpExchangeSupport() {
    }

    static String readRequestBodyAsString(HttpExchange exchange) throws IOException {
        byte[] bytes = exchange.getRequestBody().readAllBytes();
        return new String(bytes, StandardCharsets.UTF_8);
    }

    static void writeJsonResponse(HttpExchange exchange, int statusCode, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream responseBody = exchange.getResponseBody()) {
            responseBody.write(bytes);
        }
    }

    static void writeErrorResponse(HttpExchange exchange, int statusCode, String message) throws IOException {
        String json = "{\"error\":\"" + message.replace("\"", "'") + "\"}";
        writeJsonResponse(exchange, statusCode, json);
    }
}
