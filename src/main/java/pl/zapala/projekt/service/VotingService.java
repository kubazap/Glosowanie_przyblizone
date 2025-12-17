package pl.zapala.projekt.service;


import pl.zapala.projekt.protocol.SatelliteProtocol.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Voting Service - Implements the weighted average time calculation algorithm.
 * Periodically polls satellites and calculates the most probable system time.
 */
@Service
public class VotingService {

    private final TcpServerService tcpServer;
    private final Map<Integer, SatelliteData> satelliteDataMap = new ConcurrentHashMap<>();
    private final Map<Integer, Double> satelliteWeights = new ConcurrentHashMap<>();
    private final Map<Integer, Integer> errorCounters = new ConcurrentHashMap<>();

    private static final int MAX_ERRORS_TOLERANCE = 3;
    private volatile long calculatedSystemTime = 0;
    private volatile long lastCalculationTime = 0;
    private volatile int activeResponseCount = 0;

    public VotingService(TcpServerService tcpServer) {
        this.tcpServer = tcpServer;
        initializeWeights();
    }

    /**
     * Initialize default weights for all satellites (equal weight = 1.0)
     */
    private void initializeWeights() {
        for (int i = 1; i <= 8; i++) {
            satelliteWeights.put(i, 1.0);
            errorCounters.put(i, 0);
        }
    }

    /**
     * Scheduled task: Poll all satellites for time and calculate weighted average
     * Runs every 3 seconds
     */
    @Scheduled(fixedRate = 3000, initialDelay = 1000)
    public void pollSatellitesAndCalculate() {
        try {
            Request timeRequest = new Request(RequestType.GET_TIME, null);
            Map<Integer, CompletableFuture<Response>> futures = tcpServer.broadcastRequest(timeRequest);

            if (futures.isEmpty()) {
                return;
            }

            List<Response> responses = new ArrayList<>();

            futures.forEach((id, future) -> {
                try {
                    int waited = 0;
                    while (!future.isDone() && waited < 1000) {
                        Thread.sleep(10);
                        waited += 10;
                    }

                    Response response = future.getNow(null);

                    if (response == null) {
                        throw new Exception("Timeout");
                    }

                    responses.add(response);

                    SatelliteData data = satelliteDataMap.computeIfAbsent(id, k -> new SatelliteData(id));
                    data.setLastResponse(response);
                    data.setLastSeen(System.currentTimeMillis());

                    errorCounters.put(id, 0);
                    data.setConnected(true);

                } catch (Exception e) {
                    handleCommunicationError(id);
                }
            });

            activeResponseCount = responses.size();

            if (responses.isEmpty()) {
                System.out.println("[Voting] No satellite responses received");
                return;
            }

            calculatedSystemTime = calculateWeightedAverage(responses);
            lastCalculationTime = System.currentTimeMillis();

            long deviation = calculatedSystemTime - lastCalculationTime;

            System.out.printf("[Voting] System Time: %d | Deviation: %+d ms | Active: %d/%d%n",
                    calculatedSystemTime, deviation, activeResponseCount, satelliteWeights.size());

        } catch (Exception e) {
            System.err.println("[Voting] Error: " + e.getMessage());
        }
    }

    private void handleCommunicationError(int id) {
        SatelliteData data = satelliteDataMap.get(id);
        if (data == null) return;

        int currentErrors = errorCounters.getOrDefault(id, 0) + 1;
        errorCounters.put(id, currentErrors);

        if (currentErrors >= MAX_ERRORS_TOLERANCE) {
            if (data.isConnected()) {
                System.out.println("[Voting] Satellite " + id + " marked as DISCONNECTED after " + currentErrors + " failures.");
            }
            data.setConnected(false);
        } else {
            System.out.println("[Voting] Satellite " + id + " missed a beat (" + currentErrors + "/" + MAX_ERRORS_TOLERANCE + ")");
        }
    }

