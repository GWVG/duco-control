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

/** Application entry point: wires up the components and starts the HTTP server. */
public class DucoControlApp {

    // Node ID of the DucoBox unit itself (Type "BOX" in GET /v2/info/nodes).
    // Check your own system's node layout if this differs.
    private static final int MAIN_NODE_ID = 1;
    private static final int DEFAULT_PORT = 8765;

    public static void main(String[] args) throws IOException {
        URI ducoBaseUri = URI.create(envOrDefault("DUCO_BASE_URL", "http://duco.home.arpa"));
        int port = Integer.parseInt(envOrDefault("PORT", String.valueOf(DEFAULT_PORT)));

        DucoHttpGateway ducoHttpGateway = new JdkDucoHttpGateway(ducoBaseUri);
        DucoClient ducoClient = new DucoClient(ducoHttpGateway, MAIN_NODE_ID);
        AutoRevertScheduler revertScheduler =
                new AutoRevertScheduler(Executors.newSingleThreadScheduledExecutor());

        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/status", new StatusHandler(ducoClient));
        server.createContext("/set", new SetVentilationHandler(ducoClient, revertScheduler));
        server.createContext("/", new StaticFileHandler(Path.of("frontend")));
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
