package com.example.ducocontrol.server;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/** Serves the frontend files on the same origin as /status and /set, so no CORS is needed. */
public class StaticFileHandler implements HttpHandler {

    private final Path rootDirectory;

    public StaticFileHandler(Path rootDirectory) {
        this.rootDirectory = rootDirectory.toAbsolutePath().normalize();
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            HttpExchangeSupport.writeErrorResponse(exchange, 405, "Only GET is allowed");
            return;
        }

        String requestPath = exchange.getRequestURI().getPath();
        if (requestPath.equals("/")) {
            requestPath = "/index.html";
        }

        // Reject paths that resolve outside rootDirectory (path traversal, e.g. "/../../etc/passwd").
        Path resolved = rootDirectory.resolve(requestPath.substring(1)).normalize();
        if (!resolved.startsWith(rootDirectory) || !Files.isRegularFile(resolved)) {
            HttpExchangeSupport.writeErrorResponse(exchange, 404, "Not found: " + requestPath);
            return;
        }

        byte[] content = Files.readAllBytes(resolved);
        exchange.getResponseHeaders().set("Content-Type", contentTypeFor(resolved));
        exchange.sendResponseHeaders(200, content.length);
        try (OutputStream responseBody = exchange.getResponseBody()) {
            responseBody.write(content);
        }
    }

    private static String contentTypeFor(Path path) {
        String name = path.getFileName().toString();
        if (name.endsWith(".html")) {
            return "text/html; charset=utf-8";
        }
        if (name.endsWith(".css")) {
            return "text/css; charset=utf-8";
        }
        if (name.endsWith(".js")) {
            return "application/javascript; charset=utf-8";
        }
        if (name.endsWith(".webmanifest")) {
            return "application/manifest+json; charset=utf-8";
        }
        if (name.endsWith(".png")) {
            return "image/png";
        }
        if (name.endsWith(".svg")) {
            return "image/svg+xml; charset=utf-8";
        }
        return "application/octet-stream";
    }
}
