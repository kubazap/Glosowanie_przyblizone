package pl.zapala.projekt.satellite;

import com.fasterxml.jackson.databind.ObjectMapper;
import pl.zapala.projekt.protocol.SatelliteProtocol.*;

import java.io.*;
import java.net.Socket;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Satellite Client Application - Runs as a separate process.
 * Simulates a clock with configurable errors (offset, delay, crash).
 */
public class SatelliteApp {

    private final int satelliteId;
    private final String serverHost;
    private final int serverPort;
    private final ObjectMapper objectMapper;

    // Error simulation state
    private final AtomicLong timeOffset = new AtomicLong(0);
    private final AtomicBoolean crashed = new AtomicBoolean(false);

    // Base clock drift simulation (±10ms random drift)
    private final Random random = new Random();
    private long baseClockDrift;

    public SatelliteApp(int satelliteId, String serverHost, int serverPort) {
        this.satelliteId = satelliteId;
        this.serverHost = serverHost;
        this.serverPort = serverPort;
        this.objectMapper = new ObjectMapper();
        this.baseClockDrift = random.nextInt(21) - 10; // -10 to +10 ms
    }

    /**
     * Main entry point for satellite process.
     * Usage: java SatelliteApp <id> <host> <port>
     */
    public static void main(String[] args) {
        if (args.length < 3) {
            System.err.println("Usage: java SatelliteApp <id> <host> <port>");
            System.exit(1);
        }

        int id = Integer.parseInt(args[0]);
        String host = args[1];
        int port = Integer.parseInt(args[2]);

        SatelliteApp app = new SatelliteApp(id, host, port);
        app.run();
    }

    /**
     * Main run loop - connects to server and handles requests
     */
    public void run() {
        System.out.println("[Satellite-" + satelliteId + "] Starting...");

        while (true) {
            try (Socket socket = new Socket(serverHost, serverPort);
                 BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                 PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {

                System.out.println("[Satellite-" + satelliteId + "] Connected to server");

                // Send initial registration
                Response registration = new Response(
                        satelliteId,
                        getCurrentTime(),
                        ResponseStatus.OK,
                        "Satellite " + satelliteId + " connected"
                );
                out.println(objectMapper.writeValueAsString(registration));

                // Handle incoming requests
                String line;
                while ((line = in.readLine()) != null) {
                    handleRequest(line, out);
                }

            } catch (IOException e) {
                System.err.println("[Satellite-" + satelliteId + "] Connection error: " + e.getMessage());
            }

            // Reconnect after delay if not crashed
            if (!crashed.get()) {
                try {
                    Thread.sleep(5000);
                    System.out.println("[Satellite-" + satelliteId + "] Reconnecting...");
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            } else {
                System.out.println("[Satellite-" + satelliteId + "] Crashed - terminating");
                break;
            }
        }
    }

    /**
     * Handle a single request from the server
     */
    private void handleRequest(String requestJson, PrintWriter out) {
        try {
            Request request = objectMapper.readValue(requestJson, Request.class);

            // Check if crashed
            if (crashed.get() && request.getType() != RequestType.RESET_ERRORS) {
                // No response when crashed
                return;
            }

            Response response;

            switch (request.getType()) {
                case GET_TIME:
                    response = new Response(
                            satelliteId,
                            getCurrentTime(),
                            ResponseStatus.OK,
                            "Time reported"
                    );
                    break;

                case INJECT_CRASH:
                    crashed.set(true);
                    response = new Response(
                            satelliteId,
                            getCurrentTime(),
                            ResponseStatus.CRASHED,
                            "Satellite crashed"
                    );
                    System.out.println("[Satellite-" + satelliteId + "] CRASHED");
                    break;

                case INJECT_TIME_OFFSET:
                    long offset = request.getParameter() != null ? request.getParameter() : 0;
                    timeOffset.set(offset);
                    response = new Response(
                            satelliteId,
                            getCurrentTime(),
                            ResponseStatus.OK,
                            "Time offset set to " + offset + "ms"
                    );
                    System.out.println("[Satellite-" + satelliteId + "] Time offset injected: " + offset + "ms");
                    break;

                case RESET_ERRORS:
                    timeOffset.set(0);
                    crashed.set(false);
                    response = new Response(
                            satelliteId,
                            getCurrentTime(),
                            ResponseStatus.OK,
                            "All errors reset"
                    );
                    System.out.println("[Satellite-" + satelliteId + "] Errors reset");
                    break;

                case PING:
                    response = new Response(
                            satelliteId,
                            getCurrentTime(),
                            ResponseStatus.OK,
                            "Pong"
                    );
                    break;

                default:
                    response = new Response(
                            satelliteId,
                            getCurrentTime(),
                            ResponseStatus.ERROR,
                            "Unknown request type"
                    );
            }

            out.println(objectMapper.writeValueAsString(response));

        } catch (Exception e) {
            System.err.println("[Satellite-" + satelliteId + "] Error handling request: " + e.getMessage());
        }
    }

    /**
     * Get current time with all applied errors and drift
     */
    private long getCurrentTime() {
        long actualTime = System.currentTimeMillis();
        long offset = timeOffset.get();
        return actualTime + baseClockDrift + offset;
    }
}