    /**
     * Calculate weighted average time from satellite responses
     */
    private long calculateWeightedAverage(List<Response> responses) {
        if (responses.isEmpty()) {
            return System.currentTimeMillis();
        }

        // Filter only OK responses
        List<Response> validResponses = responses.stream()
                .filter(r -> r.getStatus() == ResponseStatus.OK)
                .collect(Collectors.toList());

        if (validResponses.isEmpty()) {
            return System.currentTimeMillis();
        }

        // Calculate weighted average
        double totalWeight = 0.0;
        double weightedSum = 0.0;

        for (Response response : validResponses) {
            double weight = satelliteWeights.getOrDefault(response.getId(), 1.0);
            weightedSum += response.getTimestamp() * weight;
            totalWeight += weight;
        }

        if (totalWeight == 0) {
            // Fallback to simple average
            return (long) validResponses.stream()
                    .mapToLong(Response::getTimestamp)
                    .average()
                    .orElse(System.currentTimeMillis());
        }

        return Math.round(weightedSum / totalWeight);
    }

    /**
     * Update weight for a specific satellite
     */
    public void updateSatelliteWeight(int satelliteId, double weight) {
        if (weight < 0 || weight > 10) {
            throw new IllegalArgumentException("Weight must be between 0 and 10");
        }
        satelliteWeights.put(satelliteId, weight);
        System.out.println("[Voting] Updated weight for Satellite-" + satelliteId + " to " + weight);
    }

    /**
     * Get current weight for a satellite
     */
    public double getSatelliteWeight(int satelliteId) {
        return satelliteWeights.getOrDefault(satelliteId, 1.0);
    }

    /**
     * Get state of all satellites for UI display
     */
    public List<SatelliteState> getAllSatelliteStates() {
        List<SatelliteState> states = new ArrayList<>();

        for (int id = 1; id <= 8; id++) {
            SatelliteData data = satelliteDataMap.get(id);

            boolean isAlive = (data != null && data.isConnected());

            if (data != null && data.getLastResponse() != null) {
                states.add(new SatelliteState(
                        id,
                        data.getLastSeen(),
                        data.getLastResponse().getTimestamp(),
                        data.getLastResponse().getStatus(),
                        satelliteWeights.getOrDefault(id, 1.0),
                        isAlive
                ));
            } else {
                states.add(new SatelliteState(
                        id,
                        0,
                        0,
                        ResponseStatus.ERROR,
                        satelliteWeights.getOrDefault(id, 1.0),
                        isAlive
                ));
            }
        }

        return states;
    }

    /**
     * Get calculated system time
     */
    public long getCalculatedSystemTime() {
        return calculatedSystemTime;
    }

    /**
     * Get deviation from actual system time
     */
    public long getDeviation() {
        return calculatedSystemTime - System.currentTimeMillis();
    }

    /**
     * Get number of active satellites in last poll
     */
    public int getActiveResponseCount() {
        return activeResponseCount;
    }

    /**
     * Inject error into a specific satellite
     */
    public CompletableFuture<Response> injectError(int satelliteId, RequestType errorType, Long parameter) {
        Request request = new Request(errorType, parameter);
        return tcpServer.sendRequest(satelliteId, request);
    }

    /**
     * Reset all errors for a satellite
     */
    public CompletableFuture<Response> resetSatelliteErrors(int satelliteId) {
        errorCounters.put(satelliteId, 0);
        Request request = new Request(RequestType.RESET_ERRORS, null);
        return tcpServer.sendRequest(satelliteId, request);
    }

    /**
     * Internal class to track satellite data
     */
    private static class SatelliteData {
        private final int id;
        private Response lastResponse;
        private long lastSeen;
        private boolean connected;

        public SatelliteData(int id) {
            this.id = id;
        }

        public Response getLastResponse() {
            return lastResponse;
        }

        public void setLastResponse(Response lastResponse) {
            this.lastResponse = lastResponse;
        }

        public long getLastSeen() {
            return lastSeen;
        }

        public void setLastSeen(long lastSeen) {
            this.lastSeen = lastSeen;
        }

        public boolean isConnected() {
            return connected;
        }

        public void setConnected(boolean connected) {
            this.connected = connected;
        }
    }
}