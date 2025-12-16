package pl.zapala.projekt.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import pl.zapala.projekt.protocol.SatelliteProtocol.*;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Service;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * TCP Server Service - Manages connections to satellite clients.
 * Handles incoming connections and command dispatching.
 */
@Service
public class TcpServerService {

    private static final int SERVER_PORT = 9000;
    private static final int SOCKET_TIMEOUT = 5000; // 5 seconds

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<Integer, ClientHandler> connectedClients = new ConcurrentHashMap<>();
    private final ExecutorService executorService = Executors.newCachedThreadPool();
    private final AtomicBoolean running = new AtomicBoolean(false);

    private ServerSocket serverSocket;
    private Thread acceptThread;

    @PostConstruct
    public void start() {
        if (running.get()) {
            return;
        }

        try {
            serverSocket = new ServerSocket(SERVER_PORT);
            running.set(true);

            acceptThread = new Thread(this::acceptConnections);
            acceptThread.setDaemon(true);
            acceptThread.start();

            System.out.println("[TCP Server] Started on port " + SERVER_PORT);
        } catch (IOException e) {
            System.err.println("[TCP Server] Failed to start: " + e.getMessage());
        }
    }

    @PreDestroy
    public void stop() {
        running.set(false);

        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException e) {
            System.err.println("[TCP Server] Error closing server socket: " + e.getMessage());
        }

        // Close all client connections
        connectedClients.values().forEach(ClientHandler::close);
        connectedClients.clear();

        executorService.shutdown();
        System.out.println("[TCP Server] Stopped");
    }

    /**
     * Accept incoming satellite connections
     */
    private void acceptConnections() {
        while (running.get()) {
            try {
                Socket clientSocket = serverSocket.accept();
                //// clientSocket.setSoTimeout(SOCKET_TIMEOUT);

                executorService.submit(() -> handleNewConnection(clientSocket));

            } catch (IOException e) {
                if (running.get()) {
                    System.err.println("[TCP Server] Accept error: " + e.getMessage());
                }
            }
        }
    }

    /**
     * Handle a new satellite connection
     */
    private void handleNewConnection(Socket socket) {
        try {
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);

            // Read registration message
            String registrationJson = in.readLine();
            if (registrationJson == null) {
                socket.close();
                return;
            }

            Response registration = objectMapper.readValue(registrationJson, Response.class);
            int satelliteId = registration.getId();

            // Remove old connection if exists
            ClientHandler oldHandler = connectedClients.remove(satelliteId);
            if (oldHandler != null) {
                oldHandler.close();
            }

            // Create new handler
            ClientHandler handler = new ClientHandler(satelliteId, socket, in, out);
            connectedClients.put(satelliteId, handler);

            System.out.println("[TCP Server] Satellite-" + satelliteId + " connected from " +
                    socket.getInetAddress().getHostAddress());

        } catch (Exception e) {
            System.err.println("[TCP Server] Error handling connection: " + e.getMessage());
            try {
                socket.close();
            } catch (IOException ignored) {}
        }
    }

    /**
     * Send a request to a specific satellite and wait for response
     */
    public CompletableFuture<Response> sendRequest(int satelliteId, Request request) {
        ClientHandler handler = connectedClients.get(satelliteId);
        if (handler == null) {
            return CompletableFuture.failedFuture(
                    new IllegalStateException("Satellite " + satelliteId + " not connected")
            );
        }

        return handler.sendRequest(request);
    }

    /**
     * Send request to all connected satellites
     */
    public Map<Integer, CompletableFuture<Response>> broadcastRequest(Request request) {
        Map<Integer, CompletableFuture<Response>> responses = new ConcurrentHashMap<>();

        connectedClients.forEach((id, handler) -> {
            responses.put(id, handler.sendRequest(request));
        });

        return responses;
    }

    /**
     * Get list of connected satellite IDs
     */
    public java.util.Set<Integer> getConnectedSatellites() {
        return new java.util.HashSet<>(connectedClients.keySet());
    }

    /**
     * Check if a satellite is connected
     */
    public boolean isConnected(int satelliteId) {
        return connectedClients.containsKey(satelliteId);
    }

    /**
     * Client handler for individual satellite connection
     */
    private class ClientHandler {
        private final int satelliteId;
        private final Socket socket;
        private final BufferedReader in;
        private final PrintWriter out;
        private final BlockingQueue<CompletableFuture<Response>> pendingResponses = new LinkedBlockingQueue<>();

        public ClientHandler(int satelliteId, Socket socket, BufferedReader in, PrintWriter out) {
            this.satelliteId = satelliteId;
            this.socket = socket;
            this.in = in;
            this.out = out;

            // Start response reader thread
            Thread readerThread = new Thread(this::readResponses);
            readerThread.setDaemon(true);
            readerThread.start();
        }

        public CompletableFuture<Response> sendRequest(Request request) {
            CompletableFuture<Response> future = new CompletableFuture<>();

            try {
                String requestJson = objectMapper.writeValueAsString(request);
                out.println(requestJson);

                pendingResponses.offer(future);

                // Set timeout
                executorService.submit(() -> {
                    try {
                        Thread.sleep(SOCKET_TIMEOUT);
                        future.completeExceptionally(new TimeoutException("Request timeout"));
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                });

            } catch (Exception e) {
                future.completeExceptionally(e);
            }

            return future;
        }

        private void readResponses() {
            try {
                String line;
                while ((line = in.readLine()) != null) {
                    try {
                        Response response = objectMapper.readValue(line, Response.class);

                        CompletableFuture<Response> future = pendingResponses.poll();
                        if (future != null && !future.isDone()) {
                            future.complete(response);
                        }

                    } catch (Exception e) {
                        System.err.println("[TCP Server] Error parsing response from Satellite-" +
                                satelliteId + ": " + e.getMessage());
                    }
                }
            } catch (SocketTimeoutException e) {
                // Normal timeout, continue
            } catch (IOException e) {
                System.out.println("[TCP Server] Satellite-" + satelliteId + " disconnected");
            } finally {
                close();
                connectedClients.remove(satelliteId);
            }
        }

        public void close() {
            try {
                socket.close();
            } catch (IOException ignored) {}

            // Complete all pending futures with error
            CompletableFuture<Response> future;
            while ((future = pendingResponses.poll()) != null) {
                future.completeExceptionally(new IOException("Connection closed"));
            }
        }
    }
}
