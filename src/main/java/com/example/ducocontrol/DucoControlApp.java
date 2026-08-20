package com.example.ducocontrol;

import com.example.ducocontrol.client.AutoRevertScheduler;
import com.example.ducocontrol.client.DucoClient;
import com.example.ducocontrol.client.DucoHttpGateway;
import com.example.ducocontrol.client.JdkDucoHttpGateway;
import com.example.ducocontrol.server.SetVentilationHandler;
import com.example.ducocontrol.server.StaticFileHandler;
import com.example.ducocontrol.server.StatusHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.file.Path;
import java.util.concurrent.Executors;

/**
 * Application entry point: wires up all the components (dependency wiring
 * by hand -- no Spring container doing it for you) and starts the built-in
 * com.sun.net.httpserver.HttpServer.
 *
 * Why no Spring Boot? A deliberate choice from the assignment: the goal is
 * to learn the fundamentals of an HTTP server (registering routes, writing
 * requests/responses yourself), not to learn how to configure a framework
 * that abstracts all of that away.
 */
public class DucoControlApp {

    // Node 1 is the DucoBox itself (Type "BOX" in GET /v2/info/nodes) --
    // confirmed as the node ID to build against by running GET
    // /v2/info/nodes against the real system first. Project history: node 5
    // (from older documentation) did not exist on this system.
    private static final int MAIN_NODE_ID = 1;
    private static final int DEFAULT_PORT = 8765;

    public static void main(String[] args) throws IOException {
        // Configurable via environment variables instead of hardcoded, so
        // you can point DUCO_BASE_URL at an IP address if "duco.home.arpa"
        // doesn't resolve on the machine this runs on.
        URI ducoBaseUri = URI.create(envOrDefault("DUCO_BASE_URL", "http://duco.home.arpa"));
        int port = Integer.parseInt(envOrDefault("PORT", String.valueOf(DEFAULT_PORT)));

        DucoHttpGateway ducoHttpGateway = new JdkDucoHttpGateway(ducoBaseUri);
        DucoClient ducoClient = new DucoClient(ducoHttpGateway, MAIN_NODE_ID);
        AutoRevertScheduler revertScheduler =
                new AutoRevertScheduler(Executors.newSingleThreadScheduledExecutor());

        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/status", new StatusHandler(ducoClient));
        server.createContext("/set", new SetVentilationHandler(ducoClient, revertScheduler));
        // "/" catches everything that doesn't match a more specific context
        // -- com.sun.net.httpserver.HttpServer always picks the longest
        // matching context prefix, so /status and /set keep priority over
        // their own paths.
        server.createContext("/", new StaticFileHandler(Path.of("frontend")));

        // By default HttpServer processes requests one at a time on a
        // single thread (executor = null). With a thread pool, multiple
        // requests can be handled concurrently -- important once the
        // frontend polls /status every few seconds while someone else calls
        // /set at the same time.
        server.setExecutor(Executors.newFixedThreadPool(4));
        server.start();

        System.out.println("duco-control listening on http://localhost:" + port
                + " (DucoBox: " + ducoBaseUri + ", node " + MAIN_NODE_ID + ")");
    }

    private static String envOrDefault(String name, String defaultValue) {
        String value = System.getenv(name);
        return value != null ? value : defaultValue;
    }
}
