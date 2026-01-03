package pl.zapala.projekt.satellite;

import com.fasterxml.jackson.databind.ObjectMapper;
import pl.zapala.projekt.protocol.SatelliteProtocol.*;

import java.io.*;
import java.net.Socket;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Satellite Client Application with error handling and Watchdog Timer.
 * Simulates a clock with configurable errors (offset, delay, crash).
 */
public class SatelliteApp {

    private final int satelliteId;
    private final String serverHost;
    private final int serverPort;
    private final ObjectMapper objectMapper;

    // Error simulation state
    private final AtomicLong timeOffset = new AtomicLong(0);
    private final AtomicLong networkDelay = new AtomicLong(0);
    private final AtomicBoolean crashed = new AtomicBoolean(false);

    // Watchdog monitoring
    private final AtomicLong lastFeedTime = new AtomicLong(System.currentTimeMillis());
    private static final long WATCHDOG_TIMEOUT = 10000; // 10 seconds

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
        System.out.println("[Satellite-" + satelliteId + "] Starting with Watchdog protection...");

        startWatchdog();

        while (true) {
            try (Socket socket = new Socket(serverHost, serverPort);
                 BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                 PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {

                System.out.println("[Satellite-" + satelliteId + "] Connected to server");

                Response registration = new Response(
                        satelliteId,
                        getCurrentTime(),
                        ResponseStatus.OK,
                        "Satellite " + satelliteId + " connected"
                );
                out.println(objectMapper.writeValueAsString(registration));

                // Main loop - handle incoming requests
                String line;
                while ((line = in.readLine()) != null) {
                    feedWatchdog();
                    handleRequest(line, out);
                }

            } catch (IOException e) {
                System.err.println("[Satellite-" + satelliteId + "] Connection error: " + e.getMessage());
            }

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
     * Watchdog Timer - monitors main thread activity
     */
    private void startWatchdog() {
        Thread watchdog = new Thread(() -> {
            while (!crashed.get()) {
                try {
                    Thread.sleep(2000);

                    long timeSinceLastFeed = System.currentTimeMillis() - lastFeedTime.get();

                    if (timeSinceLastFeed > WATCHDOG_TIMEOUT) {
                        System.err.println("[Satellite-" + satelliteId + "] WATCHDOG ALERT: Main thread unresponsive for "
                                + timeSinceLastFeed + "ms!");
                    }

                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }, "Watchdog-" + satelliteId);

        watchdog.setDaemon(true);
        watchdog.start();
    }

    /**
     * Feed watchdog - confirms main thread activity
     */
    private void feedWatchdog() {
        lastFeedTime.set(System.currentTimeMillis());
    }

    /**
     * Handle a single request from the server with error simulation
     */
    private void handleRequest(String requestJson, PrintWriter out) {
        try {
            long delay = networkDelay.get();
            if (delay > 0) {
                Thread.sleep(delay);
            }

            Request request = objectMapper.readValue(requestJson, Request.class);

            if (crashed.get() && request.getType() != RequestType.RESET_ERRORS) {
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
                    System.out.println("[Satellite-" + satelliteId + "] CRASHED - simulating hardware failure");
                    out.println(objectMapper.writeValueAsString(response));

                    Thread.sleep(500);
                    System.exit(0);
                    return;

                case INJECT_TIME_OFFSET:
                    long offset = request.getParameter() != null ? request.getParameter() : 0;
                    timeOffset.set(offset);
                    response = new Response(
                            satelliteId,
                            getCurrentTime(),
                            ResponseStatus.OK,
                            "Time offset set to " + offset + "ms"
                    );
                    System.out.println("[Satellite-" + satelliteId + "] Time offset: " + offset + "ms");
                    break;

                case INJECT_NETWORK_DELAY:
                    long delayValue = request.getParameter() != null ? request.getParameter() : 0;
                    networkDelay.set(delayValue);
                    response = new Response(
                            satelliteId,
                            getCurrentTime(),
                            ResponseStatus.OK,
                            "Network delay set to " + delayValue + "ms"
                    );
                    System.out.println("[Satellite-" + satelliteId + "] Network delay: " + delayValue + "ms");
                    break;

                case RESET_ERRORS:
                    timeOffset.set(0);
                    networkDelay.set(0);
                    crashed.set(false);
                    response = new Response(
                            satelliteId,
                            getCurrentTime(),
                            ResponseStatus.OK,
                            "All errors reset"
                    );
                    System.out.println("[Satellite-" + satelliteId + "] All errors reset");
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
            System.err.println("[Satellite-" + satelliteId + "] Error: " + e.getMessage());
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